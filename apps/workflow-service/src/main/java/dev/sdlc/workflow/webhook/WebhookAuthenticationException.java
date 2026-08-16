package dev.sdlc.workflow.webhook;

public class WebhookAuthenticationException extends RuntimeException {
    public WebhookAuthenticationException() {
        super("Webhook signature validation failed");
    }
}
