package dev.sdlc.workflow.artifact;

public final class ArtifactNotFoundException extends RuntimeException {
    public ArtifactNotFoundException(String artifactId, int version) {
        super("Artifact was not found: " + artifactId + " version " + version);
    }
}
