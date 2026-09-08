package com.nekyia.bountyfulSeas.nexo;

import java.nio.file.Path;
import java.util.List;

/**
 * What {@link NexoBlueprintWriter} did, so the caller can say so on the console
 * without deciding again whether anything happened.
 *
 * @param target  the blueprint file, or null when nothing was written
 * @param created the item ids added by this run, empty when none were
 * @param note    why nothing was written, or null when the run was normal
 */
public record BlueprintResult(Path target, List<String> created, List<String> repaired, String note) {

    public BlueprintResult {
        created = List.copyOf(created);
        repaired = List.copyOf(repaired);
    }

    static BlueprintResult written(Path target, List<String> created, List<String> repaired) {
        return new BlueprintResult(target, created, repaired, null);
    }

    static BlueprintResult unchanged(Path target) {
        return new BlueprintResult(target, List.of(), List.of(), null);
    }

    static BlueprintResult skipped(String note) {
        return new BlueprintResult(null, List.of(), List.of(), note);
    }

    /** True when the run could not do its job, as opposed to having nothing to do. */
    public boolean skipped() {
        return note != null;
    }
}
