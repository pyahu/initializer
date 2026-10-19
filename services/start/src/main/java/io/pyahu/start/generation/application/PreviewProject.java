package io.pyahu.start.generation.application;

import io.pyahu.start.generation.domain.ProjectFile;
import io.pyahu.start.generation.domain.ProjectRequest;
import io.pyahu.start.generation.domain.ProjectWriter;
import java.util.List;
import org.springframework.stereotype.Service;

/** The files a request would give, to look at before downloading them. Nothing is stored. */
@Service
public class PreviewProject {

    /** The files under {@code root}, the folder the zip puts them in. */
    public record Preview(String root, List<ProjectFile> files) {

        public Preview {
            files = List.copyOf(files);
        }
    }

    private final GenerateProject generateProject;
    private final ProjectWriter writer;

    PreviewProject(GenerateProject generateProject, ProjectWriter writer) {
        this.generateProject = generateProject;
        this.writer = writer;
    }

    public Preview preview(ProjectRequest request) {
        return new Preview(request.name(), writer.files(generateProject.describe(request)));
    }
}
