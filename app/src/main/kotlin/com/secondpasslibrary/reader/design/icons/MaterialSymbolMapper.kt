package com.secondpasslibrary.reader.design.icons

import androidx.annotation.DrawableRes
import com.secondpasslibrary.reader.R

internal data class MaterialSymbol(val token: String, @param:DrawableRes val drawableResource: Int)

internal object MaterialSymbolMapper {
    // One exhaustive semantic-to-vendor vocabulary table.
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun resolve(icon: AppIcon): MaterialSymbol = when (icon) {
        AppIcon.Profile -> symbol("account_circle", R.drawable.ic_symbol_account_circle)

        AppIcon.Add -> symbol("add", R.drawable.ic_symbol_add)

        AppIcon.Publisher -> symbol("apartment", R.drawable.ic_symbol_apartment)

        AppIcon.Back -> symbol("arrow_back", R.drawable.ic_symbol_arrow_back)

        AppIcon.MoveDown,
        AppIcon.SortDescending -> symbol("arrow_downward", R.drawable.ic_symbol_arrow_downward)

        AppIcon.Forward -> symbol("arrow_forward", R.drawable.ic_symbol_arrow_forward)

        AppIcon.MoveUp,
        AppIcon.SortAscending -> symbol("arrow_upward", R.drawable.ic_symbol_arrow_upward)

        AppIcon.Series -> symbol("auto_stories", R.drawable.ic_symbol_auto_stories)

        AppIcon.SkipImportAnnotation -> symbol("block", R.drawable.ic_symbol_block)

        AppIcon.Library -> symbol("book_2", R.drawable.ic_symbol_book_2)

        AppIcon.Book -> symbol("menu_book", R.drawable.ic_symbol_menu_book)

        AppIcon.Bookmark -> symbol("bookmark", R.drawable.ic_symbol_bookmark)

        AppIcon.BookmarkFilled -> symbol("bookmark", R.drawable.ic_symbol_bookmark_filled)

        AppIcon.AddBookmark -> symbol("bookmark_add", R.drawable.ic_symbol_bookmark_add)

        AppIcon.Highlight -> symbol("border_color", R.drawable.ic_symbol_border_color)

        AppIcon.HighlightWithNote -> symbol("chat_bubble", R.drawable.ic_symbol_chat_bubble)

        AppIcon.Confirm -> symbol("check", R.drawable.ic_symbol_check)

        AppIcon.Success,
        AppIcon.PreviousLayerVisible -> symbol("check_circle", R.drawable.ic_symbol_check_circle)

        AppIcon.Previous -> symbol("chevron_left", R.drawable.ic_symbol_chevron_left)

        AppIcon.Next -> symbol("chevron_right", R.drawable.ic_symbol_chevron_right)

        AppIcon.Close -> symbol("close", R.drawable.ic_symbol_close)

        AppIcon.Delete -> symbol("delete", R.drawable.ic_symbol_delete)

        AppIcon.ClearImportReview -> symbol("delete_sweep", R.drawable.ic_symbol_delete_sweep)

        AppIcon.Completed -> symbol("done", R.drawable.ic_symbol_done)

        AppIcon.Export -> symbol("download", R.drawable.ic_symbol_download)

        AppIcon.Edit -> symbol("edit", R.drawable.ic_symbol_edit)

        AppIcon.EditAnnotation -> symbol("edit_note", R.drawable.ic_symbol_edit_note)

        AppIcon.Collapse -> symbol("expand_less", R.drawable.ic_symbol_expand_less)

        AppIcon.Expand -> symbol("expand_more", R.drawable.ic_symbol_expand_more)

        AppIcon.FinishBook -> symbol("flag", R.drawable.ic_symbol_flag)

        AppIcon.SortPositional ->
            symbol("format_list_numbered", R.drawable.ic_symbol_format_list_numbered)

        AppIcon.SortReversePositional ->
            symbol("format_list_numbered_rtl", R.drawable.ic_symbol_format_list_numbered_rtl)

        AppIcon.Group -> symbol("group", R.drawable.ic_symbol_group)

        AppIcon.GroupShelf -> symbol("group_work", R.drawable.ic_symbol_group_work)

        AppIcon.Groups -> symbol("groups", R.drawable.ic_symbol_groups)

        AppIcon.Help -> symbol("help", R.drawable.ic_symbol_help)

        AppIcon.ReadingHistory -> symbol("history", R.drawable.ic_symbol_history)

        AppIcon.Home -> symbol("home", R.drawable.ic_symbol_home)

        AppIcon.Marginalia -> symbol("ink_highlighter", R.drawable.ic_symbol_ink_highlighter)

        AppIcon.MoveShelfItemDown ->
            symbol("keyboard_arrow_down", R.drawable.ic_symbol_keyboard_arrow_down)

        AppIcon.MoveShelfItemUp ->
            symbol("keyboard_arrow_up", R.drawable.ic_symbol_keyboard_arrow_up)

        AppIcon.LibraryScope -> symbol("library_books", R.drawable.ic_symbol_library_books)

        AppIcon.ListLayout -> symbol("view_list", R.drawable.ic_symbol_view_list)

        AppIcon.GridLayout -> symbol("grid_view", R.drawable.ic_symbol_grid_view)

        AppIcon.Link -> symbol("link", R.drawable.ic_symbol_link)

        AppIcon.ConnectedLibrary ->
            symbol("local_library", R.drawable.ic_symbol_local_library)

        AppIcon.Locked -> symbol("lock", R.drawable.ic_symbol_lock)

        AppIcon.Logout -> symbol("logout", R.drawable.ic_symbol_logout)

        AppIcon.ManagedUsers -> symbol("manage_accounts", R.drawable.ic_symbol_manage_accounts)

        AppIcon.NavigationMenu,
        AppIcon.TableOfContents -> symbol("menu", R.drawable.ic_symbol_menu)

        AppIcon.OverflowHorizontal -> symbol("more_horiz", R.drawable.ic_symbol_more_horiz)

        AppIcon.OverflowVertical -> symbol("more_vert", R.drawable.ic_symbol_more_vert)

        AppIcon.JumpToLocation -> symbol("my_location", R.drawable.ic_symbol_my_location)

        AppIcon.OpenExternal -> symbol("open_in_new", R.drawable.ic_symbol_open_in_new)

        AppIcon.User,
        AppIcon.Author -> symbol("person", R.drawable.ic_symbol_person)

        AppIcon.PublicGroup -> symbol("public", R.drawable.ic_symbol_public)

        AppIcon.PreviousLayerHidden ->
            symbol("radio_button_unchecked", R.drawable.ic_symbol_radio_button_unchecked)

        AppIcon.Search -> symbol("search", R.drawable.ic_symbol_search)

        AppIcon.Tag -> symbol("sell", R.drawable.ic_symbol_sell)

        AppIcon.Settings -> symbol("settings", R.drawable.ic_symbol_settings)

        AppIcon.SharedShelf -> symbol("share", R.drawable.ic_symbol_share)

        AppIcon.Shelf -> symbol("shelves", R.drawable.ic_symbol_shelves)

        AppIcon.SortAlphabetical -> symbol("sort_by_alpha", R.drawable.ic_symbol_sort_by_alpha)

        AppIcon.CompleteImportReview -> symbol("task_alt", R.drawable.ic_symbol_task_alt)

        AppIcon.UndoImportReview -> symbol("undo", R.drawable.ic_symbol_undo)

        AppIcon.SortUnspecified -> symbol("unfold_more", R.drawable.ic_symbol_unfold_more)

        AppIcon.Import -> symbol("upload_file", R.drawable.ic_symbol_upload_file)

        AppIcon.Offline -> symbol("cloud_off", R.drawable.ic_symbol_cloud_off)
    }

    private fun symbol(token: String, @DrawableRes resource: Int) = MaterialSymbol(token, resource)
}
