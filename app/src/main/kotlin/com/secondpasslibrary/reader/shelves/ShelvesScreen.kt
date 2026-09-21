package com.secondpasslibrary.reader.shelves

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.secondpasslibrary.client.ShelfItemMove
import com.secondpasslibrary.reader.design.components.AppBarPresentation
import com.secondpasslibrary.reader.design.components.ContextualAppBar
import com.secondpasslibrary.reader.shelves.collection.ShelfCollectionScrollStates
import com.secondpasslibrary.reader.shelves.collection.ShelvesRoot
import com.secondpasslibrary.reader.shelves.collection.rememberShelfCollectionScrollStates
import com.secondpasslibrary.reader.shelves.detail.ShelfDetailContent
import com.secondpasslibrary.reader.shelves.editor.RemoveShelfItemDialog
import com.secondpasslibrary.reader.shelves.editor.ShelfContentsEditorContent
import com.secondpasslibrary.reader.shelves.editor.ShelfContentsEditorState
import com.secondpasslibrary.reader.shelves.editor.ShelfPositionDialog
import com.secondpasslibrary.reader.shelves.management.CreatePersonalShelfDialog
import com.secondpasslibrary.reader.shelves.management.DeletePersonalShelfDialog
import com.secondpasslibrary.reader.shelves.management.EditPersonalShelfDialog
import com.secondpasslibrary.reader.shelves.management.canManageShelf

@Composable
internal fun ShelvesScreen(
    state: ShelvesState,
    onIntent: (ShelvesIntent) -> Unit,
    serverMutationsAvailable: Boolean,
    onOpenDrawer: () -> Unit,
    onBookSelected: (ShelfBookNavigationRequest) -> Unit,
    onExitInitialDetail: (() -> Unit)? = null
) {
    val collectionScrollStates = rememberShelfCollectionScrollStates()
    val detail = state.destination as? ShelvesDestination.Detail
    val editor = state.destination as? ShelvesDestination.ContentsEditor
    val back = when {
        editor != null -> ({ onIntent(ShelvesIntent.BackFromContentsEditor) })
        detail != null -> onExitInitialDetail ?: { onIntent(ShelvesIntent.BackFromDetail) }
        else -> onOpenDrawer
    }
    BackHandler(enabled = detail != null || editor != null, onBack = back)

    ShelvesScaffold(state.appBarPresentation(), back) { modifier ->
        ShelvesDestinationContent(
            state,
            onIntent,
            serverMutationsAvailable,
            onBookSelected,
            collectionScrollStates,
            modifier
        )
    }
    if (serverMutationsAvailable) ShelvesMutationDialogs(state, onIntent)
}

@Composable
private fun ShelvesDestinationContent(
    state: ShelvesState,
    onIntent: (ShelvesIntent) -> Unit,
    serverMutationsAvailable: Boolean,
    onBookSelected: (ShelfBookNavigationRequest) -> Unit,
    collectionScrollStates: ShelfCollectionScrollStates,
    modifier: Modifier
) {
    when (val destination = state.destination) {
        is ShelvesDestination.ContentsEditor ->
            if (serverMutationsAvailable) ShelfEditorDestination(state.editor, onIntent, modifier)

        is ShelvesDestination.Detail ->
            ShelfDetailDestination(
                state,
                destination,
                onIntent,
                serverMutationsAvailable,
                onBookSelected,
                modifier
            )

        is ShelvesDestination.Collection ->
            ShelfCollectionDestination(
                state,
                onIntent,
                serverMutationsAvailable,
                collectionScrollStates,
                modifier
            )
    }
}

@Composable
private fun ShelfEditorDestination(
    state: ShelfContentsEditorState,
    onIntent: (ShelvesIntent) -> Unit,
    modifier: Modifier
) = ShelfContentsEditorContent(
    state = state,
    onLoadNextPage = { onIntent(ShelvesIntent.LoadNextEditorPage) },
    onRetry = { onIntent(ShelvesIntent.RetryEditor) },
    onMoveUp = { onIntent(ShelvesIntent.MoveEditorItem(it, ShelfItemMove.UP)) },
    onMoveDown = { onIntent(ShelvesIntent.MoveEditorItem(it, ShelfItemMove.DOWN)) },
    onMoveToPosition = { onIntent(ShelvesIntent.OpenEditorPosition(it)) },
    onRemove = { onIntent(ShelvesIntent.RequestEditorRemoval(it)) },
    onDismissFailure = { onIntent(ShelvesIntent.DismissEditorMutationFailure) },
    onEditDetails = { onIntent(ShelvesIntent.OpenEdit) },
    onDeleteShelf = { onIntent(ShelvesIntent.OpenDelete) },
    modifier = modifier
)

