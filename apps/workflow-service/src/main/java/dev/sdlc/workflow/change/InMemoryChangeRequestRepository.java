package dev.sdlc.workflow.change;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryChangeRequestRepository implements ChangeRequestRepository {
    private final ConcurrentMap<String, EpicChangeRequest> requests = new ConcurrentHashMap<>();

    @Override
    public Optional<EpicChangeRequest> findById(String changeRequestId) {
        return Optional.ofNullable(requests.get(changeRequestId));
    }

    @Override
    public EpicChangeRequest save(EpicChangeRequest changeRequest) {
        requests.put(changeRequest.changeRequestId(), changeRequest);
        return changeRequest;
    }

    @Override
    public List<EpicChangeRequest> findByEpicId(String epicId) {
        return requests.values().stream().filter(request -> request.epicId().equals(epicId)).toList();
    }
}
