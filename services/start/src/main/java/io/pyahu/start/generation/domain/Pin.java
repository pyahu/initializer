package io.pyahu.start.generation.domain;

import static java.util.Objects.requireNonNull;

import org.jspecify.annotations.Nullable;

/**
 * One component of a baseline revision.
 *
 * @param image the image reference with its tag, for images that name it
 * @param digest the image digest, for images
 * @param distributionSha256Sum the download checksum, for Gradle
 */
public record Pin(
        String version,
        @Nullable String image,
        @Nullable String digest,
        @Nullable String distributionSha256Sum) {

    public Pin {
        requireNonNull(version, "version");
    }

    public static Pin of(String version) {
        return new Pin(version, null, null, null);
    }
}
