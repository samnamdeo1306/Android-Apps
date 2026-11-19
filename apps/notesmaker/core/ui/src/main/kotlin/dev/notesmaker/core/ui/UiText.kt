package dev.notesmaker.core.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource

/**
 * Text for the UI that a ViewModel can build without a `Context`, so ViewModels stay JVM-testable
 * and the text follows configuration changes such as a language switch.
 */
@Immutable
sealed interface UiText {
    data class Resource(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** Text that is already user content, such as a session title. Never for app copy (NFR-52). */
    data class Literal(val text: String) : UiText
}

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Resource -> context.getString(id, *args.toTypedArray())
    is UiText.Literal -> text
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Resource -> stringResource(id, *args.toTypedArray())
    is UiText.Literal -> text
}
