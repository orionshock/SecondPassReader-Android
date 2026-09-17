package com.secondpasslibrary.reader.coroutines

import kotlinx.coroutines.CancellationException

@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> runSuspendCatching(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}
