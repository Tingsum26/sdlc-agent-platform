package dev.sdlc.workflow.enterprise;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DeterministicFakeTransport implements EnterpriseTransport {
    private final Map<String, Deque<Object>> scripts = new HashMap<>();
    private final List<EnterpriseRequest> ledger = new ArrayList<>();

    public synchronized void script(EnterpriseProvider provider, String operation, EnterpriseResponse response) {
        scripts.computeIfAbsent(key(provider, operation), ignored -> new ArrayDeque<>()).add(response);
    }

    public synchronized void scriptFailure(EnterpriseProvider provider, String operation, EnterpriseAdapterException failure) {
        scripts.computeIfAbsent(key(provider, operation), ignored -> new ArrayDeque<>()).add(failure);
    }

    public synchronized List<EnterpriseRequest> ledger() { return List.copyOf(ledger); }

    @Override
    public synchronized EnterpriseResponse execute(
            EnterpriseRequest request, Duration timeout, EnterpriseCancellation cancellation) {
        if (cancellation.cancelled()) throw new EnterpriseAdapterException(
                EnterpriseErrorCategory.CANCELLED, false, "Provider operation cancelled");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        ledger.add(request);
        Deque<Object> queue = scripts.get(key(request.provider(), request.operation()));
        if (queue == null || queue.isEmpty()) throw new EnterpriseAdapterException(
                EnterpriseErrorCategory.CONTRACT_MISMATCH, false, "No deterministic provider scenario is configured");
        Object outcome = queue.removeFirst();
        if (outcome instanceof EnterpriseAdapterException failure) throw failure;
        EnterpriseResponse response = (EnterpriseResponse) outcome;
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw EnterpriseAdapterException.fromStatus(response.statusCode());
        return response;
    }

    private static String key(EnterpriseProvider provider, String operation) { return provider.name() + ":" + operation; }
}
