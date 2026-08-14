package com.bitworksmc.headdb.core.update;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class SemanticVersion implements Comparable<SemanticVersion> {

    private static final Pattern NUMERIC_IDENTIFIER = Pattern.compile("0|[1-9][0-9]*");
    private static final Pattern ALPHANUMERIC_IDENTIFIER = Pattern.compile("[0-9A-Za-z-]+");

    private final BigInteger major;
    private final BigInteger minor;
    private final BigInteger patch;
    private final List<Identifier> prerelease;

    private SemanticVersion(
            BigInteger major,
            BigInteger minor,
            BigInteger patch,
            List<Identifier> prerelease
    ) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.prerelease = List.copyOf(prerelease);
    }

    static Optional<SemanticVersion> parse(String rawVersion) {
        if (rawVersion == null) {
            return Optional.empty();
        }

        String version = rawVersion.trim();
        if (version.startsWith("v") || version.startsWith("V")) {
            version = version.substring(1);
        }
        if (version.isEmpty()) {
            return Optional.empty();
        }

        String[] buildParts = version.split("\\+", -1);
        if (buildParts.length > 2 || (buildParts.length == 2 && !validIdentifiers(buildParts[1], false))) {
            return Optional.empty();
        }

        String[] prereleaseParts = buildParts[0].split("-", 2);
        String[] core = prereleaseParts[0].split("\\.", -1);
        if (core.length != 3
                || !NUMERIC_IDENTIFIER.matcher(core[0]).matches()
                || !NUMERIC_IDENTIFIER.matcher(core[1]).matches()
                || !NUMERIC_IDENTIFIER.matcher(core[2]).matches()) {
            return Optional.empty();
        }

        List<Identifier> prerelease = new ArrayList<>();
        if (prereleaseParts.length == 2) {
            if (!validIdentifiers(prereleaseParts[1], true)) {
                return Optional.empty();
            }
            for (String value : prereleaseParts[1].split("\\.")) {
                prerelease.add(Identifier.of(value));
            }
        }

        return Optional.of(new SemanticVersion(
                new BigInteger(core[0]),
                new BigInteger(core[1]),
                new BigInteger(core[2]),
                prerelease
        ));
    }

    private static boolean validIdentifiers(String value, boolean rejectNumericLeadingZeroes) {
        if (value.isEmpty()) {
            return false;
        }
        for (String identifier : value.split("\\.", -1)) {
            if (!ALPHANUMERIC_IDENTIFIER.matcher(identifier).matches()) {
                return false;
            }
            if (rejectNumericLeadingZeroes
                    && identifier.length() > 1
                    && identifier.chars().allMatch(Character::isDigit)
                    && identifier.charAt(0) == '0') {
                return false;
            }
        }
        return true;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int comparison = major.compareTo(other.major);
        if (comparison != 0) {
            return comparison;
        }
        comparison = minor.compareTo(other.minor);
        if (comparison != 0) {
            return comparison;
        }
        comparison = patch.compareTo(other.patch);
        if (comparison != 0) {
            return comparison;
        }

        if (prerelease.isEmpty()) {
            return other.prerelease.isEmpty() ? 0 : 1;
        }
        if (other.prerelease.isEmpty()) {
            return -1;
        }

        int sharedLength = Math.min(prerelease.size(), other.prerelease.size());
        for (int index = 0; index < sharedLength; index++) {
            comparison = prerelease.get(index).compareTo(other.prerelease.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(prerelease.size(), other.prerelease.size());
    }

    private record Identifier(String value, BigInteger numericValue) implements Comparable<Identifier> {

        private static Identifier of(String value) {
            return new Identifier(
                    value,
                    value.chars().allMatch(Character::isDigit) ? new BigInteger(value) : null
            );
        }

        @Override
        public int compareTo(Identifier other) {
            if (numericValue != null && other.numericValue != null) {
                return numericValue.compareTo(other.numericValue);
            }
            if (numericValue != null) {
                return -1;
            }
            if (other.numericValue != null) {
                return 1;
            }
            return value.compareTo(other.value);
        }
    }
}
