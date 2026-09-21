package com.secondpasslibrary.reader.app

import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.persistence.PendingReaderContinuationOutcome
import com.secondpasslibrary.reader.reader.persistence.ReaderContinuationOutcomeNoticeStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private typealias PendingOutcomeFlow = MutableStateFlow<List<PendingReaderContinuationOutcome>>

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSyncOutcomeNoticeControllerTest {
    @Test
    fun `copy handles forwarded edits dropped deletes and singular plural naturally`() {
        assertEquals(
            "A previous Reading Session closed while you were offline. " +
                "1 Marginalia edit was carried into the current Reading Session.",
            message(edits = 1)
        )
        assertEquals(
            "A previous Reading Session closed while you were offline. " +
                "1 deletion couldn’t be applied because the previous Reading Session had closed.",
            message(deletes = 1)
        )
        assertEquals(
            "A previous Reading Session closed while you were offline. " +
                "2 Marginalia edits were carried into the current Reading Session. " +
                "3 deletions couldn’t be applied because the previous Reading Sessions had already closed.",
            message(edits = 2, deletes = 3)
        )
    }

    @Test
    fun `pending outcomes aggregate once and acknowledge exact source rows`() = runTest {
        val store = FakeOutcomeStore()
        val controller = ReaderSyncOutcomeNoticeController(store, this)
        val account = account("profile-a")
        store.emit(
            account,
            outcome("source-1", edits = 1),
            outcome("source-2", edits = 2, deletes = 2)
        )

        controller.update(account)
        advanceUntilIdle()

        val notice = requireNotNull(controller.notice.value)
        assertEquals(2, notice.affectedSessionCount)
        assertEquals(3, notice.forwardedEditCount)
        assertEquals(2, notice.droppedDeleteCount)
        assertEquals(
            "Previous Reading Sessions closed while you were offline. " +
                "3 Marginalia edits were carried into new Reading Sessions. " +
                "2 deletions couldn’t be applied because the previous Reading Sessions had already closed.",
            ReaderSyncOutcomeNoticePresenter.message(notice)
        )

        controller.acknowledge(notice.id)
        advanceUntilIdle()

        assertNull(controller.notice.value)
        assertEquals(listOf("source-1", "source-2"), store.consumed.single().second)
        controller.clear()
    }

    @Test
    fun `zero outcomes do not show and consumed outcome does not return`() = runTest {
        val store = FakeOutcomeStore()
        val controller = ReaderSyncOutcomeNoticeController(store, this)
        val account = account("profile-a")
        store.emit(account, outcome("zero"))

        controller.update(account)
        advanceUntilIdle()
        assertNull(controller.notice.value)

        store.emit(account, outcome("visible", deletes = 1))
        advanceUntilIdle()
        val notice = requireNotNull(controller.notice.value)
        controller.acknowledge(notice.id)
        advanceUntilIdle()
        controller.clear()
        controller.update(account)
        advanceUntilIdle()

        assertNull(controller.notice.value)
        controller.clear()
    }

    @Test
    fun `background outcome waits for shell and account switch cannot consume former account`() =
        runTest {
            val store = FakeOutcomeStore()
            val controller = ReaderSyncOutcomeNoticeController(store, this)
            val accountA = account("profile-a")
            val accountB = account("profile-b")
            store.emit(accountA, outcome("source-a", edits = 1))

            assertNull(controller.notice.value)
            controller.update(accountA)
            advanceUntilIdle()
            val staleNotice = requireNotNull(controller.notice.value)

            store.emit(accountB, outcome("source-b", deletes = 1))
            controller.update(accountB)
            advanceUntilIdle()
            val currentNotice = requireNotNull(controller.notice.value)
            controller.acknowledge(staleNotice.id)
            controller.acknowledge(currentNotice.id)
            advanceUntilIdle()

            assertEquals(listOf(accountB), store.consumed.map { it.first })
            assertEquals(listOf("source-b"), store.consumed.single().second)
            controller.clear()
        }

    private fun message(edits: Int = 0, deletes: Int = 0): String =
        ReaderSyncOutcomeNoticePresenter.message(
            ReaderSyncOutcomeNotice(1, 1, edits, deletes)
        )

    private fun account(profile: String) =
        LocalReaderAccountKey.from("a6722b5a-7982-4778-8c74-39be4241a654", profile)

    private fun outcome(source: String, edits: Int = 0, deletes: Int = 0) =
        PendingReaderContinuationOutcome(source, edits, deletes)

    private class FakeOutcomeStore : ReaderContinuationOutcomeNoticeStore {
        private val outcomes = mutableMapOf<LocalReaderAccountKey, PendingOutcomeFlow>()
        val consumed = mutableListOf<Pair<LocalReaderAccountKey, List<String>>>()

        fun emit(account: LocalReaderAccountKey, vararg values: PendingReaderContinuationOutcome) {
            flow(account).value = values.toList()
        }

        override fun pendingOutcomes(
            account: LocalReaderAccountKey
        ): Flow<List<PendingReaderContinuationOutcome>> = flow(account)

        override suspend fun consume(
            account: LocalReaderAccountKey,
            sourceLocalSessionIds: List<String>
        ) {
            consumed += account to sourceLocalSessionIds
            flow(account).value = flow(account).value.filterNot {
                it.sourceLocalSessionId in sourceLocalSessionIds
            }
        }

        private fun flow(account: LocalReaderAccountKey) =
            outcomes.getOrPut(account) { MutableStateFlow(emptyList()) }
    }
}
