package io.pyahu.start.generation.application;

import io.pyahu.start.generation.domain.Baselines;
import io.pyahu.start.generation.domain.Checklists;
import io.pyahu.start.generation.domain.ProjectArchive;
import io.pyahu.start.generation.domain.ProjectDescription;
import io.pyahu.start.generation.domain.ProjectRecipe;
import io.pyahu.start.generation.domain.ProjectRequest;
import io.pyahu.start.generation.domain.ProjectWriter;
import io.pyahu.start.generation.domain.TemplateCatalog;
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
}
