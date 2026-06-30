package com.eightfold.veritas.model;

import java.util.List;

/** The winning value for one scalar field plus its confidence and full trace. */
public final class FieldResolution {
    public final Object value;
    public final double confidence;
    public final String winningSource;
    public final String method;
    public final List<Claim> evidence;   // every claim that contributed or competed
    public final int agreed;             // distinct sources agreeing with the winner
    public final int conflicted;         // distinct sources disagreeing

    public FieldResolution(Object value, double confidence, String winningSource,
                           String method, List<Claim> evidence, int agreed, int conflicted) {
        this.value = value;
        this.confidence = confidence;
        this.winningSource = winningSource;
        this.method = method;
        this.evidence = evidence;
        this.agreed = agreed;
        this.conflicted = conflicted;
    }
}
