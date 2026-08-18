package dev.sdlc.workflow.dependency;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryDependencyRepository implements DependencyRepository {
    private final ConcurrentMap<String, Dependency> dependencies = new ConcurrentHashMap<>();

    @Override
    public Optional<Dependency> findById(String dependencyId) {
        return Optional.ofNullable(dependencies.get(dependencyId));
    }

    @Override
    public Dependency save(Dependency dependency) {
        dependencies.put(dependency.dependencyId(), dependency);
        return dependency;
    }

    @Override
    public List<Dependency> findByEpicId(String epicId) {
        return dependencies.values().stream().filter(dep -> dep.epicId().equals(epicId)).toList();
    }
}
