package dev.notesmaker.core.domain.model

/**
 * A resource-free label for something the domain names, such as an engine. `:core:ui` resolves
 * [key] to a string resource and falls back to [fallback] for keys it does not know (for example a
 * new engine module that ships before its translations).
 */
public data class UiLabel(val key: String, val fallback: String)
