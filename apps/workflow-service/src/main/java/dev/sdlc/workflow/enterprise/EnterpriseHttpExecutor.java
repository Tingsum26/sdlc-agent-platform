package dev.sdlc.workflow.enterprise;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

@FunctionalInterface
public interface EnterpriseHttpExecutor {
    EnterpriseResponse execute(String method, URI uri, Map<String, String> headers, String body, Duration timeout);
}
