package io.pyahu.start.generation.application;

import io.pyahu.start.generation.domain.GenerationRequest;
import io.pyahu.start.generation.domain.ProjectFile;
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

    PreviewProject(GenerateProject generateProject) {
        this.generateProject = generateProject;
    }

    public Preview preview(GenerationRequest request) {
        return new Preview(request.name(), generateProject.files(request));
    }
}
