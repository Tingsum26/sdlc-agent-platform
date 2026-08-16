package dev.sdlc.workflow.persistence;

import dev.sdlc.workflow.pod.PodRoster;
import dev.sdlc.workflow.pod.PodRosterRepository;
import dev.sdlc.workflow.pod.StaleRosterRevisionException;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public final class MongoPodRosterRepository implements PodRosterRepository {
    static final String COLLECTION = "podRosters";
    private final MongoOperations mongo;

    public MongoPodRosterRepository(MongoOperations mongo) { this.mongo = mongo; }

    @Override
    public Optional<PodRoster> find(String journeyId) {
        return Optional.ofNullable(mongo.findById(journeyId, PodRosterDocument.class, COLLECTION))
                .map(PodRosterDocument::toDomain);
    }

    @Override
    public PodRoster save(PodRoster roster, long expectedRevision) {
        PodRosterDocument document = PodRosterDocument.fromDomain(roster);
        if (expectedRevision == 0) {
            try {
                return mongo.insert(document, COLLECTION).toDomain();
            } catch (DuplicateKeyException exception) {
                throw new StaleRosterRevisionException("Pod roster revision changed");
            }
        }
        Query query = Query.query(Criteria.where("_id").is(roster.journeyId()).and("revision").is(expectedRevision));
        PodRosterDocument saved = mongo.findAndReplace(query, document,
                FindAndReplaceOptions.options().returnNew(), COLLECTION);
        if (saved == null) throw new StaleRosterRevisionException("Pod roster revision changed");
        return saved.toDomain();
    }
}
