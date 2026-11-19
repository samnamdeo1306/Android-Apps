package dev.notesmaker.core.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The summary and each section's points are JSON arrays in a text column (ADR-23): they are always
 * read and written with their parent and never queried on their own.
 */
internal class StringListConverter {
    private val serializer = ListSerializer(String.serializer())

    @TypeConverter
    fun toJson(list: List<String>): String = Json.encodeToString(serializer, list)

    @TypeConverter
    fun fromJson(json: String): List<String> = Json.decodeFromString(serializer, json)
}
