package com.nekyia.bountyfulSeas.nexo;

import java.util.List;
import java.util.OptionalDouble;

/**
 * One item this plugin wants Nexo to have.
 *
 * <p>This is the whole boundary between the config side and the Nexo side. The
 * writer knows nothing about fish, categories or where any of it was read from -
 * it is handed items already described in Nexo's terms and writes them out.
 * Anything fish shaped is turned into this on the way in.
 */
public interface NexoItem {

    /** The Nexo item id, without a namespace: {@code herring}, not {@code nexo:herring}. */
    String id();

    /** The item name, MiniMessage as everywhere else in the stack. */
    String displayName();

    /** Lore lines exactly as they should appear, already formatted. */
    List<String> lore();

    /**
     * Saturation this item restores when eaten, or empty when it is not edible.
     *
     * <p>Empty is the only thing that makes an item inedible, so it has to stay
     * distinct from a present value of zero.
     */
    OptionalDouble foodSaturation();
}
