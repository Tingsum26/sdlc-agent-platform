package dev.sdlc.workflow.logging;

import java.util.regex.Pattern;

public final class StructuredLogSanitizer {
    private static final Pattern CREDENTIAL = Pattern.compile("(?i)(token|password|cookie|secret|authorization)=\\S+");

    private StructuredLogSanitizer() {
    }

    public static String safe(String value) {
        if (value == null) {
            return "";
        }
        return CREDENTIAL.matcher(value.replace('\r', ' ').replace('\n', ' ')).replaceAll("$1=[redacted]");
    }
}
