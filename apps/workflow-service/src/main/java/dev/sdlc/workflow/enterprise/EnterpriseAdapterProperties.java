package dev.sdlc.workflow.enterprise;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;

public final class EnterpriseAdapterProperties {
    private final Map<EnterpriseProvider, URI> baseUris;

    public EnterpriseAdapterProperties(Map<EnterpriseProvider, URI> baseUris) {
        this.baseUris = new EnumMap<>(baseUris);
        this.baseUris.forEach((provider, uri) -> {
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getHost() == null) {
                throw new IllegalArgumentException(provider + " requires an HTTPS base URI without credentials");
            }
        });
    }

    public URI baseUri(EnterpriseProvider provider) {
        URI uri = baseUris.get(provider);
        if (uri == null) throw new IllegalArgumentException("Provider base URI is not configured");
        return uri;
    }
}
