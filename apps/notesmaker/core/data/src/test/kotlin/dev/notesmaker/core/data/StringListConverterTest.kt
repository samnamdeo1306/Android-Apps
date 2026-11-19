package dev.notesmaker.core.data.db

import org.junit.Test
import kotlin.test.assertEquals

class StringListConverterTest {
    private val converter = StringListConverter()

    @Test
    fun `round-trips text with quotes, commas, newlines and non-Latin scripts`() {
        val items = listOf("E = mc²", "He said \"photosynthesis\", twice", "line one\nline two", "Фотосинтез", "光合作用", "")
        assertEquals(items, converter.fromJson(converter.toJson(items)))
    }

    @Test
    fun `stores a plain JSON array`() {
        assertEquals("""["a","b"]""", converter.toJson(listOf("a", "b")))
        assertEquals(emptyList(), converter.fromJson("[]"))
    }
}
