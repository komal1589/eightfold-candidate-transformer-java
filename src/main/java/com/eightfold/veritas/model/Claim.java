package com.eightfold.veritas.model;

/**
 * One atomic, fully-traceable assertion about a single canonical field.
 *
 * <p>The central idea of the engine: we never merge raw values directly. Every
 * source emits Claims ("this source says full_name = 'Ada Lovelace', normalized
 * the same way, observed at time T, with base trust 0.9"). All Claims for a
 * candidate form the <b>Evidence Ledger</b>, and merging is a pure,
 * deterministic reduction over that ledger -- which is what makes every output
 * field explainable and reproducible.
 *
 * @param fieldPath  dotted canonical path, e.g. "full_name", "location.country",
 *                   "emails", "skills", "experience".
 * @param raw        the value exactly as it appeared in the source.
 * @param value      the normalized value ({@code null} if normalization failed).
 * @param method     how we derived it, e.g. "csv_column:name", "github_api:languages".
 * @param sourceId   stable id of the source type, e.g. "recruiter_csv".
 * @param trust      effective trust of the source for this field, in [0, 1].
 * @param observedAt ISO date string used as the recency tie-breaker.
 * @param order      position in the manifest -- the final deterministic tie-breaker.
 */
public record Claim(
        String fieldPath,
        Object raw,
        Object value,
        String method,
        String sourceId,
        double trust,
        String observedAt,
        int order) {
}
