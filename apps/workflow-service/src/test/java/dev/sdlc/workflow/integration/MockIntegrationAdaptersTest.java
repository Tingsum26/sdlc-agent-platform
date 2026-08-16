package dev.sdlc.workflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MockIntegrationAdaptersTest {
    @Test
    void exposesDeterministicPublicFixturesWithoutMutatingExternalSystems() {
        TicketAdapter ticket = new MockTicketAdapter();
        CiStatusAdapter ci = new MockCiStatusAdapter();
        ScmEventAdapter scm = new MockScmEventAdapter();

        assertThat(ticket.getTicket("DEMO-123").summary()).isEqualTo("Public demo requirement");
        assertThat(ci.getStatus("REPO_A", "0123456").state()).isEqualTo(CiState.PASSED);
        assertThat(scm.normalize("pull_request", "delivery-1").repositoryAlias()).isEqualTo("REPO_A");
    }
}
