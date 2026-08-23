# Icon vocabulary

Feature and shell code use `AppIcon` semantic keys and `AppIconGraphic`; raw Material token selection and vector resource IDs stay in `design.icons.MaterialSymbolMapper`. The canonical visual source is Material Symbols Outlined at 24dp. Official VectorDrawables are bundled so icons render offline and do not depend on the discontinued Compose Material Icons catalog or a font provider.

## Canonical mapping

| App semantic key | Material token | Meaning | Known usage |
| --- | --- | --- | --- |
| `Profile` | `account_circle` | Profile identity | Account/profile surfaces |
| `Add` | `add` | Add or create | Contextual creation actions |
| `Publisher` | `apartment` | Publisher metadata | Book metadata |
| `Back` | `arrow_back` | Return | Reader/activity return |
| `MoveDown`, `SortDescending` | `arrow_downward` | Move down or descending/newest first | Ordering controls |
| `Forward` | `arrow_forward` | Navigate forward or finish and continue | Sequential workflows |
| `MoveUp`, `SortAscending` | `arrow_upward` | Move up or ascending/oldest first | Ordering controls |
| `Series` | `auto_stories` | Series identity or empty recent-reading state | Series metadata/sort, recent reading |
| `SkipImportAnnotation` | `block` | Skip import-review annotation | Cross-project reference only |
| `Library` | `book_2` | Books collection or missing-cover fallback | Drawer Library, dashboard Books |
| `Bookmark` | `bookmark` | Bookmark identity | Marginalia |
| `AddBookmark` | `bookmark_add` | Add bookmark here | Reader action |
| `RemoveBookmark` | `bookmark_added` | Current location is bookmarked; remove it | Reader toggle state |
| `Highlight` | `border_color` | Highlight without note | Marginalia |
| `HighlightWithNote` | `chat_bubble` | Highlight with attached note | Marginalia |
| `Confirm` | `check` | Confirm, save, or current selection | Forms and menus |
| `Success`, `PreviousLayerVisible` | `check_circle` | Success or visible prior-session layer | Feedback, marginalia layers |
| `Previous` | `chevron_left` | Previous item/page | Pagination |
| `Next` | `chevron_right` | Next item/page | Pagination |
| `Close` | `close` | Close, cancel, dismiss, or contextually close session | Dialogs and sessions |
| `Delete` | `delete` | Destructive removal | Confirmed destructive actions |
| `ClearImportReview` | `delete_sweep` | Clear import review | Cross-project reference only |
| `Completed` | `done` | Completed state or leave edit mode | Shelf editing and completion |
| `Export` | `download` | Export marginalia/session data | Future export |
| `Edit` | `edit` | Edit entity metadata | Session/shelf metadata |
| `EditAnnotation` | `edit_note` | Add/edit note or annotation fallback | Marginalia |
| `Collapse` | `expand_less` | Collapse menu/order surface | Expandable controls |
| `Expand` | `expand_more` | Expand menu/order/scope surface | Expandable controls |
| `FinishBook` | `flag` | Finish book/session with no next book | Completion affordance |
| `SortPositional` | `format_list_numbered` | Natural, positional, or quantity order | Sort controls |
| `SortReversePositional` | `format_list_numbered_rtl` | Reverse shelf/series order | Sort controls |
| `Group` | `group` | One private/custom group | Group identity |
| `GroupShelf` | `group_work` | Group-owned shelf | Shelf ownership |
| `Groups` | `groups` | Groups collection or private group scope | Group navigation/scope |
| `Help` | `help` | Contextual help | Help affordances |
| `Sessions` | `history` | Reading Sessions or marginalia history | Drawer Marginalia, history navigation |
| `Home` | `home` | Application Home | Drawer Home |
| `Marginalia` | `ink_highlighter` | Marginalia workspace | Reader menu/workspace |
| `MoveShelfItemDown` | `keyboard_arrow_down` | Move shelf item down | Shelf editing |
| `MoveShelfItemUp` | `keyboard_arrow_up` | Move shelf item up | Shelf editing |
| `LibraryScope` | `library_books` | All-library scope or book-count order | Filtering/order controls |
| `Link` | `link` | Begin client linking | Connection flow |
| `ConnectedLibrary` | `local_library` | Connected server-library or library root | Home status, breadcrumbs |
| `Locked` | `lock` | Inaccessible shelf item | Access state |
| `Logout` | `logout` | Cooperatively revoke the current client session | Settings connection actions |
| `ManagedUsers` | `manage_accounts` | Managed users administration | Server-side reference only |
| `NavigationMenu`, `TableOfContents` | `menu` | Open app drawer or reader TOC | Shell menu; future reader TOC |
| `Book` | `menu_book` | One specific book or book-oriented view | Book detail/reader context |
| `OverflowHorizontal` | `more_horiz` | Horizontal overflow | Navigation/menu overflow |
| `OverflowVertical` | `more_vert` | Item/card overflow | Shelf and item actions |
| `JumpToLocation` | `my_location` | Jump to annotation location | Reader marginalia |
| `OpenExternal` | `open_in_new` | Open external authorization/workspace | Pairing authorization |
| `User`, `Author` | `person` | User, author, or personal ownership | Identity and ordering |
| `PublicGroup` | `public` | Public group identity/scope | Group display |
| `PreviousLayerHidden` | `radio_button_unchecked` | Prior-session layer hidden | Marginalia layers |
| `Search` | `search` | Search or in-book search | Search fields/actions |
| `Tag` | `sell` | Catalog tag | Book metadata |
| `Settings` | `settings` | Application/server settings | Drawer Settings |
| `SharedShelf` | `share` | Shelf shared by another user | Shelf ownership |
| `Shelf` | `shelves` | Personal/general shelf | Drawer Shelves, shelf identity |
| `SortAlphabetical` | `sort_by_alpha` | Alphabetical order | Sort controls |
| `CompleteImportReview` | `task_alt` | Manually complete import-review row | Cross-project reference only |
| `UndoImportReview` | `undo` | Undo import-review completion | Cross-project reference only |
| `SortUnspecified` | `unfold_more` | Sortable column not currently sorted | Sort controls |
| `Import` | `upload_file` | Book or marginalia import | Future only when Android scope requires it |
| `Offline` | `cloud_off` | Section refresh is unreachable while cached content remains usable | Home cached-data status |
| — | `warning_amber` | Test-fixture warning | Not a production semantic key or bundled asset |

## Required distinctions and aliases

- `Library` / `book_2` means the books collection or a missing cover. `Book` / `menu_book` means one specific book or a book-oriented surface. `ConnectedLibrary` / `local_library` means the server-library root.
- `Group` is one group; `Groups` is the collection. `Shelf` is personal/general, `SharedShelf` is shared by others, and `GroupShelf` is group-owned.
- `User` and `Author` intentionally share `person`, but remain separate semantic keys so future design changes do not leak into feature code.
- `NavigationMenu` and `TableOfContents` intentionally share the hamburger symbol while preserving separate shell and reader meanings.
- Reference-only import-review and managed-user symbols remain documented for cross-project consistency but must not be treated as current Android feature scope.

## Asset provenance

Bundled vectors come from Google's Apache-2.0-licensed `material-design-icons` repository. Revision and license details are recorded under `third_party/material-symbols/`.
