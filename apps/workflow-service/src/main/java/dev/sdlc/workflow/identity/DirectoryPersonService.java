package dev.sdlc.workflow.identity;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks every known person independently of whether they installed the
 * workbench. Roster imports create {@code NOT_ONBOARDED} entries; identity
 * binding flips them to {@code ONBOARDED}. A roster import never downgrades
 * an already-onboarded person.
 */
public final class DirectoryPersonService {

    private final ConcurrentMap<String, DirectoryPerson> persons = new ConcurrentHashMap<>();
    private final Clock clock;

    public DirectoryPersonService(Clock clock) {
        this.clock = clock;
    }

    public DirectoryPerson upsert(String principalId, String employeeId, String displayLabel,
            OnboardingStatus onboardingStatus) {
        requireText(principalId, "principalId");
        requireText(employeeId, "employeeId");
        requireText(displayLabel, "displayLabel");
        DirectoryPerson person = new DirectoryPerson(principalId, employeeId, displayLabel, onboardingStatus,
                clock.instant());
        persons.put(principalId, person);
        return person;
    }

    public DirectoryPerson upsertFromRoster(String principalId, String employeeId, String displayLabel) {
        OnboardingStatus existing = persons.containsKey(principalId)
                ? persons.get(principalId).onboardingStatus()
                : OnboardingStatus.NOT_ONBOARDED;
        return upsert(principalId, employeeId, displayLabel, existing);
    }

    public Optional<DirectoryPerson> findByPrincipalId(String principalId) {
        return Optional.ofNullable(persons.get(principalId));
    }

    public List<DirectoryPerson> listAll() {
        List<DirectoryPerson> all = new ArrayList<>(persons.values());
        all.sort(Comparator.comparing(DirectoryPerson::employeeId));
        return all;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
