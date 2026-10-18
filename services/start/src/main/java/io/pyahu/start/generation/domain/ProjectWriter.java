package io.pyahu.start.generation.domain;

/** Turns a description into the files of the project. */
public interface ProjectWriter {

    ProjectArchive write(ProjectDescription description);
}
