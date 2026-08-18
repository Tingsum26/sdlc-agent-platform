package dev.sdlc.workflow.skip;

import java.util.List;

public interface SkipAttestationRepository {
    SkipAttestation save(SkipAttestation attestation);
    List<SkipAttestation> findByTaskId(String taskId);
}
