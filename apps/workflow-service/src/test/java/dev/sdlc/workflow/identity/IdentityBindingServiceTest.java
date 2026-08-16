package dev.sdlc.workflow.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IdentityBindingServiceTest {

    private final IdentityBindingService service = new IdentityBindingService();

    @Test
    void bindsScrumMasterWithoutGithubAccount() {
        EnterprisePrincipal principal = service.bindAdminPrincipal(
                "EMP-100", "Fictional Scrum Master", "f***@example.invalid");

        assertEquals("EMP-100", principal.employeeId());
        assertEquals(IdentitySource.ADMIN_BINDING, principal.source());
        assertNull(principal.githubLogin());
    }

    @Test
    void rejectsRawEmailInsteadOfMaskedEmail() {
        assertThrows(IllegalArgumentException.class, () -> service.bindAdminPrincipal(
                "EMP-100", "Fictional Scrum Master", "fictional.sm@example.invalid"));
    }
}