@Composable
private fun ShelfDetailDestination(
    state: ShelvesState,
    destination: ShelvesDestination.Detail,
    onIntent: (ShelvesIntent) -> Unit,
    serverMutationsAvailable: Boolean,
    onBookSelected: (ShelfBookNavigationRequest) -> Unit,
    modifier: Modifier
) = ShelfDetailContent(
    state = state.detail,
    onOrderingSelected = { onIntent(ShelvesIntent.ChangeItemOrdering(it)) },
    onLayoutSelected = { onIntent(ShelvesIntent.SetItemLayout(it)) },
    onLoadNextPage = { onIntent(ShelvesIntent.LoadNextItemPage) },
    onRetryDetail = { onIntent(ShelvesIntent.RetryDetail) },
    onRetryItems = { onIntent(ShelvesIntent.RetryItems) },
    onBookSelected = { bookId ->
        onBookSelected(ShelfBookNavigationRequest(bookId, destination.shelfId, destination.origin))
    },
    canManage = serverMutationsAvailable &&
        canManageShelf(destination.origin, state.detail.detail.shelf),
    onManageContents = { onIntent(ShelvesIntent.OpenContentsEditor) },
    modifier = modifier
)

@Composable
private fun ShelfCollectionDestination(
    state: ShelvesState,
    onIntent: (ShelvesIntent) -> Unit,
    serverMutationsAvailable: Boolean,
    scrollStates: ShelfCollectionScrollStates,
    modifier: Modifier
) = ShelvesRoot(
    state = state,
    onCollectionSelected = { onIntent(ShelvesIntent.ShowCollection(it)) },
    onOrderingSelected = { onIntent(ShelvesIntent.ChangeCollectionOrdering(it)) },
    onLoadNextPersonal = {
        onIntent(ShelvesIntent.LoadNextCollectionPage(ShelvesCollection.PERSONAL))
    },
    onLoadNextShared = {
        onIntent(ShelvesIntent.LoadNextCollectionPage(ShelvesCollection.SHARED))
    },
    onLoadNextGroup = {
        onIntent(ShelvesIntent.LoadNextCollectionPage(ShelvesCollection.GROUP))
    },
    onRetryPersonal = { onIntent(ShelvesIntent.RetryCollection(ShelvesCollection.PERSONAL)) },
    onRetryShared = { onIntent(ShelvesIntent.RetryCollection(ShelvesCollection.SHARED)) },
    onRetryGroup = { onIntent(ShelvesIntent.RetryCollection(ShelvesCollection.GROUP)) },
    onShelfSelected = { onIntent(ShelvesIntent.SelectShelf(it)) },
    onCreateShelf = { onIntent(ShelvesIntent.OpenCreate) },
    createShelfAvailable = serverMutationsAvailable,
    scrollStates = scrollStates,
    modifier = modifier
)

@Composable
private fun ShelvesMutationDialogs(state: ShelvesState, onIntent: (ShelvesIntent) -> Unit) {
    if (state.createOpen) {
        CreatePersonalShelfDialog(
            state = state.create,
            onNameChanged = { onIntent(ShelvesIntent.UpdateCreateName(it)) },
            onDescriptionChanged = { onIntent(ShelvesIntent.UpdateCreateDescription(it)) },
            onVisibilityChanged = { onIntent(ShelvesIntent.UpdateCreateVisibility(it)) },
            onSubmit = { onIntent(ShelvesIntent.SubmitCreate) },
            onDismiss = { onIntent(ShelvesIntent.DismissCreate) }
        )
    }
    if (state.edit.open) {
        EditPersonalShelfDialog(
            state = state.edit,
            onNameChanged = { onIntent(ShelvesIntent.UpdateEditName(it)) },
            onDescriptionChanged = { onIntent(ShelvesIntent.UpdateEditDescription(it)) },
            onVisibilityChanged = { onIntent(ShelvesIntent.UpdateEditVisibility(it)) },
            onSubmit = { onIntent(ShelvesIntent.SubmitEdit) },
            onDismiss = { onIntent(ShelvesIntent.DismissEdit) }
        )
    }
    if (state.delete.open) {
        DeletePersonalShelfDialog(
            state = state.delete,
            onConfirm = { onIntent(ShelvesIntent.ConfirmDelete) },
            onDismiss = { onIntent(ShelvesIntent.DismissDelete) }
        )
    }
    state.editor.positionDialog?.let { dialog ->
        ShelfPositionDialog(
            state = dialog,
            maximum = state.editor.totalCount,
            submitting = state.editor.mutation.inProgress,
            onValueChanged = { onIntent(ShelvesIntent.UpdateEditorPosition(it)) },
            onSubmit = { onIntent(ShelvesIntent.SubmitEditorPosition) },
            onDismiss = { onIntent(ShelvesIntent.DismissEditorPosition) }
        )
    }
    state.editor.removalDialog?.let { dialog ->
        RemoveShelfItemDialog(
            state = dialog,
            submitting = state.editor.mutation.inProgress,
            onConfirm = { onIntent(ShelvesIntent.ConfirmEditorRemoval) },
            onDismiss = { onIntent(ShelvesIntent.DismissEditorRemoval) }
        )
    }
}

@Composable
private fun ShelvesScaffold(
    presentation: AppBarPresentation,
    onNavigation: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ContextualAppBar(presentation, onNavigation = onNavigation)
        }
    ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
}
