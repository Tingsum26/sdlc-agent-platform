package dev.sdlc.workflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class DemoHeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> LOOPBACKS = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String actorId = request.getHeader("X-Demo-User");
        if (actorId != null && !actorId.isBlank()) {
            if (!LOOPBACKS.contains(request.getRemoteAddr())) {
                throw new UnauthorizedRequestException("Demo header authentication is restricted to loopback clients");
            }
            request.setAttribute(CurrentUser.REQUEST_ATTRIBUTE, new CurrentUser(actorId.strip()));
        }
        filterChain.doFilter(request, response);
    }
}
