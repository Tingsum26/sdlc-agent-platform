package dev.sdlc.workflow.enterprise;

import java.time.Duration;

public interface EnterpriseTransport {
    EnterpriseResponse execute(EnterpriseRequest request, Duration timeout, EnterpriseCancellation cancellation);
}
