package com.eightfold.veritas.adapters;

import java.util.List;

/**
 * Adapter contract. Each adapter maps one messy input format onto a list of
 * {@link ClaimDraft}s (field, raw, value, method). The pipeline stamps source
 * id / trust / order / timestamp uniformly, so adapters focus only on "what
 * does my format say", not bookkeeping.
 *
 * <p>Adapters must never let bad input crash the run; the pipeline wraps each in
 * a sandbox, but adapters also guard internally so a malformed row degrades to
 * "no claim".
 */
public interface Adapter {
    String typeName();

    List<ClaimDraft> extract(String raw);

    /** A single un-stamped assertion produced by an adapter. */
    record ClaimDraft(String fieldPath, Object raw, Object value, String method) {
        static ClaimDraft of(String fieldPath, Object raw, Object value, String method) {
            return new ClaimDraft(fieldPath, raw, value, method);
        }
    }
}
