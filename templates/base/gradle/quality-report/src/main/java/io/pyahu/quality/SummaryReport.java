package io.pyahu.quality;

/**
 * job-summary.md: the short version CI writes to the GitHub job summary, the only format GitHub renders there. The
 * full report is quality.html in the reports artifact.
 */
final class SummaryReport {

    static String render(Report r) {
        StringBuilder md = new StringBuilder();
        String icon = switch (r.status()) {
            case Report.PASSED -> "✅";
            case Report.FAILED -> "❌";
            default -> "⏸️";
        };
        md.append("### ").append(icon).append(" Quality: ").append(r.headline()).append("\n\n");
        if (r.failedFirst() != null) {
            Report.Gate g = r.failedFirst();
            md.append("**").append(g.definition().title()).append("** failed: ")
                    .append(cell(firstLine(Report.reason(g)))).append("  \n");
            md.append("Run `").append(g.definition().command()).append("` to reproduce it. ")
                    .append("Why it matters and how to fix it: [")
                    .append(g.definition().guide()).append("](").append(g.definition().guideUrl()).append(").\n\n");
        }
        md.append("| Gate | Result | |\n| --- | --- | --- |\n");
        for (Report.Gate g : r.gates()) {
            String mark = switch (g.status()) {
                case Report.PASSED -> "✅";
                case Report.FAILED -> "❌";
                default -> "⏸️";
            };
            md.append("| ").append(mark).append(' ').append(g.definition().title())
                    .append(" | ").append(cell(g.headline()))
                    .append(" | [").append(g.definition().guide()).append("](").append(g.definition().guideUrl()).append(") |\n");
        }
        md.append("\nThe full report is `quality.html` in the `reports` artifact of this run.\n");
        return md.toString();
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.strip().lines().findFirst().orElse("");
    }

    private static String cell(String text) {
        return text == null ? "" : text.replace("|", "\\|").replace("\n", " ");
    }

    private SummaryReport() {}
}
