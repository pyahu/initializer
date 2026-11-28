package io.pyahu.start.generation.application;

import io.pyahu.start.generation.domain.Baselines;
import io.pyahu.start.generation.domain.Checklists;
import io.pyahu.start.generation.domain.GenerationRequest;
import io.pyahu.start.generation.domain.MonorepoDescription;
import io.pyahu.start.generation.domain.MonorepoRequest;
import io.pyahu.start.generation.domain.ProjectArchive;
import io.pyahu.start.generation.domain.ProjectDescription;
import io.pyahu.start.generation.domain.ProjectFile;
import io.pyahu.start.generation.domain.ProjectRecipe;
import io.pyahu.start.generation.domain.ProjectRequest;
import io.pyahu.start.generation.domain.ProjectWriter;
import io.pyahu.start.generation.domain.TemplateCatalog;
import java.util.List;
import org.springframework.stereotype.Service;

/** Turns a validated request into a zip, on the active baseline. Nothing is stored. */
@Service
public class GenerateProject {

    private final Baselines baselines;
    private final ProjectRecipe recipe;
    private final ProjectWriter writer;

    GenerateProject(Baselines baselines, TemplateCatalog templates, Checklists checklists, ProjectWriter writer) {
        this.baselines = baselines;
        this.recipe = new ProjectRecipe(templates, checklists);
        this.writer = writer;
    }

    public ProjectDescription describe(ProjectRequest request) {
        return recipe.describe(request, baselines.active());
    }

    public ProjectArchive generate(ProjectRequest request) {
        return writer.write(describe(request));
    }

    public MonorepoDescription describe(MonorepoRequest request) {
        return recipe.describe(request, baselines.active());
    }

    /** A zip of one service or of a monorepo. */
    public ProjectArchive generate(GenerationRequest request) {
        return switch (request) {
            case ProjectRequest single -> generate(single);
            case MonorepoRequest monorepo -> writer.write(describe(monorepo));
        };
    }

    /** The files of one service or of a monorepo, sorted by path. */
    public List<ProjectFile> files(GenerationRequest request) {
        return switch (request) {
            case ProjectRequest single -> writer.files(describe(single));
            case MonorepoRequest monorepo -> writer.files(describe(monorepo));
        };
    }
}
