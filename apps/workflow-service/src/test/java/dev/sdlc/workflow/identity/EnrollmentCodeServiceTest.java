package dev.sdlc.workflow.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class EnrollmentCodeServiceTest {

    @Test
    void bindsANonGithubIdentityWithAOneTimeCode() {
        EnrollmentCodeService service = new EnrollmentCodeService(new IdentityBindingService(), Clock.systemUTC());

        String code = service.issueCode("EMP-777");
        EnterprisePrincipal principal = service.bind(code, "Fictional BA", "b***@example.invalid");

        assertEquals("PRINCIPAL-EMP-777", principal.principalId());
        assertEquals("EMP-777", principal.employeeId());
        assertEquals(IdentitySource.ADMIN_BINDING, principal.source());
    }

    @Test
    void rejectsReuseOfAnEnrollmentCode() {
        EnrollmentCodeService service = new EnrollmentCodeService(new IdentityBindingService(), Clock.systemUTC());

        String code = service.issueCode("EMP-778");
        service.bind(code, "Fictional BA", "b***@example.invalid");

        assertThrows(IllegalArgumentException.class, () -> service.bind(code, "Fictional Other", "o***@example.invalid"));
    }

    @Test
    void rejectsAnExpiredEnrollmentCode() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-18T00:00:00Z"));
        EnrollmentCodeService service = new EnrollmentCodeService(new IdentityBindingService(), clock);

        String code = service.issueCode("EMP-779");
        clock.advance(Duration.ofMinutes(16));

        assertThrows(IllegalArgumentException.class, () -> service.bind(code, "Fictional BA", "b***@example.invalid"));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
