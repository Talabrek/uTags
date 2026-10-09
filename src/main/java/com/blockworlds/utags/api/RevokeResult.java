package com.blockworlds.utags.api;

/**
 * The result of {@link UTagsApi#revoke}.
 *
 * @param outcome  what the revoke did; on a replay, the outcome of the first call with that op id, read from the ledger
 * @param replayed true when the op id had been used before, so nothing changed
 */
public record RevokeResult(RevokeOutcome outcome, boolean replayed) {
}
