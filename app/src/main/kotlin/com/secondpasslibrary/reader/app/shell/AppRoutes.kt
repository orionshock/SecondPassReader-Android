package com.secondpasslibrary.reader.app.shell

import androidx.navigation3.runtime.NavKey
import com.secondpasslibrary.reader.design.icons.AppIcon
import kotlinx.serialization.Serializable

@Serializable
enum class AppDestination(val label: String, val icon: AppIcon) : NavKey {
    Home("Home", AppIcon.Home),
    Library("Library", AppIcon.Library),
    Shelves("Shelves", AppIcon.Shelf),
    Marginalia("Marginalia", AppIcon.ReadingHistory),
    Settings("Settings", AppIcon.Settings)
}

@Serializable
data class LibrarySearchRoute(val query: String) : NavKey

@Serializable
data class LibraryAuthorRoute(val authorId: String) : NavKey

@Serializable
data class LibrarySeriesRoute(val seriesId: String) : NavKey

@Serializable
data class LibraryTagRoute(val tagId: String, val tagSlug: String) : NavKey

@Serializable
data class ShelfDetailRoute(val shelfId: String, val origin: ShelfCollectionOrigin) : NavKey

@Serializable
sealed interface BookDetailReturnTarget {
    @Serializable
    data object Library : BookDetailReturnTarget

    @Serializable
    data object Home : BookDetailReturnTarget

    @Serializable
    data object Marginalia : BookDetailReturnTarget

    @Serializable
    data class ReadingSessionDetail(val route: ReadingSessionDetailRoute) :
        BookDetailReturnTarget

    @Serializable
    data class BookMarginalia(val route: BookMarginaliaRoute) : BookDetailReturnTarget

    @Serializable
    data class ShelfDetail(val shelfId: String, val origin: ShelfCollectionOrigin) :
        BookDetailReturnTarget
}

@Serializable
enum class ShelfCollectionOrigin {
    PERSONAL,
    SHARED,
    GROUP
}

@Serializable
data class BookDetailRoute(val bookId: String, val returnTarget: BookDetailReturnTarget) : NavKey

@Serializable
sealed interface ReaderReturnTarget {
    @Serializable
    data object Home : ReaderReturnTarget

    @Serializable
    data class BookDetail(val route: BookDetailRoute) : ReaderReturnTarget
}

@Serializable
data class ReaderRoute(
    val bookId: String,
    val returnTarget: ReaderReturnTarget,
    val existingSessionId: String? = null,
    val titleHint: String? = null
) : NavKey,
    AppShellDrawerGesturePolicy {
    init {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        require(existingSessionId == null || existingSessionId.isNotBlank()) {
            "Existing Reading Session ID must not be blank."
        }
        require(
            returnTarget !is ReaderReturnTarget.BookDetail || returnTarget.route.bookId == bookId
        ) { "Reader Book must match its Book Detail return target." }
    }

    override val drawerGestureEnabled: Boolean = false
}

@Serializable
sealed interface MarginaliaReturnTarget {
    @Serializable
    data class BookDetail(val route: BookDetailRoute) : MarginaliaReturnTarget

    @Serializable
    data object Library : MarginaliaReturnTarget
}

@Serializable
data class BookMarginaliaRoute(val bookId: String, val returnTarget: MarginaliaReturnTarget) :
    NavKey

@Serializable
sealed interface ReadingSessionDetailReturnTarget {
    @Serializable
    data object Home : ReadingSessionDetailReturnTarget
}

@Serializable
enum class ReadingSessionDetailRouteAction {
    VIEW,
    EDIT,
    CLOSE
}

@Serializable
data class ReadingSessionDetailRoute(
    val sessionId: String,
    val returnTarget: ReadingSessionDetailReturnTarget,
    val action: ReadingSessionDetailRouteAction = ReadingSessionDetailRouteAction.VIEW
) : NavKey

internal fun NavKey.topLevelDestination(): AppDestination = when (this) {
    is LibrarySearchRoute,
    is LibraryAuthorRoute,
    is LibrarySeriesRoute,
    is LibraryTagRoute -> AppDestination.Library

    is ShelfDetailRoute -> AppDestination.Shelves

    is BookDetailRoute -> returnTarget.topLevelDestination()

    is ReaderRoute -> returnTarget.topLevelDestination()

    is BookMarginaliaRoute -> when (val target = returnTarget) {
        is MarginaliaReturnTarget.BookDetail -> target.route.topLevelDestination()
        MarginaliaReturnTarget.Library -> AppDestination.Library
    }

    is ReadingSessionDetailRoute -> when (returnTarget) {
        ReadingSessionDetailReturnTarget.Home -> AppDestination.Home
    }

    is AppDestination -> this

    else -> AppDestination.Home
}

private fun BookDetailReturnTarget.topLevelDestination(): AppDestination = when (this) {
    BookDetailReturnTarget.Home -> AppDestination.Home
    BookDetailReturnTarget.Library -> AppDestination.Library
    BookDetailReturnTarget.Marginalia -> AppDestination.Marginalia
    is BookDetailReturnTarget.ReadingSessionDetail -> route.topLevelDestination()
    is BookDetailReturnTarget.BookMarginalia -> route.topLevelDestination()
    is BookDetailReturnTarget.ShelfDetail -> AppDestination.Shelves
}

private fun ReaderReturnTarget.topLevelDestination(): AppDestination = when (this) {
    ReaderReturnTarget.Home -> AppDestination.Home
    is ReaderReturnTarget.BookDetail -> route.topLevelDestination()
}
