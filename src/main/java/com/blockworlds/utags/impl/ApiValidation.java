package com.blockworlds.utags.impl;

import java.util.regex.Pattern;

/** The op id and source formats of the public API (see {@code UTagsApi}). */
public final class ApiValidation {

    private static final Pattern OP_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,100}");
    private static final Pattern SOURCE = Pattern.compile("[a-z0-9_.:-]{1,64}");

    private ApiValidation() {
    }

    public static boolean validOpId(String opId) {
        return opId != null && OP_ID.matcher(opId).matches();
    }

    public static boolean validSource(String source) {
        return source != null && SOURCE.matcher(source).matches();
    }

    /** Throws IllegalArgumentException (a caller bug, not an "unknown outcome") when an argument breaks the format. */
    public static void check(Object player, String tagId, String source, String opId) {
        if (player == null) throw new IllegalArgumentException("player is null");
        if (tagId == null || tagId.isBlank() || tagId.length() > 255) throw new IllegalArgumentException("invalid tag id");
        if (source != null && !validSource(source)) {
            throw new IllegalArgumentException("invalid source (1-64 characters of [a-z0-9_.:-]): " + source);
        }
        if (!validOpId(opId)) throw new IllegalArgumentException("invalid op id (1-100 characters of [A-Za-z0-9_.:-]): " + opId);
    }
}
