package dev.sdlc.workflow.jiraprojection;

import dev.sdlc.workflow.artifact.ArtifactMetadata;
import dev.sdlc.workflow.ticket.TicketWorkflow;

public final class JiraSummaryFactory {

    public static final int MAX_LENGTH = 500;

    public String create(TicketWorkflow ticket, ArtifactMetadata artifact) {
        // Jira ticket identifiers use company-specific formats and are caller-controlled.
        // Keep outbound summaries restricted to fixed wording plus approved artifact metadata.
        String summary = "Approved SDLC artifact | " + artifact.type().name() + " | APPROVED";
        return summary.length() <= MAX_LENGTH ? summary : summary.substring(0, MAX_LENGTH);
    }
}
