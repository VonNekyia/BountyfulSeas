package com.nekyia.bountyfulSeas.nexo;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

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
            return null;
        }
        try {
            Object builder = itemFromId.invoke(null, id);
            if (builder == null) {
                return null;
            }
            if (build == null) {
                build = builder.getClass().getMethod("build");
            }
            return build.invoke(builder) instanceof ItemStack stack ? stack : null;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
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
