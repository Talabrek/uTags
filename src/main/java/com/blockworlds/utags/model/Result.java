package com.blockworlds.utags.model;

/**
 * Generic class for operation results with success/failure states.
 * @param <T> The type of the contained value
 */
public class Result<T> {
    private final boolean success;
    private final T value;
    private final String message;
    private final Throwable error;

    private Result(boolean success, T value, String message, Throwable error) {
        this.success = success;
        this.value = value;
        this.message = message;
        this.error = error;
    }

    /** Creates a successful result with a value */
    public static <T> Result<T> success(T value) {
        return new Result<>(true, value, null, null);
    }

    /** Creates a failure result with a message */
    public static <T> Result<T> failure(String message) {
        return new Result<>(false, null, message, null);
    }

    /** Creates a failure result with a message and error */
    public static <T> Result<T> error(String message, Throwable error) {
        return new Result<>(false, null, message, error);
    }

    /** Returns whether the operation was successful */
    public boolean isSuccess() {
        return success;
    }

    /** Returns the value (may be null if not successful) */
    public T getValue() {
        return value;
    }

    /** Returns the error message (may be null if successful) */
    public String getMessage() {
        return message;
    }

    /** Returns the error (may be null if successful or no error provided) */
    public Throwable getError() {
        return error;
    }
}
