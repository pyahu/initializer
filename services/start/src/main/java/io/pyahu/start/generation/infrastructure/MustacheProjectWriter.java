package io.pyahu.start.generation.infrastructure;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import io.pyahu.start.generation.domain.Auth;
import io.pyahu.start.generation.domain.ChecklistItem;
import io.pyahu.start.generation.domain.Contribution;
import io.pyahu.start.generation.domain.Contribution.CatalogLibrary;
import io.pyahu.start.generation.domain.Contribution.CatalogPlugin;
import io.pyahu.start.generation.domain.Contribution.CatalogVersion;
import io.pyahu.start.generation.domain.Contribution.ChecklistEntry;
import io.pyahu.start.generation.domain.Contribution.Dependency;
import io.pyahu.start.generation.domain.Contribution.Property;
import io.pyahu.start.generation.domain.Contribution.Section;
import io.pyahu.start.generation.domain.Contribution.Value;
import io.pyahu.start.generation.domain.Database;
import io.pyahu.start.generation.domain.Frontend;
import io.pyahu.start.generation.domain.ProjectArchive;
import io.pyahu.start.generation.domain.ProjectDescription;
import io.pyahu.start.generation.domain.ProjectRequest;
import io.pyahu.start.generation.domain.ProjectWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Renders every file with JMustache and packs them into a zip under a folder named after the project.
 *
 * <p>Templates use {@code [[ ]]} as delimiters, so the {@code ${{ }}} of GitHub Actions and the {@code {{ }}} of
 * other tools pass through untouched. A missing value fails the generation instead of writing an empty string.
 * Sections test the flag of one option, such as {@code [[#postgres]]}; anything more belongs in the recipe.
 */
@Component
class MustacheProjectWriter implements ProjectWriter {

    /** Copied as they are: binaries and the wrapper scripts, which are not templates. */
    private static final Set<String> VERBATIM = Set.of("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar");

    private final ClasspathTemplates templates;
    private final Mustache.Compiler compiler =
            Mustache.compiler().withDelims("[[ ]]").escapeHTML(false).strictSections(false);
    private final Map<String, Template> compiled = new ConcurrentHashMap<>();

    MustacheProjectWriter(ClasspathTemplates templates) {
        this.templates = templates;
    }

    @Override
    public ProjectArchive write(ProjectDescription description) {
        Map<String, Object> model = model(description);
        String root = description.request().name() + "/";
        ZipArchive zip = new ZipArchive();
        for (Contribution.File file : description.all(Contribution.File.class)) {
            zip.add(root + file.path(), render(file, model), file.executable());
        }
        return new ProjectArchive(description.request().name() + ".zip", zip.toByteArray());
    }

    /** The files and their contents, for tests and a later preview. */
    Map<String, String> renderText(ProjectDescription description) {
        Map<String, Object> model = model(description);
        Map<String, String> out = new LinkedHashMap<>();
        for (Contribution.File file : description.all(Contribution.File.class)) {
            out.put(file.path(), new String(render(file, model), StandardCharsets.UTF_8));
        }
        return out;
    }

    private byte[] render(Contribution.File file, Map<String, Object> model) {
        byte[] raw = templates.read(file.template());
        if (VERBATIM.contains(file.path())) {
            return raw;
        }
        Template template = compiled.computeIfAbsent(
                file.template(), t -> compiler.compile(new String(raw, StandardCharsets.UTF_8)));
        String text = template.execute(model);
        if (file.path().endsWith(".java")) {
            text = JavaImports.sort(text);
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static Map<String, Object> model(ProjectDescription d) {
        ProjectRequest r = d.request();
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("name", r.name());
        model.put("group", r.group());
        model.put("packageName", r.packageName());
        model.put("description", r.description());
        model.put("architecture", r.architecture().id());
        // One flag per option, for the sections of files that differ by option.
        model.put("postgres", r.database() == Database.POSTGRES);
        model.put("oidc", r.auth() == Auth.OIDC);
        model.put("nextjs", r.frontend() == Frontend.NEXTJS);
        // The frontend's login: OIDC with Next.js.
        model.put("webAuth", r.auth() == Auth.OIDC && r.frontend() == Frontend.NEXTJS);
        for (Value v : d.all(Value.class)) {
            model.put(v.name(), v.value());
        }
        model.put("versions", d.all(CatalogVersion.class));
        model.put("libraries", d.all(CatalogLibrary.class));
        model.put("plugins", d.all(CatalogPlugin.class));
        model.put("dependencySections", sections(d.all(Dependency.class)));
        model.put("applicationYaml", ApplicationYaml.write(d.all(Property.class)));
        model.put("parts", checklist(d.all(ChecklistEntry.class)));
        return model;
    }

    private static List<Map<String, Object>> sections(List<Dependency> dependencies) {
        List<Map<String, Object>> sections = new ArrayList<>();
        for (Section section : Section.values()) {
            List<Dependency> in =
                    dependencies.stream().filter(x -> x.section() == section).toList();
            if (!in.isEmpty()) {
                sections.add(Map.of("first", sections.isEmpty(), "dependencies", in));
            }
        }
        return sections;
    }

    record ChecklistPart(String title, List<ChecklistGuide> guides) {}

    record ChecklistGuide(String title, String url, List<ChecklistLine> items) {}

    record ChecklistLine(String id, String text, String url) {}

    private static List<ChecklistPart> checklist(List<ChecklistEntry> entries) {
        Map<String, ChecklistPart> parts = new LinkedHashMap<>();
        Map<String, ChecklistGuide> guides = new LinkedHashMap<>();
        for (ChecklistEntry entry : entries) {
            ChecklistItem item = entry.item();
            String guideUrl = "https://initializer.pyahu.io/guides/" + item.guide() + "/";
            ChecklistPart part = parts.computeIfAbsent(
                    item.part(), p -> new ChecklistPart("Part " + item.partNumber() + ": " + p, new ArrayList<>()));
            ChecklistGuide guide = guides.computeIfAbsent(item.guide(), g -> {
                ChecklistGuide created = new ChecklistGuide(item.guideTitle(), guideUrl, new ArrayList<>());
                part.guides().add(created);
                return created;
            });
            guide.items().add(new ChecklistLine(item.id(), item.text(), guideUrl + "#" + item.card()));
        }
        return List.copyOf(parts.values());
    }
}
