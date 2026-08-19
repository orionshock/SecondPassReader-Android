package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.Shelf

internal fun canManageShelf(origin: ShelvesCollection, shelf: Shelf?): Boolean =
    origin == ShelvesCollection.PERSONAL && shelf?.canEdit == true
