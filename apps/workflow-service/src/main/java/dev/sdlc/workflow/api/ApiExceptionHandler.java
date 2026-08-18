package dev.sdlc.workflow.api;

import dev.sdlc.workflow.artifact.ArtifactHashMismatchException;
import dev.sdlc.workflow.artifact.ArtifactImmutableException;
import dev.sdlc.workflow.artifact.ArtifactNotFoundException;
import dev.sdlc.workflow.artifact.UnsafeArtifactContentException;
import dev.sdlc.workflow.conflict.WorkflowConflictException;
import dev.sdlc.workflow.security.UnauthorizedRequestException;
import dev.sdlc.workflow.identity.IdentityNotFoundException;
import dev.sdlc.workflow.pod.InvalidPodRosterException;
import dev.sdlc.workflow.pod.StaleRosterRevisionException;
import dev.sdlc.workflow.task.IllegalTaskTransitionException;
import dev.sdlc.workflow.task.StaleTaskVersionException;
import dev.sdlc.workflow.task.TaskNotFoundException;
import dev.sdlc.workflow.webhook.WebhookAuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UnauthorizedRequestException.class)
    ResponseEntity<Map<String, Object>> unauthorized(UnauthorizedRequestException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication required", exception.getMessage(), request);
    }

    @ExceptionHandler(WebhookAuthenticationException.class)
    ResponseEntity<Map<String, Object>> webhookUnauthorized(WebhookAuthenticationException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Webhook rejected", "Webhook signature validation failed", request);
    }

    @ExceptionHandler({StaleTaskVersionException.class, StaleRosterRevisionException.class,
            IllegalTaskTransitionException.class, ArtifactImmutableException.class, WorkflowConflictException.class})
    ResponseEntity<Map<String, Object>> conflict(RuntimeException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Workflow conflict", "The workflow state changed; refresh and retry", request);
    }

    @ExceptionHandler({TaskNotFoundException.class, ArtifactNotFoundException.class, IdentityNotFoundException.class})
    ResponseEntity<Map<String, Object>> notFound(RuntimeException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", "The requested resource does not exist", request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class,
            UnsafeArtifactContentException.class, ArtifactHashMismatchException.class, InvalidPodRosterException.class})
    ResponseEntity<Map<String, Object>> badRequest(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "The request failed validation", request);
    }

    private ResponseEntity<Map<String, Object>> problem(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://example.invalid/problems/" + status.value());
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("correlationId", CorrelationIdFilter.from(request));
        return ResponseEntity.status(status).body(body);
    }
}
