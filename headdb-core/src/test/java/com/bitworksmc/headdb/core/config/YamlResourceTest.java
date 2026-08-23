package com.bitworksmc.headdb.core.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class YamlResourceTest {
    @Test
    void bundledYamlFilesAreValid() throws Exception {
        for (String resource : List.of("categories.yml", "config.yml", "messages/en.yml",
                "messages/es.yml", "plugin.yml", "sounds.yml")) {
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream, () -> "Missing resource: " + resource);
                assertDoesNotThrow(() -> new Yaml().load(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)),
                        () -> "Invalid YAML resource: " + resource);
            }
        }
    }
}
