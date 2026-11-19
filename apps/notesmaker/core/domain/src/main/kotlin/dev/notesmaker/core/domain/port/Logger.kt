package dev.notesmaker.core.domain.port

/** Logging without android.util.Log in the domain (LLD Section 10). **Port.** */
public interface Logger {
    public fun debug(tag: String, message: String)

    public fun info(tag: String, message: String)

    public fun warn(tag: String, message: String, error: Throwable? = null)

    public fun error(tag: String, message: String, error: Throwable? = null)
}

/**
 * Wraps user content (transcript text, notes, file names) so it cannot reach a log by accident:
 * [toString] prints only its length. Adapters print [value] only in debug builds (HLD 9.5).
 */
public class Sensitive(public val value: String) {
    override fun toString(): String = "<redacted ${value.length} chars>"
}
