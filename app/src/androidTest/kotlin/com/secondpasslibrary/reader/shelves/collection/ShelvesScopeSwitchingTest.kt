package com.secondpasslibrary.reader.shelves.collection

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfOwner
import com.secondpasslibrary.client.ShelfVisibility
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.shelves.ShelvesCollection
import com.secondpasslibrary.reader.shelves.ShelvesDestination
import com.secondpasslibrary.reader.shelves.ShelvesIntent
import com.secondpasslibrary.reader.shelves.ShelvesScreen
import com.secondpasslibrary.reader.shelves.ShelvesState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShelvesScopeSwitchingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectorExposesExactlyThreeScopesWithIndependentEmptyCopy() {
        var selected by mutableStateOf(ShelvesCollection.PERSONAL)
        compose.setContent {
            SecondPassTheme {
                ShelvesRoot(
                    state = emptyState(selected),
                    onCollectionSelected = { selected = it },
                    onOrderingSelected = {},
                    onSearchQueryChanged = {},
                    onSearchSubmitted = {},
                    onSearchCleared = {},
                    onLoadNextPersonal = {},
                    onLoadNextShared = {},
                    onLoadNextGroup = {},
                    onRetryPersonal = {},
                    onRetryShared = {},
                    onRetryGroup = {},
                    onShelfSelected = {},
                    onCreateShelf = {},
                    createShelfAvailable = true,
                    scrollStates = rememberShelfCollectionScrollStates()
                )
            }
        }

        compose.onAllNodesWithText("Personal").assertCountEquals(1)
        compose.onAllNodesWithText("Shared by Others").assertCountEquals(1)
        compose.onAllNodesWithText("Group Shelves").assertCountEquals(1)
        compose.onNodeWithText("Personal").assertIsSelected()
        compose.onNodeWithText("No personal shelves yet.").assertIsDisplayed()

        compose.onNodeWithText("Shared by Others").performClick().assertIsSelected()
        compose.onNodeWithText("No shelves shared with you.").assertIsDisplayed()
        compose.onNodeWithText("Group Shelves").performClick().assertIsSelected()
        compose.onNodeWithText("No group shelves available.").assertIsDisplayed()
    }

    @Test
    fun shelfSearchUsesScopePlaceholderAndImeSubmission() {
        var submitted = 0
        var query by mutableStateOf("")
        compose.setContent {
            SecondPassTheme {
                ShelvesRoot(
                    state = emptyState(ShelvesCollection.PERSONAL).copy(
                        personal = loadedCollection(emptyList()).copy(searchInput = query)
                    ),
                    onCollectionSelected = {},
                    onOrderingSelected = {},
                    onSearchQueryChanged = { query = it },
                    onSearchSubmitted = { submitted += 1 },
                    onSearchCleared = { query = "" },
                    onLoadNextPersonal = {},
                    onLoadNextShared = {},
                    onLoadNextGroup = {},
                    onRetryPersonal = {},
                    onRetryShared = {},
                    onRetryGroup = {},
                    onShelfSelected = {},
                    onCreateShelf = {},
                    createShelfAvailable = true,
                    scrollStates = rememberShelfCollectionScrollStates()
                )
            }
        }

        compose.onNodeWithText("Search shelves").performTextInput("favorites")
        compose.onNodeWithText("favorites").performImeAction()
        compose.runOnIdle { assertEquals(1, submitted) }
    }

    @Test
    fun scopeAndDetailSwitchesRestorePersonalScrollWithoutHiddenScopeContent() {
        var state by mutableStateOf(populatedState(ShelvesCollection.PERSONAL))
        compose.setContent {
            SecondPassTheme {
                ShelvesScreen(
                    state = state,
                    onIntent = { intent ->
                        if (intent is ShelvesIntent.ShowCollection) {
                            state = populatedState(intent.collection)
                        }
                    },
                    serverMutationsAvailable = false,
                    onOpenDrawer = {},
                    onBookSelected = {}
                )
            }
        }

        compose.onNodeWithTag(ShelvesCollection.PERSONAL.listTestTag).performScrollToIndex(18)
        compose.onNodeWithText("Personal 18").assertIsDisplayed()
        repeat(3) {
            compose.onNodeWithText("Shared by Others").performClick()
            compose.onNodeWithText("Shared 0").assertIsDisplayed()
            compose.onNodeWithText("Personal 18").assertDoesNotExist()
            compose.onNodeWithText("Group Shelves").performClick()
            compose.onNodeWithText("Group 0").assertIsDisplayed()
            compose.onNodeWithText("Personal").performClick()
            compose.onNodeWithText("Personal 18").assertIsDisplayed()
        }

        compose.runOnIdle {
            state = state.copy(
                destination = ShelvesDestination.Detail(
                    shelfId = "personal-18",
                    origin = ShelvesCollection.PERSONAL
                )
            )
        }
        compose.runOnIdle {
            state = state.copy(
                destination = ShelvesDestination.Collection(ShelvesCollection.PERSONAL)
            )
        }
        compose.onNodeWithText("Personal 18").assertIsDisplayed()
    }

    private fun emptyState(selected: ShelvesCollection) = ShelvesState(
        destination = ShelvesDestination.Collection(selected),
        personal = loadedCollection(emptyList()),
        shared = loadedCollection(emptyList()),
        group = loadedCollection(emptyList())
    )

    private fun populatedState(selected: ShelvesCollection) = ShelvesState(
        destination = ShelvesDestination.Collection(selected),
        personal = loadedCollection(shelves("Personal", "personal")),
        shared = loadedCollection(shelves("Shared", "shared")),
        group = loadedCollection(shelves("Group", "group"))
    )

    private fun loadedCollection(shelves: List<Shelf>) = ShelfCollectionState(
        shelves = shelves,
        totalCount = shelves.size,
        hasLoaded = true,
        currentPage = 1
    )

    private fun shelves(label: String, idPrefix: String) = List(30) { index ->
        Shelf(
            id = "$idPrefix-$index",
            name = "$label $index",
            description = null,
            owner = ShelfOwner.User("profile-$index", "reader-$index"),
            visibility = ShelfVisibility.LISTED,
            itemCount = index,
            canEdit = idPrefix == "personal",
            createdBy = null,
            createdAt = "2026-09-21T00:00:00Z",
            updatedAt = "2026-09-21T00:00:00Z",
            matchedItemId = null,
            previewBooks = emptyList()
        )
    }
}
