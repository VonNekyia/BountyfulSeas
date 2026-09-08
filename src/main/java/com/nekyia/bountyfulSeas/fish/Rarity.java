package com.nekyia.bountyfulSeas.fish;

/**
 * What tier a catch belongs to, and the first thing rolled when something bites.
 *
 * <p>A catch picks a tier before it picks an entry, so the odds of a legendary
 * stay the same whether a spot holds one legendary fish or twenty. Within the
 * chosen tier, {@code spawn_weight} decides between the entries.
 *
 * <p>Two of these are not fish at all: {@link #MISC} is junk and {@link #TREASURE}
 * is something worth having. Both carry no length, which is why they are tiers
 * rather than modifiers - what comes up on the line is one roll, and junk competes
 * in it like everything else.
 */
public enum Rarity implements ConfigValue {

    COMMON,
    RARE,

    /** Rubbish. Not a fish, so it has no length. */
    TRASH,

    EPIC,

    /** Odds and ends. Not a fish, so it has no length. */
    MISC,

    LEGENDARY,

    /** Worth having. Not a fish, so it has no length. */
    TREASURE,

    /**
     * Vanishingly rare, and outsized when it does appear.
     *
     * <p>A mythic rolls its length near the top of what the entry allows rather
     * than anywhere within it, so catching one is remarkable twice over.
     */
    MYTHIC,

    /** Reserved for hand-placed fish, such as event rewards. */
    SIGNATURE;

    /** Whether entries in this tier are objects rather than fish, and so have no length. */
    public boolean suppressesSize() {
        return !isFish();
    }

    /**
     * Whether this tier holds actual fish.
     *
     * <p>Lure is a fishing enchantment, so it lifts the rarer fish tiers and leaves
     * the junk and the treasure alone - those are what Luck of the Sea is for.
     */
    public boolean isFish() {
        return this != TRASH && this != MISC && this != TREASURE;
    }

    /** Whether this tier rolls its length at the top of the entry's range. */
    public boolean outsized() {
        return this == MYTHIC;
    }
}
