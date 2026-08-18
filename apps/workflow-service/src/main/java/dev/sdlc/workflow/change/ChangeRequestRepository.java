package dev.sdlc.workflow.change;

import java.util.List;
import java.util.Optional;

public interface ChangeRequestRepository {
    Optional<EpicChangeRequest> findById(String changeRequestId);
    EpicChangeRequest save(EpicChangeRequest changeRequest);
    List<EpicChangeRequest> findByEpicId(String epicId);
}
