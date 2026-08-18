package dev.sdlc.workflow.jiraprojection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryJiraProjectionRepository implements JiraProjectionRepository {
    private final ConcurrentMap<String, JiraProjection> projections = new ConcurrentHashMap<>();

    @Override
    public Optional<JiraProjection> findById(String projectionId) {
        return Optional.ofNullable(projections.get(projectionId));
    }

    @Override
    public JiraProjection save(JiraProjection projection) {
        projections.put(projection.projectionId(), projection);
        return projection;
    }

    @Override
    public List<JiraProjection> findAll() {
        return new ArrayList<>(projections.values());
    }
}
