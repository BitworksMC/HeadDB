package com.bitworksmc.headdb.legacy;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class LegacyWebsiteLinks {
    private static final String DEFAULT_BASE_URL = "https://headdb.net";

    private LegacyWebsiteLinks() {
    }

    static String submissionUrl(String configuredBaseUrl) {
        return normalizeBaseUrl(configuredBaseUrl) + "/submit";
    }

    static String headUrl(String configuredBaseUrl, int headId) {
        return normalizeBaseUrl(configuredBaseUrl) + "/heads/" + headId;
    }

    static String searchUrl(String configuredBaseUrl, String[] args) {
        String category = null;
        Set<String> tags = new LinkedHashSet<String>();
        List<Integer> ids = new ArrayList<Integer>();
        List<String> names = new ArrayList<String>();
        boolean matchAny = false;

        List<String> logicalArguments = combineQuotedArguments(args, 1);
        for (String token : logicalArguments) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (lower.equals("--any")) {
                matchAny = true;
                continue;
            }
            if (lower.startsWith("category:")) {
                category = token.substring(9);
            } else if (lower.startsWith("tags:")) {
                String[] split = token.substring(5).split(",");
                for (String tag : split) {
                    if (!tag.trim().isEmpty()) {
                        tags.add(tag.trim());
                    }
                }
            } else if (lower.startsWith("ids:")) {
                String[] split = token.substring(4).split(",");
                for (String id : split) {
                    try {
                        ids.add(Integer.parseInt(id.trim()));
                    } catch (NumberFormatException ignored) {
                        // The search command already handles invalid legacy ID filters.
                    }
                }
            } else {
                names.add(token);
            }
        }

        List<String> parameters = new ArrayList<String>();
        String name = join(names);
        if (!name.isEmpty()) {
            parameters.add(parameter("q", name));
        }

        String categorySlug = slugify(category);
        if (!categorySlug.isEmpty()) {
            parameters.add(parameter("category", categorySlug));
        }

        List<String> tagSlugs = new ArrayList<String>();
        for (String tag : tags) {
            String slug = slugify(tag);
            if (!slug.isEmpty()) {
                tagSlugs.add(slug);
            }
        }
        if (!tagSlugs.isEmpty()) {
            parameters.add(parameter("tags", join(tagSlugs, ",")));
        }

        if (!ids.isEmpty()) {
            List<String> idValues = new ArrayList<String>();
            for (Integer id : ids) {
                if (id != null && id > 0) idValues.add(String.valueOf(id));
            }
            if (!idValues.isEmpty()) parameters.add(parameter("ids", join(idValues, ",")));
        }
        if (matchAny) parameters.add(parameter("match", "any"));

        String url = normalizeBaseUrl(configuredBaseUrl) + "/heads";
        return parameters.isEmpty() ? url : url + "?" + join(parameters, "&");
    }

    private static String normalizeBaseUrl(String configuredBaseUrl) {
        if (configuredBaseUrl == null || configuredBaseUrl.trim().isEmpty()) {
            return DEFAULT_BASE_URL;
        }

        String value = configuredBaseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        try {
            URI uri = URI.create(value);
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
            // Use the official site below.
        }
        return DEFAULT_BASE_URL;
    }

    private static String slugify(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT)
                .replace("&", " and ")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static String parameter(String name, String value) {
        return encode(name) + "=" + encode(value);
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String join(List<String> values) {
        return join(values, " ");
    }

    private static String join(List<String> values, String separator) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(separator);
            }
            result.append(value);
        }
        return result.toString();
    }

    static List<String> combineQuotedArguments(String[] raw, int start) {
        List<String> result = new ArrayList<String>();
        StringBuilder pending = new StringBuilder();
        boolean quoted = false;
        for (int i = start; i < raw.length; i++) {
            String token = raw[i];
            if (!quoted) {
                int quote = token.indexOf('"');
                if (quote < 0) {
                    result.add(token);
                    continue;
                }
                quoted = true;
                pending.append(token.substring(0, quote)).append(token.substring(quote + 1));
            } else {
                pending.append(' ').append(token);
            }
            int end = pending.indexOf("\"");
            if (end >= 0) {
                pending.deleteCharAt(end);
                result.add(pending.toString());
                pending.setLength(0);
                quoted = false;
            }
        }
        if (pending.length() > 0) result.add(pending.toString());
        return result;
    }
}
