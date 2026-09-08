package com.nekyia.bountyfulSeas.fish;

/**
 * What tier a catch belongs to, and the first thing rolled when something bites.
 *
 * <p>A catch picks a tier before it picks an entry, so the odds of a legendary
 * stay the same whether a spot holds one legendary fish or twenty. Within the
 * chosen tier, {@code spawn_weight} decides between the entries.
 *
 * <p>Two of these are not fish at all: {@link #MISC} is junk and {@link #TREASURE}
 * is worth having - enchanted books and the like. Both carry no length, which is
 * why they are tiers rather than modifiers: what comes up on the line is one roll,
 * and junk competes in it like everything else.
 *
 * <p>{@link #UNCOMMON} is the buffer. Everything an enchantment adds to a better
 * tier is taken out of it, so the odds always add up to what they started at.
 */
public enum Rarity implements ConfigValue {

    /** The everyday catch, and the pool better drops are paid for out of. */
    UNCOMMON,

    RARE,

    /** Junk. Not a fish, so it has no length, and no enchantment lifts it. */
    MISC,

    EPIC,

    LEGENDARY,

    /** Worth having. Not a fish, so it has no length. Lifted by Luck of the Sea. */
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

    /** Whether this tier holds actual fish. */
    public boolean isFish() {
        return this != MISC && this != TREASURE;
    }

    /**
     * Whether Luck of the Fish raises this tier.
     *
     * <p>Named outright rather than derived from {@link #isFish()}: the buffer is a
     * fish tier too and must not lift itself, and mythic is deliberately left out so
     * that no enchantment can make it ordinary.
     */
    public boolean liftedByFishLuck() {
        return this == RARE || this == EPIC || this == LEGENDARY;
    }

    /** Whether Luck of the Sea raises this tier. */
    public boolean liftedBySeaLuck() {
        return this == TREASURE;
    }

    /** Whether the chance added to better tiers is taken out of this one. */
    public boolean isBuffer() {
        return this == UNCOMMON;
    }

    /** Whether this tier rolls its length at the top of the entry's range. */
    public boolean outsized() {
        return this == MYTHIC;
    }
}
