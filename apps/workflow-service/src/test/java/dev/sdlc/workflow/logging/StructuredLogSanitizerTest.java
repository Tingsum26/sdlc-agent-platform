package dev.sdlc.workflow.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StructuredLogSanitizerTest {
    @Test
    void removesLineBreaksAndCredentialValues() {
        assertThat(StructuredLogSanitizer.safe("failed\npassword=secret token=abc"))
                .isEqualTo("failed password=[redacted] token=[redacted]");
    }
}
