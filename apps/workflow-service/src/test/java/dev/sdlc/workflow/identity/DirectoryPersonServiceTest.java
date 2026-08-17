package dev.sdlc.workflow.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import org.junit.jupiter.api.Test;

class DirectoryPersonServiceTest {

    @Test
    void rosterUpsertKeepsNewMembersNotOnboarded() {
        DirectoryPersonService service = new DirectoryPersonService(Clock.systemUTC());

        service.upsertFromRoster("PRINCIPAL-EMP-201", "EMP-201", "Fictional Developer");

        DirectoryPerson person = service.findByPrincipalId("PRINCIPAL-EMP-201").orElseThrow();
        assertEquals(OnboardingStatus.NOT_ONBOARDED, person.onboardingStatus());
        assertEquals("EMP-201", person.employeeId());
        assertEquals(1, service.listAll().size());
    }

    @Test
    void rosterUpsertPreservesAnAlreadyOnboardedStatus() {
        DirectoryPersonService service = new DirectoryPersonService(Clock.systemUTC());
        service.upsert("PRINCIPAL-EMP-100", "EMP-100", "Fictional Scrum Master", OnboardingStatus.ONBOARDED);

        DirectoryPerson person = service.upsertFromRoster("PRINCIPAL-EMP-100", "EMP-100", "Fictional Scrum Master");

        assertEquals(OnboardingStatus.ONBOARDED, person.onboardingStatus());
    }

    @Test
    void explicitUpsertOverridesStatusForIdentityBinding() {
        DirectoryPersonService service = new DirectoryPersonService(Clock.systemUTC());
        service.upsertFromRoster("PRINCIPAL-EMP-301", "EMP-301", "Fictional iOS Developer");

        DirectoryPerson bound = service.upsert("PRINCIPAL-EMP-301", "EMP-301", "Fictional iOS Developer",
                OnboardingStatus.ONBOARDED);

        assertEquals(OnboardingStatus.ONBOARDED, bound.onboardingStatus());
    }
}
