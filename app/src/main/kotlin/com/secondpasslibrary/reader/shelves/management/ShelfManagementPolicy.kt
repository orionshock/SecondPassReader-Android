package com.secondpasslibrary.reader.shelves.management

import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.reader.shelves.ShelvesCollection

internal fun canManageShelf(origin: ShelvesCollection, shelf: Shelf?): Boolean =
    origin == ShelvesCollection.PERSONAL && shelf?.canEdit == true
