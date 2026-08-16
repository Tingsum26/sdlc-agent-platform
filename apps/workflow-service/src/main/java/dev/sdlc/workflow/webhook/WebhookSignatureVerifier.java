package dev.sdlc.workflow.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class WebhookSignatureVerifier {
    private static final String PREFIX = "sha256=";
    private final byte[] secret;

    public WebhookSignatureVerifier(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Webhook secret must be configured");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public void verify(byte[] rawBody, String suppliedSignature) {
        if (suppliedSignature == null || !suppliedSignature.startsWith(PREFIX)) {
            throw new WebhookAuthenticationException();
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody);
            byte[] supplied = HexFormat.of().parseHex(suppliedSignature.substring(PREFIX.length()));
            if (!MessageDigest.isEqual(expected, supplied)) {
                throw new WebhookAuthenticationException();
            }
        } catch (IllegalArgumentException exception) {
            throw new WebhookAuthenticationException();
        } catch (WebhookAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to validate webhook signature", exception);
        }
    }
}
