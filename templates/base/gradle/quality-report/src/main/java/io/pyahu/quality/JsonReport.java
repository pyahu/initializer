package io.pyahu.quality;

import java.time.Instant;
import java.util.Map;

/**
 * quality.json, for tools and AI agents. The schema is stable: fields are added, never renamed. Each failed gate
 * names the command that reproduces it and the guide step that explains the fix.
 */
final class JsonReport {

    static final String SCHEMA = "https://initializer.pyahu.io/schemas/quality-report/v1";

    static String render(Report r) {
        StringBuilder j = new StringBuilder();
        j.append("{\n");
        field(j, 1, "schema", SCHEMA).append(",\n");
        field(j, 1, "project", r.project()).append(",\n");
        field(j, 1, "generatedAt", r.generatedAt().toString()).append(",\n");
        field(j, 1, "status", r.status()).append(",\n");
        field(j, 1, "summary", r.headline()).append(",\n");
        field(j, 1, "where", r.where()).append(",\n");
        field(j, 1, "runUrl", r.runUrl()).append(",\n");
        indent(j, 1).append("\"firstFailure\": ");
        if (r.failedFirst() == null) {
            j.append("null");
        } else {
            Report.Gate g = r.failedFirst();
            j.append("{ ");
            j.append("\"gate\": ").append(quote(g.id())).append(", ");
            j.append("\"reason\": ").append(quote(Report.reason(g))).append(", ");
            j.append("\"command\": ").append(quote(g.definition().command())).append(", ");
            j.append("\"guide\": ").append(quote(g.definition().guideUrl())).append(" }");
        }
        j.append(",\n");
        indent(j, 1).append("\"environment\": {");
        int i = 0;
        for (Map.Entry<String, String> e : r.environment().entrySet()) {
            j.append(i++ == 0 ? "\n" : ",\n");
            field(j, 2, e.getKey(), e.getValue());
        }
        j.append("\n");
        indent(j, 1).append("},\n");
        indent(j, 1).append("\"gates\": [");
        for (int g = 0; g < r.gates().size(); g++) {
            j.append(g == 0 ? "\n" : ",\n");
            gate(j, r.gates().get(g));
        }
        j.append("\n");
        indent(j, 1).append("]\n}\n");
        return j.toString();
    }

    private static void gate(StringBuilder j, Report.Gate g) {
        Gates.Definition d = g.definition();
        indent(j, 2).append("{\n");
        field(j, 3, "id", d.id()).append(",\n");
        field(j, 3, "title", d.title()).append(",\n");
        field(j, 3, "status", g.status()).append(",\n");
        indent(j, 3).append("\"fromThisRun\": ").append(g.fromThisRun()).append(",\n");
        field(j, 3, "ranAt", g.ranAt() == 0 ? null : Instant.ofEpochMilli(g.ranAt()).toString()).append(",\n");
        field(j, 3, "summary", g.headline()).append(",\n");
        field(j, 3, "checks", d.purpose()).append(",\n");
        field(j, 3, "command", d.command()).append(",\n");
        field(j, 3, "fix", d.fix()).append(",\n");
        field(j, 3, "guide", d.guide()).append(",\n");
        field(j, 3, "guideUrl", d.guideUrl()).append(",\n");
        field(j, 3, "checklist", d.checklist()).append(",\n");
        field(j, 3, "toolReport", d.toolReport() == null ? null : "build/reports/" + d.toolReport().substring(3)).append(",\n");
        field(j, 3, "failure", g.failure()).append(",\n");
        indent(j, 3).append("\"metrics\": [");
        for (int m = 0; m < g.metrics().size(); m++) {
            Report.Metric metric = g.metrics().get(m);
            j.append(m == 0 ? "\n" : ",\n");
            indent(j, 4).append("{ \"name\": ").append(quote(metric.label()))
                    .append(", \"value\": ").append(round(metric.value()))
                    .append(", \"minimum\": ").append(metric.minimum() == null ? "null" : round(metric.minimum()))
                    .append(", \"detail\": ").append(quote(metric.text())).append(" }");
        }
        j.append(g.metrics().isEmpty() ? "],\n" : "\n" + "      ],\n");
        indent(j, 3).append("\"findingsTotal\": ").append(g.findingsTotal()).append(",\n");
        indent(j, 3).append("\"findings\": [");
        for (int f = 0; f < g.findings().size(); f++) {
            Report.Finding finding = g.findings().get(f);
            j.append(f == 0 ? "\n" : ",\n");
            indent(j, 4).append("{ \"rule\": ").append(quote(finding.rule()))
                    .append(", \"location\": ").append(quote(finding.location()))
                    .append(", \"message\": ").append(quote(finding.message()))
                    .append(", \"url\": ").append(quote(finding.url())).append(" }");
        }
        j.append(g.findings().isEmpty() ? "]\n" : "\n" + "      ]\n");
        indent(j, 2).append("}");
    }

    private static String round(double v) {
        return String.valueOf(Math.round(v * 10000) / 10000.0);
    }

    private static StringBuilder field(StringBuilder j, int level, String name, String value) {
        return indent(j, level).append(quote(name)).append(": ").append(quote(value));
    }

    private static StringBuilder indent(StringBuilder j, int level) {
        return j.append("  ".repeat(level));
    }

    static String quote(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder q = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> q.append("\\\"");
                case '\\' -> q.append("\\\\");
                case '\n' -> q.append("\\n");
                case '\r' -> q.append("\\r");
                case '\t' -> q.append("\\t");
                default -> {
                    if (c < 0x20) {
                        q.append(String.format("\\u%04x", (int) c));
                    } else {
                        q.append(c);
                    }
                }
            }
        }
        return q.append('"').toString();
    }

    private JsonReport() {}
}
