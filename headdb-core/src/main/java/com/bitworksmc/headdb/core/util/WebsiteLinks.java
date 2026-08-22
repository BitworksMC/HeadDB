package com.bitworksmc.headdb.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Builds the public HeadDB website links shown by the plugin. */
public final class WebsiteLinks {

    public static final String DEFAULT_BASE_URL = "https://headdb.net";

    private WebsiteLinks() {
    }

    public static String normalizeBaseUrl(String configuredUrl) {
        if (configuredUrl == null || configuredUrl.isBlank()) {
            return DEFAULT_BASE_URL;
        }

        String value = configuredUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
            // Use the official site when a server owner enters an invalid URL.
        }
        return DEFAULT_BASE_URL;
    }

    public static String submissionUrl(String baseUrl) {
        return normalizeBaseUrl(baseUrl) + "/submit";
    }

    public static String searchUrl(
            String baseUrl,
            String name,
            String category,
            Collection<String> tags,
            Collection<Integer> ids
    ) {
        List<String> parameters = new ArrayList<>();
        String trimmedName = name == null ? "" : name.trim();
        if (!trimmedName.isEmpty()) {
            parameters.add(parameter("q", trimmedName));
        } else if (ids != null && ids.size() == 1) {
            parameters.add(parameter("q", String.valueOf(ids.iterator().next())));
        }

        String categorySlug = slugify(category);
        if (!categorySlug.isEmpty()) {
            parameters.add(parameter("category", categorySlug));
        }

        if (tags != null) {
            String tagSlugs = tags.stream()
                    .map(WebsiteLinks::slugify)
                    .filter(tag -> !tag.isEmpty())
                    .distinct()
                    .collect(Collectors.joining(","));
            if (!tagSlugs.isEmpty()) {
                parameters.add(parameter("tags", tagSlugs));
            }
        }

        String url = normalizeBaseUrl(baseUrl) + "/heads";
        return parameters.isEmpty() ? url : url + "?" + String.join("&", parameters);
    }

    public static Component makeClickable(Component message, String url, Component hoverText) {
        return message
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(hoverText));
    }

    static String slugify(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("&", " and ")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static String parameter(String name, String value) {
        return encode(name) + "=" + encode(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
