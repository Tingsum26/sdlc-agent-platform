package dev.sdlc.workflow.splunk;

import dev.sdlc.workflow.integration.SplunkDiagnosticAdapter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emits allowlisted structured audit events to Splunk through the diagnostic
 * adapter. Only the fields in the adapter's allowlist survive; everything
 * else is dropped by the sanitizer, so callers may pass rich detail; string
 * values are sanitized for {@code keyword=value} secrets only.
 *
 * Emission is best-effort: a failed publish is logged and swallowed so that
 * observability can never break the primary workflow operation.
 */
public final class SplunkAuditPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(SplunkAuditPublisher.class);

    private final SplunkDiagnosticAdapter splunk;

    public SplunkAuditPublisher(SplunkDiagnosticAdapter splunk) {
        this.splunk = splunk;
    }

    public void jiraProjection(String ticketId, String milestoneId, String status, String correlationId,
            String detail) {
        publish(List.of(Map.of(
                "component", "workflow-service",
                "event", "jira_projection",
                "correlationId", correlationId,
                "taskId", ticketId,
                "status", status,
                "detail", milestoneId + " " + detail)));
    }

    public void ciStatus(String ticketId, String repositoryAlias, String state, String correlationId) {
        publish(List.of(Map.of(
                "component", "workflow-service",
                "event", "ci_status",
                "correlationId", correlationId,
                "taskId", ticketId,
                "status", state,
                "detail", repositoryAlias)));
    }

    private void publish(List<Map<String, Object>> events) {
        try {
            splunk.publish(events);
        } catch (RuntimeException exception) {
            // Audit is fail-open: an observability outage must not break the workflow.
            LOG.warn("Splunk audit publish failed; event dropped", exception);
        }
    }
}
