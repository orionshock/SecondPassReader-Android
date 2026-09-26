package com.secondpasslibrary.client

internal const val EMPTY_PAGE = """{"count":0,"next":null,"previous":null,"results":[]}"""

internal const val COMPACT_BOOK =
    """{
        "id":"book-1","title":"Shelf Book 1","sort_title":"Shelf Book 1","subtitle":"",
        "authors":[{"id":"author-1","name":"Author One"}],"series":null,
        "catalog_tags":[],"language":"eng","publisher":null,"published_year":null,
        "published_month":null,"published_day":null,"published_date_precision":"",
        "cover_url":"https://cdn.example/book-1.jpg","file_format":"epub"
    }"""

internal const val SHELF_DETAIL =
    """{
        "id":"shelf 1","name":"Personal","description":null,"owner_type":"user",
        "owner_user":{"profile_id":"profile-1","username":"reader"},"owner_group":null,
        "visibility":"private","item_count":2,"can_edit":true,
        "created_by":{"profile_id":"profile-1","username":"reader"},
        "created_at":"2026-08-01T00:00:00Z","updated_at":"2026-08-02T00:00:00Z",
        "matched_item_id":"item-7"
    }"""

internal val SHELF_ITEM =
    """{
        "id":"item-1","shelf":"shelf-1","book":$COMPACT_BOOK,"position":2,
        "added_by":{"profile_id":"profile-2","username":"adder"},
        "created_at":"2026-08-01T00:00:00Z","updated_at":"2026-08-02T00:00:00Z"
    }"""

internal const val SHELF_PAGE =
    """{
        "count":3,"next":"https://library.example/api/v1/shelves/?page=4","previous":null,
        "results":[
            $SHELF_DETAIL,
            {
                "id":"shelf-shared","name":"Another Reader","description":"Shared picks",
                "owner_type":"user",
                "owner_user":{"profile_id":"profile-2","username":"other-reader"},
                "owner_group":null,"visibility":"listed","item_count":0,"can_edit":false,
                "created_by":null,
                "created_at":"2026-08-01T00:00:00Z","updated_at":"2026-08-02T00:00:00Z",
                "matched_item_id":null,"preview_books":[]
            },
            {
                "id":"shelf-group","name":"Common Room","description":null,
                "owner_type":"group","owner_user":null,
                "owner_group":{"id":"group-1","name":"Common Room","is_public_group":true},
                "visibility":"listed","item_count":2,"can_edit":false,"created_by":null,
                "created_at":"2026-08-01T00:00:00Z","updated_at":"2026-08-02T00:00:00Z",
                "preview_books":[
                    {"id":"book-2","title":"Second","cover_url":null},
                    {"id":"book-1","title":"First","cover_url":"https://cdn.example/cover.webp"},
                    {"id":"book-2","title":"Second duplicate","cover_url":null}
                ]
            }
        ]
    }"""

internal val SHELF_ITEMS_PAGE =
    """{
        "count":2,"next":null,"previous":"https://library.example/api/v1/shelves/shelf-1/items/?page=1",
        "results":[
            {"id":"item-1","shelf":"shelf-1","book":$COMPACT_BOOK,"position":2,
             "added_by":{"profile_id":"profile-2","username":"adder"},
             "created_at":"2026-08-01T00:00:00Z","updated_at":"2026-08-02T00:00:00Z"},
            {"id":"item-2","shelf":"shelf-1","book":${COMPACT_BOOK.replace("book-1", "book-2")},
             "position":9,"added_by":null,"created_at":"2026-08-03T00:00:00Z",
             "updated_at":"2026-08-04T00:00:00Z"}
        ]
    }"""

internal val SHELF_EDITOR_PAGE =
    """{
        "count":2,"next":null,"previous":null,"visible_item_count":1,"unavailable_item_count":1,
        "results":[
            {"id":"item-visible","shelf":"shelf-1","book":$COMPACT_BOOK,"position":3,
             "unavailable":false,"added_by":{"profile_id":"profile-2","username":"adder"}},
            {"id":"item-hidden","shelf":"shelf-1","book":null,"position":8,"unavailable":true,
             "added_by":{"profile_id":"profile-2","username":"adder"}}
        ]
    }"""
