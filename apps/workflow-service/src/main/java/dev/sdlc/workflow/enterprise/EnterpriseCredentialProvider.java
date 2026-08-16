package dev.sdlc.workflow.enterprise;

import java.util.Optional;

public interface EnterpriseCredentialProvider {
    Optional<String> authorizationValue(EnterpriseProvider provider);
}
