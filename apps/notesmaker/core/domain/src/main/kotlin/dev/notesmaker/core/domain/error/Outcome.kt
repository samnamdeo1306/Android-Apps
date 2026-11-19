package dev.notesmaker.core.domain.error

import kotlin.coroutines.cancellation.CancellationException

/**
 * Result of an operation that can fail in a way the user should hear about (ADR-20).
 *
 * Expected failures are values of a sealed type [E], so callers get an exhaustive `when`.
 * Unexpected failures (bugs, corruption) stay exceptions.
 */
public sealed interface Outcome<out T, out E> {
    public data class Success<out T>(val value: T) : Outcome<T, Nothing>

    public data class Failure<out E>(val error: E) : Outcome<Nothing, E>
}

public inline fun <T, E, R> Outcome<T, E>.map(transform: (T) -> R): Outcome<R, E> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

public inline fun <T, E, R> Outcome<T, E>.flatMap(transform: (T) -> Outcome<R, E>): Outcome<R, E> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

public inline fun <T, E, F> Outcome<T, E>.mapFailure(transform: (E) -> F): Outcome<T, F> = when (this) {
    is Outcome.Success -> this
    is Outcome.Failure -> Outcome.Failure(transform(error))
}

public inline fun <T, E> Outcome<T, E>.onSuccess(action: (T) -> Unit): Outcome<T, E> {
    if (this is Outcome.Success) action(value)
    return this
}

public inline fun <T, E> Outcome<T, E>.onFailure(action: (E) -> Unit): Outcome<T, E> {
    if (this is Outcome.Failure) action(error)
    return this
}

public inline fun <T, E, R> Outcome<T, E>.fold(onSuccess: (T) -> R, onFailure: (E) -> R): R = when (this) {
    is Outcome.Success -> onSuccess(value)
    is Outcome.Failure -> onFailure(error)
}

public fun <T, E> Outcome<T, E>.getOrNull(): T? = (this as? Outcome.Success)?.value

public fun <T, E> Outcome<T, E>.failureOrNull(): E? = (this as? Outcome.Failure)?.error

public fun <T> T.asSuccess(): Outcome<T, Nothing> = Outcome.Success(this)

public fun <E> E.asFailure(): Outcome<Nothing, E> = Outcome.Failure(this)

/**
 * Like [runCatching], but rethrows [CancellationException] so structured cancellation keeps working
 * (ADR-20 rule 3). Plain `runCatching` around suspending code turns a cancelled coroutine into a
 * "failure" and keeps it running. Use this at port boundaries to convert technology exceptions.
 */
public inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    Result.failure(e)
}
