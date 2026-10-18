package io.pyahu.start.generation.infrastructure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Sorts the imports of a rendered Java file the way palantir-java-format does: static imports, a blank line, the
 * others, each in lexicographic order. The order depends on the package the user picked, so templates cannot fix it,
 * and the generated project's {@code spotlessCheck} would fail otherwise.
 */
final class JavaImports {

    private JavaImports() {}

    static String sort(String source) {
        List<String> lines = source.lines().toList();
        int first = -1;
        int last = -1;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.startsWith("import ")) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            } else if (first >= 0 && !l.isBlank()) {
                break;
            }
        }
        if (first < 0) {
            return source;
        }
        // By the imported name, so a.B.C sorts after a.B as palantir-java-format does.
        Comparator<String> byName =
                Comparator.comparing(l -> l.substring(0, l.length() - 1).replaceFirst("^import (static )?", ""));
        TreeSet<String> statics = new TreeSet<>(byName);
        TreeSet<String> others = new TreeSet<>(byName);
        for (String l : lines.subList(first, last + 1)) {
            if (l.startsWith("import static ")) {
                statics.add(l.strip());
            } else if (l.startsWith("import ")) {
                others.add(l.strip());
            }
        }
        List<String> out = new ArrayList<>(lines.subList(0, first));
        out.addAll(statics);
        if (!statics.isEmpty() && !others.isEmpty()) {
            out.add("");
        }
        out.addAll(others);
        out.addAll(lines.subList(last + 1, lines.size()));
        return String.join("\n", out) + (source.endsWith("\n") ? "\n" : "");
    }
}
