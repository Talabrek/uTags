package com.blockworlds.utags.utils;

import com.blockworlds.utags.exceptions.ValidationException;
import java.util.Collection;

/**
 * Utility class for argument validation.
 * Provides methods to check preconditions and throw appropriate exceptions if they are not met.
 */
public class Preconditions {

    /**
     * Ensures that an object reference is not null.
     *
     * @param reference The object reference to check
     * @param errorMessage The exception message to use if the check fails
     * @param <T> The type of the reference
     * @return The non-null reference
     * @throws ValidationException if reference is null
     */
    public static <T> T checkNotNull(T reference, String errorMessage) {
        if (reference == null) {
            throw new ValidationException(errorMessage);
        }
        return reference;
    }

    /**
     * Ensures that a string is not null or empty.
     *
     * @param string The string to check
     * @param errorMessage The exception message to use if the check fails
     * @return The non-empty string
     * @throws ValidationException if string is null or empty
     */
    public static String checkNotEmpty(String string, String errorMessage) {
        if (string == null || string.trim().isEmpty()) {
            throw new ValidationException(errorMessage);
        }
        return string;
    }

    /**
     * Ensures that a collection is not null or empty.
     *
     * @param collection The collection to check
     * @param errorMessage The exception message to use if the check fails
     * @param <T> The type of the collection
     * @return The non-empty collection
     * @throws ValidationException if collection is null or empty
     */
    public static <T extends Collection<?>> T checkNotEmpty(T collection, String errorMessage) {
        if (collection == null || collection.isEmpty()) {
            throw new ValidationException(errorMessage);
        }
        return collection;
    }

    /**
     * Ensures that an expression is true.
     *
     * @param expression The expression to check
     * @param errorMessage The exception message to use if the check fails
     * @throws ValidationException if expression is false
     */
    public static void checkArgument(boolean expression, String errorMessage) {
        if (!expression) {
            throw new ValidationException(errorMessage);
        }
    }

    /**
     * Ensures that a value is positive (greater than zero).
     *
     * @param value The value to check
     * @param errorMessage The exception message to use if the check fails
     * @return The value if positive
     * @throws ValidationException if value is not positive
     */
    public static int checkPositive(int value, String errorMessage) {
        if (value <= 0) {
            throw new ValidationException(errorMessage);
        }
        return value;
    }

    /**
     * Ensures that a value is not negative (greater than or equal to zero).
     *
     * @param value The value to check
     * @param errorMessage The exception message to use if the check fails
     * @return The value if not negative
     * @throws ValidationException if value is negative
     */
    public static int checkNotNegative(int value, String errorMessage) {
        if (value < 0) {
            throw new ValidationException(errorMessage);
        }
        return value;
    }

    /**
     * Ensures that a value is within the specified range.
     *
     * @param value The value to check
     * @param min The minimum allowable value (inclusive)
     * @param max The maximum allowable value (inclusive)
     * @param errorMessage The exception message to use if the check fails
     * @return The value if within range
     * @throws ValidationException if value is outside the specified range
     */
    public static int checkRange(int value, int min, int max, String errorMessage) {
        if (value < min || value > max) {
            throw new ValidationException(errorMessage);
        }
        return value;
    }

    /**
     * Ensures that a string matches a regular expression pattern.
     *
     * @param string The string to check
     * @param regex The regular expression pattern
     * @param errorMessage The exception message to use if the check fails
     * @return The string if it matches the pattern
     * @throws ValidationException if string does not match the pattern
     */
    public static String checkPattern(String string, String regex, String errorMessage) {
        checkNotNull(string, "String cannot be null");
        if (!string.matches(regex)) {
            throw new ValidationException(errorMessage);
        }
        return string;
    }

    /**
     * Ensures that a string's length is within the specified range.
     *
     * @param string The string to check
     * @param minLength The minimum allowable length (inclusive)
     * @param maxLength The maximum allowable length (inclusive)
     * @param errorMessage The exception message to use if the check fails
     * @return The string if its length is within range
     * @throws ValidationException if string's length is outside the specified range
     */
    public static String checkLength(String string, int minLength, int maxLength, String errorMessage) {
        checkNotNull(string, "String cannot be null");
        int length = string.length();
        if (length < minLength || length > maxLength) {
            throw new ValidationException(errorMessage);
        }
        return string;
    }

    /**
     * Ensures that an index is valid for a collection or array of the specified size.
     *
     * @param index The index to check
     * @param size The size of the collection or array
     * @param errorMessage The exception message to use if the check fails
     * @return The index if valid
     * @throws ValidationException if index is negative or greater than or equal to size
     */
    public static int checkIndex(int index, int size, String errorMessage) {
        if (index < 0 || index >= size) {
            throw new ValidationException(errorMessage);
        }
        return index;
    }
}
