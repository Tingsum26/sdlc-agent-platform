package dev.sdlc.workflow.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sdlc.workflow.api.CorrelationIdFilter;
import dev.sdlc.workflow.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ObjectMapper mapper;

    public RequestLoggingFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("timestamp", Instant.now().toString());
            event.put("level", "INFO");
            event.put("component", "workflow-service");
            event.put("event", "http_request_completed");
            event.put("correlationId", CorrelationIdFilter.from(request));
            event.put("method", request.getMethod());
            event.put("path", StructuredLogSanitizer.safe(request.getRequestURI()));
            event.put("status", response.getStatus());
            event.put("durationMs", (System.nanoTime() - start) / 1_000_000L);
            Object current = request.getAttribute(CurrentUser.REQUEST_ATTRIBUTE);
            if (current instanceof CurrentUser user) {
                event.put("actorId", user.actorId());
            }
            LOG.info(asJson(event));
        }
    }

    private String asJson(Map<String, Object> event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            return "{\"component\":\"workflow-service\",\"event\":\"log_serialization_failed\"}";
        }
    }
}
