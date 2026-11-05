package com.nekyia.bountyfulSeas.nexo;

import java.util.List;

/**
 * What {@link NexoBlueprintWriter} did, so the caller can say so on the console
 * without deciding again whether anything happened.
 *
 * @param created  the item ids added by this run, empty when none were
 * @param repaired {@code id -> number} for every entry whose model data had to move
 * @param moved    {@code id -> file} for every entry that changed file
 * @param note     why nothing was written, or null when the run was normal
 */
public record BlueprintResult(List<String> created, List<String> repaired, List<String> moved, String note) {

    public BlueprintResult {
        created = List.copyOf(created);
        repaired = List.copyOf(repaired);
        moved = List.copyOf(moved);
    }

    static BlueprintResult done(List<String> created, List<String> repaired, List<String> moved) {
        return new BlueprintResult(created, repaired, moved, null);
    }

    static BlueprintResult skipped(String note) {
        return new BlueprintResult(List.of(), List.of(), List.of(), note);
    }

    /** True when the run could not do its job, as opposed to having nothing to do. */
    public boolean skipped() {
        return note != null;
    }
}
