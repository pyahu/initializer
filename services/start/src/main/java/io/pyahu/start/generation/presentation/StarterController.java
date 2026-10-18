package io.pyahu.start.generation.presentation;

import io.micrometer.core.instrument.MeterRegistry;
import io.pyahu.start.generation.application.DescribeOptions;
import io.pyahu.start.generation.application.GenerateProject;
import io.pyahu.start.generation.domain.Architecture;
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
    private final DescribeOptions describeOptions;
    private final HttpServletRequest http;
    private final MeterRegistry meters;

    StarterController(
            GenerateProject generateProject,
            DescribeOptions describeOptions,
            HttpServletRequest http,
            MeterRegistry meters) {
        this.generateProject = generateProject;
        this.describeOptions = describeOptions;
        this.http = http;
        this.meters = meters;
    }

    @Override
    public ResponseEntity<Metadata> getMetadata() {
        DescribeOptions.Options options = describeOptions.describe();
        var baseline = options.baseline();
        List<Field> fields = options.fields().stream()
                .map(f -> new Field(f.id(), f.description())
                        .defaultValue(f.defaultValue())
                        .pattern(f.pattern().pattern())
                        .maxLength(f.maxLength()))
                .toList();
        List<Choice> architectures = options.architectures().stream()
                .map(a -> new Choice(a.id(), a.title(), a == Architecture.DEFAULT))
                .toList();
        return ResponseEntity.ok(new Metadata(
                new BaselineInfo(baseline.id(), baseline.java(), baseline.springBoot(), MAINTENANCE),
                fields,
                architectures));
    }

    @Override
    public ResponseEntity<Resource> getStarter(
            @Nullable String name,
            @Nullable String group,
            @Nullable String _package,
            @Nullable String description,
            @Nullable String architecture) {
        return starter(name, group, _package, description, architecture);
    }

    @Override
    public ResponseEntity<Resource> postStarter(
            @Nullable String name,
            @Nullable String group,
            @Nullable String _package,
            @Nullable String description,
            @Nullable String architecture) {
        return starter(name, group, _package, description, architecture);
    }

    private ResponseEntity<Resource> starter(
            @Nullable String name,
            @Nullable String group,
            @Nullable String packageName,
            @Nullable String description,
            @Nullable String architecture) {
        rejectUnknownOrRepeatedFields(http.getParameterMap());
        ProjectRequest request = ProjectRequest.of(name, group, packageName, description, architecture);
        ProjectArchive archive = generateProject.generate(request);
        meters.counter(
                        "initializer.projects.generated",
                        "architecture",
                        request.architecture().id())
                .increment();
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

    /** A typo such as {@code nme=orders} must fail, not quietly give a project called demo. */
    static void rejectUnknownOrRepeatedFields(Map<String, String[]> parameters) {
        List<FieldError> errors = new ArrayList<>();
        new TreeMap<>(parameters).forEach((field, values) -> {
            if (!FIELDS.contains(field)) {
                errors.add(new FieldError(field, "unknown field"));
            } else if (values.length > 1) {
                errors.add(new FieldError(field, "given more than once"));
            }
        });
        if (!errors.isEmpty()) {
            throw new InvalidProjectRequest(errors);
        }
    }
}
