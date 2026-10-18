package io.pyahu.start.generation.domain;

import java.util.List;

/** The project templates, in sets: {@code base} for every project and one set per architecture. */
public interface TemplateCatalog {

    /** The files of a set, as paths relative to it, sorted. */
    List<String> files(String set);
}
