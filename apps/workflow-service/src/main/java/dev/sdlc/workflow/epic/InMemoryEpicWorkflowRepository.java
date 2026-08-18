package dev.sdlc.workflow.epic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryEpicWorkflowRepository implements EpicWorkflowRepository {
    private final ConcurrentMap<String, EpicWorkflow> epics = new ConcurrentHashMap<>();

    @Override
    public Optional<EpicWorkflow> findById(String epicId) {
        return Optional.ofNullable(epics.get(epicId));
    }

    @Override
    public EpicWorkflow save(EpicWorkflow epic) {
        epics.put(epic.epicId(), epic);
        return epic;
    }

    @Override
    public List<EpicWorkflow> findAll() {
        return new ArrayList<>(epics.values());
    }
}
