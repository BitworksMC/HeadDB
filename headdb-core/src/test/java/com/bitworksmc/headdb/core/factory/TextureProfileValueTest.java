package com.bitworksmc.headdb.core.factory;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextureProfileValueTest {

    @Test
    void encodesAHeadDbTextureAsAPlayerProfileProperty() {
        String url = "https://headdb.net/api/v1/textures/f6eae70943e1b01d57f48a12a766c4be3a3dddd14aa94dc9aec2d9a3a2680edb";
        String decoded = new String(
                Base64.getDecoder().decode(TextureProfileValue.fromUrl(url)),
                StandardCharsets.UTF_8
        );

        assertEquals("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}", decoded);
    }

    @Test
    void encodesMojangTexturesThroughTheSameSafePath() {
        String url = "https://textures.minecraft.net/texture/abc123";
        String decoded = new String(
                Base64.getDecoder().decode(TextureProfileValue.fromUrl(url)),
                StandardCharsets.UTF_8
        );

        assertEquals("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}", decoded);
    }

    @Test
    void rejectsNonHttpsRelativeAndUntrustedTextureUrls() {
        assertThrows(IllegalArgumentException.class,
                () -> TextureProfileValue.fromUrl("http://headdb.net/texture.png"));
        assertThrows(IllegalArgumentException.class,
                () -> TextureProfileValue.fromUrl("/api/v1/textures/abc123"));
        assertThrows(IllegalArgumentException.class,
                () -> TextureProfileValue.fromUrl("https://example.com/texture.png"));
    }
}
