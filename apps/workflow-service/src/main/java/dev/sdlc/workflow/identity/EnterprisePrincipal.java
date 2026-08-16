package dev.sdlc.workflow.identity;

import java.util.Objects;

public record EnterprisePrincipal(
        String principalId,
        String employeeId,
        String displayLabel,
        String maskedEmail,
        IdentitySource source,
        String githubLogin) {

    public EnterprisePrincipal {
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(displayLabel, "displayLabel");
        Objects.requireNonNull(source, "source");
    }
}
