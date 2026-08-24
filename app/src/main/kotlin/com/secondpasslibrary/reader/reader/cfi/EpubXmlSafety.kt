package com.secondpasslibrary.reader.reader.cfi

private const val UNSIGNED_BYTE_MASK = 0xFF
private val FORBIDDEN_XML_DECLARATIONS = listOf("DOCTYPE", "ENTITY")

internal object EpubXmlSafety {
    fun containsForbiddenDeclaration(bytes: ByteArray): Boolean {
        var cursor = 0
        var forbiddenDeclarationFound = false
        while (cursor < bytes.size && !forbiddenDeclarationFound) {
            val opening = bytes.indexOfAscii('<', cursor)
            if (opening < 0) {
                cursor = bytes.size
            } else {
                forbiddenDeclarationFound = bytes.hasForbiddenDeclarationAfter(opening)
                cursor = opening + 1
            }
        }
        return forbiddenDeclarationFound
    }

    private fun ByteArray.hasForbiddenDeclarationAfter(opening: Int): Boolean {
        var declaration = nextNonNullByte(opening + 1)
        if (declaration >= size || this[declaration].toInt().toChar() != '!') return false
        declaration = nextNonNullByte(declaration + 1)
        while (declaration < size && this[declaration].toInt().toChar().isWhitespace()) {
            declaration = nextNonNullByte(declaration + 1)
        }
        return FORBIDDEN_XML_DECLARATIONS.any { matchesAscii(declaration, it) }
    }

    private fun ByteArray.indexOfAscii(value: Char, startIndex: Int): Int {
        for (index in startIndex until size) {
            if ((this[index].toInt() and UNSIGNED_BYTE_MASK).toChar() == value) return index
        }
        return -1
    }

    private fun ByteArray.nextNonNullByte(startIndex: Int): Int {
        var index = startIndex
        while (index < size && this[index] == 0.toByte()) index += 1
        return index
    }

    private fun ByteArray.matchesAscii(startIndex: Int, expected: String): Boolean {
        var index = startIndex
        expected.forEach { expectedCharacter ->
            index = nextNonNullByte(index)
            if (index >= size ||
                (this[index].toInt() and UNSIGNED_BYTE_MASK).toChar().uppercaseChar() !=
                expectedCharacter
            ) {
                return false
            }
            index += 1
        }
        return true
    }
}
