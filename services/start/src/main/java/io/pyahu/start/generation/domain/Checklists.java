package io.pyahu.start.generation.domain;

import java.util.List;

/** Where the guides' checklist comes from. */
public interface Checklists {

    /** Every item, in the order of the guides. */
    List<ChecklistItem> all();
}
