package dev.sdlc.workflow.repotask;

import java.util.List;
import java.util.Optional;

public interface RepoTaskRepository {
    Optional<RepoTask> findById(String repoTaskId);
    RepoTask save(RepoTask repoTask);
    List<RepoTask> findByTicketId(String ticketId);
}
