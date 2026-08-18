package dev.sdlc.workflow.dependency;

import java.util.List;
import java.util.Optional;

public interface DependencyRepository {
    Optional<Dependency> findById(String dependencyId);
    Dependency save(Dependency dependency);
    List<Dependency> findByEpicId(String epicId);
}
