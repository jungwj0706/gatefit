package com.gatefit.core.result

sealed interface DataResult<out T> {
    data class Success<T>(val value: T) : DataResult<T>
    data class Failure(val error: DataError) : DataResult<Nothing>
}

sealed interface DataError {
    data object Network : DataError
    data object NotConfigured : DataError
    data object NotFound : DataError
    data class Server(val code: Int) : DataError
    data class Invalid(val reason: String) : DataError
    data class Unexpected(val message: String?) : DataError
}

inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> DataResult.Success(transform(value))
    is DataResult.Failure -> this
}

fun <T> DataResult<T>.getOrNull(): T? = (this as? DataResult.Success)?.value
