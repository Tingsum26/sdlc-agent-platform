package dev.sdlc.workflow.skip;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemorySkipAttestationRepository implements SkipAttestationRepository {
    private final ConcurrentMap<String, SkipAttestation> attestations = new ConcurrentHashMap<>();

    @Override
    public SkipAttestation save(SkipAttestation attestation) {
        attestations.put(attestation.attestationId(), attestation);
        return attestation;
    }

    @Override
    public List<SkipAttestation> findByTaskId(String taskId) {
        return attestations.values().stream().filter(item -> item.taskId().equals(taskId)).toList();
    }
}
