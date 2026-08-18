package dev.sdlc.workflow.epic;

import java.util.List;
import java.util.Optional;

public interface EpicWorkflowRepository {
    Optional<EpicWorkflow> findById(String epicId);
    EpicWorkflow save(EpicWorkflow epic);
    List<EpicWorkflow> findAll();
}
