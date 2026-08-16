package dev.sdlc.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("fake")
class WorkflowServiceApplicationTest {

    @Test
    void startsWithTheInfrastructureFreeFakeProfile() {
    }
}
