package io.pyahu.start.generation.infrastructure;

import io.pyahu.start.generation.domain.Contribution.Property;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes properties as nested YAML, keeping the order in which each key first appeared, with a blank line between
 * top-level sections.
 */
final class ApplicationYaml {

    private ApplicationYaml() {}

    static String write(List<Property> properties) {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Property p : properties) {
            List<String> path = segments(p.name());
            Map<String, Object> node = root;
            for (String segment : path.subList(0, path.size() - 1)) {
                node = section(node, segment, p.name());
            }
            node.put(path.getLast(), p.value());
        }
        StringBuilder out = new StringBuilder();
        write(root, 0, out);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> node, String segment, String name) {
        Object child = node.computeIfAbsent(segment, k -> new LinkedHashMap<String, Object>());
        if (!(child instanceof Map<?, ?>)) {
            throw new IllegalStateException(name + ": " + segment + " already holds a value");
        }
        return (Map<String, Object>) child;
    }

    private static void write(Map<String, Object> node, int depth, StringBuilder out) {
        String indent = "  ".repeat(depth);
        for (Map.Entry<String, Object> e : node.entrySet()) {
            if (depth == 0 && !out.isEmpty()) {
                out.append('\n');
            }
            out.append(indent).append(e.getKey()).append(':');
            if (e.getValue() instanceof Map<?, ?> child) {
                out.append('\n');
                @SuppressWarnings("unchecked")
                Map<String, Object> section = (Map<String, Object>) child;
                write(section, depth + 1, out);
            } else {
                out.append(' ').append(e.getValue()).append('\n');
            }
        }
    }

    /** Splits on dots, except inside brackets: {@code a.[b.c]} is {@code a} then {@code b.c}. */
    static List<String> segments(String name) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean bracket = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '[' && current.isEmpty()) {
                bracket = true;
            } else if (c == ']' && bracket) {
                bracket = false;
            } else if (c == '.' && !bracket) {
                segments.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        segments.add(current.toString());
        if (bracket || segments.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("not a property name: " + name);
        }
        return segments;
    }
}
