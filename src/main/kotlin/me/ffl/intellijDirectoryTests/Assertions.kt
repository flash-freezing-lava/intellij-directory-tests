package me.ffl.intellijDirectoryTests

import io.kotest.assertions.Actual
import io.kotest.assertions.AssertionErrorBuilder
import io.kotest.assertions.Expected

/// OLD ASSERTION ERROR BUILDERS FROM KOTEST 5

fun failure(message: String): AssertionError {
    return AssertionErrorBuilder.create()
        .withMessage(message)
        .build()
}

fun failure(message: String, cause: Throwable?): AssertionError {
    return AssertionErrorBuilder.create()
        .withMessage(message)
        .withCause(cause)
        .build()
}

fun failure(expected: Expected, actual: Actual, message: String): AssertionError {
    return AssertionErrorBuilder.create()
        .withMessage(message)
        .withValues(expected, actual)
        .build()
}

/// OWN ASSERTIONS

fun <T: Any> T?.shouldNotBeNull(message: () -> String): T {
    assert(this != null, message)
    return this!!
}
