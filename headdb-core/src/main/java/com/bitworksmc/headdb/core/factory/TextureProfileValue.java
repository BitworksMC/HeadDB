package com.bitworksmc.headdb.core.factory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

final class TextureProfileValue {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "textures.minecraft.net",
            "headdb.net",
            "www.headdb.net"
    );

    private TextureProfileValue() {
    }

    static String fromUrl(String textureUrl) {
        return fromUrl(parseTrustedUrl(textureUrl));
    }

    static String fromUrl(URI uri) {
        parseTrustedUrl(uri.toASCIIString());
        String payload = "{\"textures\":{\"SKIN\":{\"url\":\""
                + uri.toASCIIString()
                + "\"}}}";
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    static URI parseTrustedUrl(String textureUrl) {
        URI uri = URI.create(textureUrl);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null
                || !"https".equals(scheme.toLowerCase(Locale.ROOT))
                || host == null) {
            throw new IllegalArgumentException("Texture URL must be an absolute HTTPS URL");
        }
        if (!ALLOWED_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Texture URL host is not trusted: " + host);
        }
        return uri;
    }

    static boolean isMojangUrl(URI uri) {
        return "textures.minecraft.net".equalsIgnoreCase(uri.getHost());
    }
}
