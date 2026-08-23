package com.secondpasslibrary.reader.shelves

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondpasslibrary.reader.design.components.AppBarPresentation
import com.secondpasslibrary.reader.design.components.ContextualAppBar
import com.secondpasslibrary.reader.shelves.collection.ShelvesRoot
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
    viewModel: ShelvesViewModel,
    onOpenDrawer: () -> Unit,
    onBookSelected: (ShelfBookNavigationRequest) -> Unit,
    onExitInitialDetail: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail = state.destination as? ShelvesDestination.Detail
    val editor = state.destination as? ShelvesDestination.ContentsEditor
    BackHandler(
        enabled = detail != null || editor != null,
        onBack = if (editor !=
            null
        ) {
            viewModel::backFromContentsEditor
        } else {
            onExitInitialDetail ?: viewModel::backFromDetail
        }
    )

    ShelvesScaffold(
        presentation = state.appBarPresentation(),
        onNavigation = when {
            editor != null -> viewModel::backFromContentsEditor
            detail != null -> onExitInitialDetail ?: viewModel::backFromDetail
            else -> onOpenDrawer
        }
    ) { modifier -> ShelvesDestinationContent(state, viewModel, onBookSelected, modifier) }
    ShelvesMutationDialogs(viewModel, state.createOpen)
}

@Composable
private fun ShelvesDestinationContent(
    state: ShelvesState,
    viewModel: ShelvesViewModel,
    onBookSelected: (ShelfBookNavigationRequest) -> Unit,
    modifier: Modifier
) {
    when (val destination = state.destination) {
        is ShelvesDestination.ContentsEditor ->
            ShelfEditorDestination(state.editor, viewModel, modifier)

        is ShelvesDestination.Detail ->
            ShelfDetailDestination(state, destination, viewModel, onBookSelected, modifier)

        is ShelvesDestination.Collection -> ShelfCollectionDestination(state, viewModel, modifier)
    }
}

@Composable
private fun ShelfEditorDestination(
    state: ShelfContentsEditorState,
    viewModel: ShelvesViewModel,
    modifier: Modifier
) = ShelfContentsEditorContent(
    state = state,
    onLoadNextPage = viewModel::loadNextEditorPage,
    onRetry = viewModel::retryEditor,
    onMoveUp = viewModel::moveEditorItemUp,
    onMoveDown = viewModel::moveEditorItemDown,
    onMoveToPosition = viewModel::openEditorPosition,
    onRemove = viewModel::requestEditorRemoval,
    onDismissFailure = viewModel::dismissEditorMutationFailure,
    onEditDetails = viewModel::openEdit,
    onDeleteShelf = viewModel::openDelete,
    modifier = modifier
)

@Composable
private fun ShelfDetailDestination(
    state: ShelvesState,
    destination: ShelvesDestination.Detail,
    viewModel: ShelvesViewModel,
    onBookSelected: (ShelfBookNavigationRequest) -> Unit,
    modifier: Modifier
) = ShelfDetailContent(
    state = state.detail,
    onOrderingSelected = viewModel::changeItemOrdering,
    onLayoutSelected = viewModel::setItemLayout,
    onLoadNextPage = viewModel::loadNextItemPage,
    onRetryDetail = viewModel::retryDetail,
    onRetryItems = viewModel::retryItems,
    onBookSelected = { bookId ->
        onBookSelected(ShelfBookNavigationRequest(bookId, destination.shelfId, destination.origin))
    },
    canManage = canManageShelf(destination.origin, state.detail.detail.shelf),
    onManageContents = viewModel::openContentsEditor,
    modifier = modifier
)

@Composable
private fun ShelfCollectionDestination(
    state: ShelvesState,
    viewModel: ShelvesViewModel,
    modifier: Modifier
) = ShelvesRoot(
    state = state,
    onCollectionSelected = viewModel::showCollection,
    onOrderingSelected = viewModel::changeCollectionOrdering,
    onLoadNextPersonal = viewModel::loadNextPersonalPage,
    onLoadNextShared = viewModel::loadNextSharedPage,
    onLoadNextGroup = viewModel::loadNextGroupPage,
    onRetryPersonal = viewModel::retryPersonal,
    onRetryShared = viewModel::retryShared,
    onRetryGroup = viewModel::retryGroup,
    onShelfSelected = viewModel::selectShelf,
    onCreateShelf = viewModel::openCreate,
    modifier = modifier
)

@Composable
private fun ShelvesMutationDialogs(viewModel: ShelvesViewModel, createOpen: Boolean) {
    val createState by viewModel.createState.collectAsStateWithLifecycle()
    val editState by viewModel.editState.collectAsStateWithLifecycle()
    val deleteState by viewModel.deleteState.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    if (createOpen) {
        CreatePersonalShelfDialog(
            state = createState,
            onNameChanged = viewModel::updateCreateName,
            onDescriptionChanged = viewModel::updateCreateDescription,
            onVisibilityChanged = viewModel::updateCreateVisibility,
            onSubmit = viewModel::submitCreate,
            onDismiss = viewModel::dismissCreate
        )
    }
    if (editState.open) {
        EditPersonalShelfDialog(
            state = editState,
            onNameChanged = viewModel::updateEditName,
            onDescriptionChanged = viewModel::updateEditDescription,
            onVisibilityChanged = viewModel::updateEditVisibility,
            onSubmit = viewModel::submitEdit,
            onDismiss = viewModel::dismissEdit
        )
    }
    if (deleteState.open) {
        DeletePersonalShelfDialog(
            state = deleteState,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete
        )
    }
    editorState.positionDialog?.let { dialog ->
        ShelfPositionDialog(
            state = dialog,
            maximum = editorState.totalCount,
            submitting = editorState.mutation.inProgress,
            onValueChanged = viewModel::updateEditorPosition,
            onSubmit = viewModel::submitEditorPosition,
            onDismiss = viewModel::dismissEditorPosition
        )
    }
    editorState.removalDialog?.let { dialog ->
        RemoveShelfItemDialog(
            state = dialog,
            submitting = editorState.mutation.inProgress,
            onConfirm = viewModel::confirmEditorRemoval,
            onDismiss = viewModel::dismissEditorRemoval
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
            ContextualAppBar(presentation, onNavigation)
        }
    ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
}
