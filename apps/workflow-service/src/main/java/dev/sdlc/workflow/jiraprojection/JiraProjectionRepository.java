package dev.sdlc.workflow.jiraprojection;

import java.util.List;
import java.util.Optional;

public interface JiraProjectionRepository {
    Optional<JiraProjection> findById(String projectionId);
    JiraProjection save(JiraProjection projection);
    List<JiraProjection> findAll();
}
