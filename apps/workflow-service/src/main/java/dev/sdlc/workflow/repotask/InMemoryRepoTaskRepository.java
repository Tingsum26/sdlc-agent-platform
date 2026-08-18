package dev.sdlc.workflow.repotask;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryRepoTaskRepository implements RepoTaskRepository {
    private final ConcurrentMap<String, RepoTask> repoTasks = new ConcurrentHashMap<>();

    @Override
    public Optional<RepoTask> findById(String repoTaskId) {
        return Optional.ofNullable(repoTasks.get(repoTaskId));
    }

    @Override
    public RepoTask save(RepoTask repoTask) {
        repoTasks.put(repoTask.repoTaskId(), repoTask);
        return repoTask;
    }

    @Override
    public List<RepoTask> findByTicketId(String ticketId) {
        return repoTasks.values().stream().filter(task -> task.ticketId().equals(ticketId)).toList();
    }
}
