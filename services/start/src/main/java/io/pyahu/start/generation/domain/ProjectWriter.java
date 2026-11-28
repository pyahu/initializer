package io.pyahu.start.generation.domain;

import java.util.List;

/** Turns a description into the files of the project. */
public interface ProjectWriter {

    /** Every file, sorted by path. */
    List<ProjectFile> files(ProjectDescription description);

    /** The same files as a zip, under a folder named after the project. */
    ProjectArchive write(ProjectDescription description);

    /** Every file of the platform and of each vertical, sorted by path. */
    List<ProjectFile> files(MonorepoDescription description);

    /** The same files as a zip, under a folder named after the platform. */
    ProjectArchive write(MonorepoDescription description);
}
