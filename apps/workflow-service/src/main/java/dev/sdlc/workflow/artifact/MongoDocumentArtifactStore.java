package dev.sdlc.workflow.artifact;

import java.util.Optional;
import dev.sdlc.workflow.persistence.ArtifactDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public final class MongoDocumentArtifactStore implements ArtifactStore {

    private static final String COLLECTION = "artifacts";
    private final MongoOperations mongoOperations;

    public MongoDocumentArtifactStore(MongoOperations mongoOperations) {
        this.mongoOperations = mongoOperations;
    }

    @Override
    public ArtifactMetadata save(ArtifactMetadata artifact) {
        return mongoOperations.save(ArtifactDocument.fromDomain(artifact), COLLECTION).toDomain();
    }

    @Override
    public Optional<ArtifactMetadata> find(String artifactId, int version) {
        Query query = Query.query(Criteria.where("artifactId").is(artifactId).and("version").is(version));
        return Optional.ofNullable(mongoOperations.findOne(query, ArtifactDocument.class, COLLECTION))
                .map(ArtifactDocument::toDomain);
    }

    @Override
    public Optional<ArtifactMetadata> findLatest(String artifactId) {
        Query query = Query.query(Criteria.where("artifactId").is(artifactId))
                .with(Sort.by(Sort.Direction.DESC, "version"))
                .limit(1);
        return Optional.ofNullable(mongoOperations.findOne(query, ArtifactDocument.class, COLLECTION))
                .map(ArtifactDocument::toDomain);
    }
}
