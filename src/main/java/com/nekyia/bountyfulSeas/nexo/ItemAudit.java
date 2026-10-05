package com.nekyia.bountyfulSeas.nexo;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Asks Nexo to build every item the plugin hands out, and reports what it refuses.
 *
 * <p>The one question a running server can answer and nothing else can. Whether a
 * fish is in the draw is arithmetic over the water map, checkable without a server
 * at all; whether the item it turns into can be built depends on what Nexo loaded
 * this boot, and the only way to find out is to ask Nexo on that boot.
 *
 * <p>It matters because the alternative is silence. A fish whose item cannot be
 * built falls back to vanilla's catch, which looks exactly like a plugin that is
 * working - the symptom is a server where everything bites and nothing is ours.
 */
public final class ItemAudit {

    /**
     * @param built   how many items Nexo handed over
     * @param refused the fish it would not build, by id, with what went wrong
     */
    public record Report(int built, Map<String, String> refused) {

        public boolean passed() {
            return refused.isEmpty();
        }

        public int checked() {
            return built + refused.size();
        }
    }

    private ItemAudit() {
    }

    /**
     * @param items the Nexo id of every item the plugin would hand out, by fish id
     */
    public static Report run(Map<String, String> items) {
        int built = 0;
        Map<String, String> refused = new java.util.LinkedHashMap<>();

        for (Map.Entry<String, String> entry : items.entrySet()) {
            String id = entry.getValue();
            if (id == null) {
                refused.put(entry.getKey(), "its item is not a nexo: reference");
                continue;
            }
            ItemStack stack = NexoItemFactory.create(id);
            if (stack == null) {
                refused.put(entry.getKey(), NexoItemFactory.available()
                        ? "Nexo does not know " + id
                        : "Nexo's item API is unreachable");
            } else {
                built++;
            }
        }
        return new Report(built, refused);
    }

    /** The report as lines, for a console or a chat window. */
    public static List<String> lines(Report report) {
        List<String> said = new ArrayList<>();
        said.add(report.built() + " of " + report.checked() + " items built");
        report.refused().forEach((fish, why) -> said.add("  " + fish + ": " + why));
        if (report.passed()) {
            said.add("every catch can be handed over as its own item");
        } else {
            said.add(report.refused().size() + " would be handed over as vanilla's fish instead");
        }
        return said;
    }
}
