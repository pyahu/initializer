package io.pyahu.start.generation.domain;

import java.util.Arrays;
import java.util.Optional;

/** One value of an option that has a fixed list, such as an architecture. */
public interface Choice {

    /** What a request sends, such as {@code ddd-layered}. */
    String id();

    /** What a form shows, such as {@code DDD layered}. */
    String title();

    static <E extends Enum<E> & Choice> Optional<E> of(Class<E> type, String id) {
        return Arrays.stream(type.getEnumConstants())
                .filter(c -> c.id().equals(id))
                .findFirst();
    }
}
