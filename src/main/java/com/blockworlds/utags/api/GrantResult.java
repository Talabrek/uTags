package com.blockworlds.utags.api;

/**
 * The result of {@link UTagsApi#grant}.
 *
 * @param outcome  what the grant did; on a replay, the outcome of the first call with that op id, read from the ledger
 * @param replayed true when the op id had been used before, so nothing new was written
 */
public record GrantResult(GrantOutcome outcome, boolean replayed) {
}
