package io.pyahu.start.generation.presentation;

import io.micrometer.core.instrument.MeterRegistry;
import io.pyahu.start.generation.application.DescribeOptions;
import io.pyahu.start.generation.application.GenerateProject;
import io.pyahu.start.generation.application.PreviewProject;
import io.pyahu.start.generation.domain.FieldError;
import io.pyahu.start.generation.domain.InvalidProjectRequest;
import io.pyahu.start.generation.domain.ProjectArchive;
import io.pyahu.start.generation.domain.ProjectField;
import io.pyahu.start.generation.domain.ProjectRequest;
import io.pyahu.start.generation.presentation.api.StarterApi;
import io.pyahu.start.generation.presentation.api.model.BaselineInfo;
import io.pyahu.start.generation.presentation.api.model.Choice;
import io.pyahu.start.generation.presentation.api.model.Field;
import io.pyahu.start.generation.presentation.api.model.Metadata;
import io.pyahu.start.generation.presentation.api.model.Preview;
import io.pyahu.start.generation.presentation.api.model.PreviewFile;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** The generated contract, implemented: maps the HTTP fields to a request and the engine's answers back. */
@RestController
class StarterController implements StarterApi {

    private static final URI MAINTENANCE = URI.create("https://initializer.pyahu.io/maintenance/");
    private static final Set<String> FIELDS =
            Set.copyOf(ProjectField.ALL.stream().map(ProjectField::id).toList());

    private final GenerateProject generateProject;
    private final PreviewProject previewProject;
    private final DescribeOptions describeOptions;
    private final GenerationLimit limit;
    private final HttpServletRequest http;
    private final MeterRegistry meters;

    StarterController(
            GenerateProject generateProject,
            PreviewProject previewProject,
            DescribeOptions describeOptions,
            GenerationLimit limit,
            HttpServletRequest http,
            MeterRegistry meters) {
        this.generateProject = generateProject;
        this.previewProject = previewProject;
        this.describeOptions = describeOptions;
        this.limit = limit;
        this.http = http;
        this.meters = meters;
    }

    @Override
    public ResponseEntity<Metadata> getMetadata() {
        DescribeOptions.Options options = describeOptions.describe();
        var baseline = options.baseline();
        List<Field> fields =
                options.fields().stream().map(StarterController::field).toList();
        return ResponseEntity.ok(new Metadata(
                new BaselineInfo(baseline.id(), baseline.java(), baseline.springBoot(), MAINTENANCE), fields));
    }

    private static Field field(ProjectField f) {
        Field field = new Field(f.id(), f.description())
                .defaultValue(f.defaultValue())
                .pattern(f.pattern().pattern())
                .maxLength(f.maxLength());
        if (!f.choices().isEmpty()) {
            field.choices(f.choices().stream()
                    .map(c -> new Choice(c.id(), c.title(), c.id().equals(f.defaultValue())))
                    .toList());
        }
        return field;
    }

    // The generated signatures name every field; the request is read from the parameter map instead, which also
    // holds the fields the contract does not know.

    @Override
    public ResponseEntity<Resource> getStarter(
            @Nullable String name,
            @Nullable String group,
            @Nullable String _package,
            @Nullable String description,
            @Nullable String architecture,
            @Nullable String database,
            @Nullable String auth,
            @Nullable String frontend) {
        return starter();
    }

    @Override
    public ResponseEntity<Resource> postStarter(
            @Nullable String name,
            @Nullable String group,
            @Nullable String _package,
            @Nullable String description,
            @Nullable String architecture,
            @Nullable String database,
            @Nullable String auth,
            @Nullable String frontend) {
        return starter();
    }

    @Override
    public ResponseEntity<Preview> previewStarter(
            @Nullable String name,
            @Nullable String group,
            @Nullable String _package,
            @Nullable String description,
            @Nullable String architecture,
            @Nullable String database,
            @Nullable String auth,
            @Nullable String frontend) {
        ProjectRequest request = ProjectRequest.of(fields(http.getParameterMap()));
        PreviewProject.Preview preview = limit.run(() -> previewProject.preview(request));
        count("initializer.projects.previewed", request);
        List<PreviewFile> files = preview.files().stream()
                .map(f -> new PreviewFile(f.path(), f.size(), f.executable())
                        .content(f.text().orElse(null)))
                .toList();
        return ResponseEntity.ok(new Preview(preview.root(), files));
    }

    private ResponseEntity<Resource> starter() {
        ProjectRequest request = ProjectRequest.of(fields(http.getParameterMap()));
        ProjectArchive archive = limit.run(() -> generateProject.generate(request));
        count("initializer.projects.generated", request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(archive.fileName())
                                .build()
                                .toString())
                .body(new ByteArrayResource(archive.content()));
    }

    /** By option only: nothing about who asked. */
    private void count(String metric, ProjectRequest request) {
        meters.counter(
                        metric,
                        "architecture",
                        request.architecture().id(),
                        "database",
                        request.database().id(),
                        "auth",
                        request.auth().id(),
                        "frontend",
                        request.frontend().id())
                .increment();
    }

    /**
     * One value per field. A typo such as {@code nme=orders} must fail, not quietly give a project called demo, and
     * so must a field given twice.
     */
    static Map<String, String> fields(Map<String, String[]> parameters) {
        List<FieldError> errors = new ArrayList<>();
        Map<String, String> fields = new TreeMap<>();
        new TreeMap<>(parameters).forEach((field, values) -> {
            if (!FIELDS.contains(field)) {
                errors.add(new FieldError(field, "unknown field"));
            } else if (values.length > 1) {
                errors.add(new FieldError(field, "given more than once"));
            } else if (values.length == 1) {
                fields.put(field, values[0]);
            }
        });
        if (!errors.isEmpty()) {
            throw new InvalidProjectRequest(errors);
        }
        return fields;
    }
}
