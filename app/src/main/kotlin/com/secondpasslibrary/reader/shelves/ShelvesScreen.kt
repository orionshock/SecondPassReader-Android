package com.secondpasslibrary.reader.shelves

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

@Composable
internal fun ShelvesScreen(
    viewModel: ShelvesViewModel,
    onOpenDrawer: () -> Unit,
    onBookSelected: (ShelfBookNavigationRequest) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail = state.destination as? ShelvesDestination.Detail
    BackHandler(enabled = detail != null, onBack = viewModel::backFromDetail)

    ShelvesScaffold(
        title = state.detail.detail.shelf?.name.takeIf { detail != null } ?: "Shelves",
        detail = detail != null,
        onNavigation = if (detail == null) onOpenDrawer else viewModel::backFromDetail
    ) { modifier ->
        if (detail == null) {
            ShelvesRoot(
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
                modifier = modifier
            )
        } else {
            ShelfDetailContent(
                state = state.detail,
                onOrderingSelected = viewModel::changeItemOrdering,
                onLayoutSelected = viewModel::setItemLayout,
                onLoadNextPage = viewModel::loadNextItemPage,
                onRetryDetail = viewModel::retryDetail,
                onRetryItems = viewModel::retryItems,
                onBookSelected = { bookId ->
                    onBookSelected(
                        ShelfBookNavigationRequest(bookId, detail.shelfId, detail.origin)
                    )
                },
                modifier = modifier
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShelvesScaffold(
    title: String,
    detail: Boolean,
    onNavigation: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigation) {
                        AppIconGraphic(
                            if (detail) AppIcon.Back else AppIcon.NavigationMenu,
                            if (detail) "Back to shelves" else "Open navigation drawer"
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
            )
        }
    ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
}
