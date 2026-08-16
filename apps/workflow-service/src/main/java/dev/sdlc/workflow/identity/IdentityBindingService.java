package dev.sdlc.workflow.identity;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

public final class IdentityBindingService {

    private static final Pattern MASKED_EMAIL = Pattern.compile("^[^@]*\\*+[^@]*@example\\.invalid$");
    private final ConcurrentMap<String, EnterprisePrincipal> principals = new ConcurrentHashMap<>();

    public EnterprisePrincipal bindAdminPrincipal(String employeeId, String displayLabel, String maskedEmail) {
        requireText(employeeId, "employeeId");
        requireText(displayLabel, "displayLabel");
        if (maskedEmail != null && !MASKED_EMAIL.matcher(maskedEmail).matches()) {
            throw new IllegalArgumentException("Only a masked example.invalid email may be stored");
        }
        EnterprisePrincipal principal = new EnterprisePrincipal(
                "PRINCIPAL-" + employeeId, employeeId, displayLabel, maskedEmail,
                IdentitySource.ADMIN_BINDING, null);
        principals.put(principal.principalId(), principal);
        return principal;
    }

    public Optional<EnterprisePrincipal> findByPrincipalId(String principalId) {
        return Optional.ofNullable(principals.get(principalId));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
