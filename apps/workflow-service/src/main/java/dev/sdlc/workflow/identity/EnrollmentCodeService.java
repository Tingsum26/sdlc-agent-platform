package dev.sdlc.workflow.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Issues one-time enrollment codes so non-GitHub roles (Scrum Master, BA, QA)
 * can bind an enterprise identity without holding a GitHub account.
 */
public final class EnrollmentCodeService {

    private static final Duration CODE_TTL = Duration.ofMinutes(15);

    private final ConcurrentMap<String, Enrollment> pending = new ConcurrentHashMap<>();
    private final IdentityBindingService bindings;
    private final Clock clock;

    public EnrollmentCodeService(IdentityBindingService bindings, Clock clock) {
        this.bindings = bindings;
        this.clock = clock;
    }

    public String issueCode(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            throw new IllegalArgumentException("employeeId is required");
        }
        String code = "ENROLL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        pending.put(code, new Enrollment(employeeId, clock.instant().plus(CODE_TTL)));
        return code;
    }

    public EnterprisePrincipal bind(String code, String displayLabel, String maskedEmail) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        Enrollment enrollment = pending.get(code);
        if (enrollment == null) {
            throw new IllegalArgumentException("Unknown enrollment code");
        }
        if (clock.instant().isAfter(enrollment.expiresAt())) {
            pending.remove(code);
            throw new IllegalArgumentException("Enrollment code expired");
        }
        pending.remove(code);
        return bindings.bindAdminPrincipal(enrollment.employeeId(), displayLabel, maskedEmail);
    }

    private record Enrollment(String employeeId, Instant expiresAt) {
    }
}
