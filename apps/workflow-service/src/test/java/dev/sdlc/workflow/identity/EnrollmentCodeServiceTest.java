package dev.sdlc.workflow.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void onlyOneThreadCanBindTheSameCode() throws Exception {
        EnrollmentCodeService service = new EnrollmentCodeService(new IdentityBindingService(), Clock.systemUTC());
        String code = service.issueCode("EMP-780");
        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            workers.add(new Thread(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                try {
                    service.bind(code, "Fictional " + Thread.currentThread().getName(), "w***@example.invalid");
                    successes.incrementAndGet();
                } catch (IllegalArgumentException expected) {
                    // unknown code for every loser
                }
            }));
            workers.get(i).start();
        }
        ready.await();
        go.countDown();
        for (Thread worker : workers) { worker.join(); }
        assertEquals(1, successes.get());
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
