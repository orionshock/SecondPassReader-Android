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
sealed interface BookDetailReturnTarget {
    @Serializable
    data object Library : BookDetailReturnTarget

    @Serializable
    data object Home : BookDetailReturnTarget

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
sealed interface MarginaliaReturnTarget {
    @Serializable
    data class BookDetail(val route: BookDetailRoute) : MarginaliaReturnTarget
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

    is BookDetailRoute -> when (returnTarget) {
        BookDetailReturnTarget.Home -> AppDestination.Home
        BookDetailReturnTarget.Library -> AppDestination.Library
        is BookDetailReturnTarget.ShelfDetail -> AppDestination.Shelves
    }

    is BookMarginaliaRoute -> when (val target = returnTarget) {
        is MarginaliaReturnTarget.BookDetail -> target.route.topLevelDestination()
    }

    is ReadingSessionDetailRoute -> when (returnTarget) {
        ReadingSessionDetailReturnTarget.Home -> AppDestination.Home
    }

    is AppDestination -> this

    else -> AppDestination.Home
}
