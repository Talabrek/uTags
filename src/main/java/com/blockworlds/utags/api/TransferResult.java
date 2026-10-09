package com.blockworlds.utags.api;

/**
 * The result of {@link UTagsApi#transferAll}.
 *
 * @param copied   ledger rows copied into the target account (0 on a replay)
 * @param replayed true when the op id had been used before, so nothing new was written
 */
public record TransferResult(int copied, boolean replayed) {
}
