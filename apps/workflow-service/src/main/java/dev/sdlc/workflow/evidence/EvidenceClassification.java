package dev.sdlc.workflow.evidence;

/**
 * Describes whether a workflow transition represents normal delivery evidence or
 * a deliberately fictional public-demo transition.
 */
public enum EvidenceClassification {
    REAL,
    SIMULATED_PASS
}
