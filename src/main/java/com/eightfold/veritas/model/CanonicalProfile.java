package com.eightfold.veritas.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single trustworthy profile, fully assembled and self-describing.
 *
 * <p>This is the engine's <i>internal</i> representation. The projection layer
 * turns it into whatever shape a caller's runtime config asks for -- the engine
 * itself never changes to satisfy a new output shape.
 */
public final class CanonicalProfile {
    public final String candidateId;
    /** Scalar fields that pick a single winner from competing evidence. */
    public final Map<String, FieldResolution> fields = new LinkedHashMap<>();
    /** List-shaped fields that union rather than pick one winner. */
    public List<String> emails = new ArrayList<>();
    public List<String> phones = new ArrayList<>();
    public List<String> linksOther = new ArrayList<>();
    public List<Map<String, Object>> skills = new ArrayList<>();
    public List<Map<String, Object>> experience = new ArrayList<>();
    public List<Map<String, Object>> education = new ArrayList<>();
    public List<Map<String, Object>> provenance = new ArrayList<>();
    public double overallConfidence = 0.0;
    /** The full ledger, retained for auditing / the demo. */
    public List<Claim> ledger = new ArrayList<>();

    public CanonicalProfile(String candidateId) {
        this.candidateId = candidateId;
    }

    public Object scalar(String path) {
        FieldResolution fr = fields.get(path);
        return fr == null ? null : fr.value;
    }

    public Double confidence(String path) {
        FieldResolution fr = fields.get(path);
        return fr == null ? null : fr.confidence;
    }
}
