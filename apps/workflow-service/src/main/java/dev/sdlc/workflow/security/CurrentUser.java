package dev.sdlc.workflow.security;

import jakarta.servlet.http.HttpServletRequest;

public record CurrentUser(String actorId) {

    public static final String REQUEST_ATTRIBUTE = CurrentUser.class.getName();

    public static CurrentUser require(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        if (value instanceof CurrentUser currentUser) {
            return currentUser;
        }
        throw new UnauthorizedRequestException("Authenticated user is required");
    }
}
