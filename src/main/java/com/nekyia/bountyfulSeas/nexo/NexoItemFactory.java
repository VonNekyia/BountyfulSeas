package com.nekyia.bountyfulSeas.nexo;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Builds the actual item stack for a Nexo item id.
 *
 * <p>Reached by reflection on purpose. This plugin writes Nexo's item YAML without
 * compiling against Nexo, and keeping it that way means the build needs no Nexo
 * artifact and the plugin still starts on a server that does not run Nexo. The
 * cost is one lookup, done once and cached.
 *
 * <p>The call is {@code NexoItems.itemFromId(id).build()}. When Nexo is absent, or
 * the id is unknown, the answer is simply null and the caller falls back.
 */
public final class NexoItemFactory {

    private static final String NEXO_ITEMS = "com.nexomc.nexo.api.NexoItems";

    private static boolean resolved;
    private static boolean complained;
    private static Method itemFromId;
    private static Method build;

    private NexoItemFactory() {
    }

    /** Whether Nexo is present and its item API could be reached. */
    public static synchronized boolean available() {
        resolve();
        return itemFromId != null;
    }

    /**
     * The item for this Nexo id, or null when Nexo is absent or does not know it.
     */
    public static synchronized ItemStack create(String id) {
        resolve();
        if (itemFromId == null) {
            return complain("Nexo's item API could not be reached, so every catch"
                    + " falls back to vanilla's fish");
        }
        try {
            Object builder = itemFromId.invoke(null, id);
            if (builder == null) {
                return complain("Nexo does not know the item " + id + ", so that catch"
                        + " falls back to vanilla's fish");
            }
            if (build == null) {
                build = builder.getClass().getMethod("build");
            }
            if (build.invoke(builder) instanceof ItemStack stack) {
                return stack;
            }
            return complain("Nexo built something other than an item for " + id);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return complain("Nexo refused to build " + id + ": " + failure);
        }
    }

    /**
     * Says so once, then goes quiet.
     *
     * <p>Falling back to vanilla's fish is the right thing to do and the wrong thing
     * to do silently: the plugin then looks like it is working while handing over
     * nothing of its own, and the only way to find out is to read the source. Once,
     * because it would otherwise be one line per cast.
     */
    private static ItemStack complain(String reason) {
        if (!complained) {
            complained = true;
            Logger.getLogger("BountyfulSeas").warning(reason);
        }
        return null;
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            itemFromId = Class.forName(NEXO_ITEMS).getMethod("itemFromId", String.class);
        } catch (ReflectiveOperationException | RuntimeException absent) {
            itemFromId = null;
        }
    }
}
