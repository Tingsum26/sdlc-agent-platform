package dev.sdlc.workflow.enterprise;

public final class EnterpriseAdapterException extends RuntimeException {
    private final EnterpriseErrorCategory category;
    private final boolean retryable;

    public EnterpriseAdapterException(EnterpriseErrorCategory category, boolean retryable, String safeMessage) {
        super(safeMessage);
        this.category = category;
        this.retryable = retryable;
    }

    public EnterpriseErrorCategory category() { return category; }
    public boolean retryable() { return retryable; }

    public static EnterpriseAdapterException fromStatus(int status) {
        if (status == 429) return new EnterpriseAdapterException(EnterpriseErrorCategory.RATE_LIMIT, true, "Provider rate limited the operation");
        if (status == 401) return new EnterpriseAdapterException(EnterpriseErrorCategory.AUTHENTICATION, false, "Provider authentication failed");
        if (status == 403) return new EnterpriseAdapterException(EnterpriseErrorCategory.AUTHORIZATION, false, "Provider authorization failed");
        return new EnterpriseAdapterException(EnterpriseErrorCategory.TRANSPORT, status >= 500, "Provider request failed with status class " + (status / 100) + "xx");
    }
}
