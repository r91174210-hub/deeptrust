package com.deeptrust.user;

/**
 * Strict role hierarchy for DeepTrust.
 * ROOT is reserved exclusively for the founder account and is never
 * assignable through any public or admin API — only via the bootstrap
 * runner (see com.deeptrust.bootstrap.RootUserBootstrap).
 */
public enum Role {
    ROOT,          // Founder-only. Full system control, audit chain verification.
    ANALYST,       // Reviews AI verdicts, issues certificates.
    INVESTIGATOR,  // Reviews INCONCLUSIVE results, approves revocations.
    OPERATOR,      // Uploads media, triggers analysis pipeline.
    VIEWER         // Read-only access to verification results.
}
