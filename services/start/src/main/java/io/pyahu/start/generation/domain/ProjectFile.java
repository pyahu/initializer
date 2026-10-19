package io.pyahu.start.generation.domain;

import static java.util.Objects.requireNonNull;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * One rendered file of a project.
 *
 * @param path relative to the project folder, such as {@code src/main/resources/application.yaml}
 * @param executable whether the zip marks it executable, as {@code gradlew}
 */
public record ProjectFile(String path, byte[] content, boolean executable) {

    public ProjectFile {
        requireNonNull(path, "path");
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public int size() {
        return content.length;
    }

    /** The content as UTF-8 text; empty for a binary such as the wrapper jar. */
    public Optional<String> text() {
        for (byte b : content) {
            if (b == 0) {
                return Optional.empty();
            }
        }
        try {
            return Optional.of(StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString());
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
    }
}
