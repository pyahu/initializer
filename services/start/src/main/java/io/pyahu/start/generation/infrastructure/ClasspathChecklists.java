package io.pyahu.start.generation.infrastructure;

import io.pyahu.start.generation.domain.ChecklistItem;
import io.pyahu.start.generation.domain.Checklists;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads templates/checklist.json, which guidekit generates from data/checklist.yaml and the guides. */
@Component
class ClasspathChecklists implements Checklists {

    private final List<ChecklistItem> items;

    ClasspathChecklists(ClasspathTemplates templates) {
        this.items = parse(JsonMapper.builder().build().readTree(templates.read("checklist.json")));
    }

    @Override
    public List<ChecklistItem> all() {
        return items;
    }

    static List<ChecklistItem> parse(JsonNode root) {
        List<ChecklistItem> items = new ArrayList<>();
        int partNumber = 0;
        for (JsonNode part : root.required("parts")) {
            partNumber++;
            for (JsonNode guide : part.required("guides")) {
                for (JsonNode item : guide.required("items")) {
                    Map<String, List<Object>> filter = new LinkedHashMap<>();
                    for (var condition : item.required("appliesWhen").properties()) {
                        List<Object> allowed = new ArrayList<>();
                        for (JsonNode v : condition.getValue()) {
                            allowed.add(v.isBoolean() ? (Object) v.booleanValue() : v.asString());
                        }
                        filter.put(condition.getKey(), allowed);
                    }
                    items.add(new ChecklistItem(
                            item.required("id").asString(),
                            partNumber,
                            part.required("title").asString(),
                            guide.required("id").asString(),
                            guide.required("title").asString(),
                            item.required("card").asString(),
                            item.required("step").asInt(),
                            item.required("type").asString(),
                            item.required("text").asString(),
                            filter));
                }
            }
        }
        return List.copyOf(items);
    }
}
