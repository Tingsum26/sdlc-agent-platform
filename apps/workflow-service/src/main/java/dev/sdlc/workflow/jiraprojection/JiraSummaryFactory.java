package dev.sdlc.workflow.jiraprojection;

import dev.sdlc.workflow.artifact.ArtifactMetadata;
import dev.sdlc.workflow.ticket.TicketWorkflow;
import java.util.regex.Pattern;

public final class JiraSummaryFactory {

    public static final int MAX_LENGTH = 500;

    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]|[\\r\\n\\t]+");
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)(?<![A-Z0-9])(?:[A-Z0-9]+[_-])*(?:api[_-]?key|secret|token|password|passwd|authorization|private[_-]?key)\\s*[:=]\\s*\\S+");

    public String create(TicketWorkflow ticket, ArtifactMetadata artifact) {
        // Only fixed server-owned metadata crosses the Jira projection port.
        // Artifact titles and bodies are local-Copilot-authored content and
        // therefore never become a trusted summary source.
        String summary = "Ticket " + redact(ticket.ticketId())
                + " | " + artifact.type().name()
                + " | APPROVED";
        return summary.length() <= MAX_LENGTH ? summary : summary.substring(0, MAX_LENGTH);
    }

    private String redact(String value) {
        return SECRET_VALUE.matcher(EMAIL.matcher(URL.matcher(CONTROL_CHARACTERS.matcher(value).replaceAll(" "))
                .replaceAll("[redacted]")).replaceAll("[redacted]")).replaceAll("[redacted]")
                .replaceAll("\\s+", " ").trim();
    }
}
