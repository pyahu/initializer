package io.pyahu.start.generation.domain;

import java.util.List;
import java.util.Map;

/**
 * One item of the guides' checklist, with where it sits and when it applies.
 *
 * @param part the guides' part, such as {@code Foundations}, and its position from 1
 * @param step the step of the guide that explains the item
 * @param appliesWhen option paths to allowed values: keys combine with AND, values with OR, empty means always
 */
public record ChecklistItem(
        String id,
        int partNumber,
        String part,
        String guide,
        String guideTitle,
        String card,
        int step,
        String type,
        String text,
        Map<String, List<Object>> appliesWhen) {

    public ChecklistItem {
        appliesWhen = Map.copyOf(appliesWhen);
    }

    /** True when every key of the filter matches; an option the project does not have matches nothing. */
    public boolean appliesTo(Map<String, Object> options) {
        for (Map.Entry<String, List<Object>> condition : appliesWhen.entrySet()) {
            Object value = options.get(condition.getKey());
            List<Object> allowed = condition.getValue();
            boolean match = value instanceof List<?> list
                    ? list.stream().anyMatch(allowed::contains)
                    : value != null && allowed.contains(value);
            if (!match) {
                return false;
            }
        }
        return true;
    }
}
