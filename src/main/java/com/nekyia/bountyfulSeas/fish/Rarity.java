package com.nekyia.bountyfulSeas.fish;

/**
 * What tier a catch belongs to, and the first thing rolled when something bites.
 *
 * <p>A catch picks a tier before it picks an entry, so the odds of a legendary
 * stay the same whether a spot holds one legendary fish or twenty. Within the
 * chosen tier, {@code spawn_weight} decides between the entries.
 *
 * <p>Not all of these are fish. Junk and treasure are objects: they carry no
 * length, which is why they are tiers rather than modifiers - what comes up on the
 * line is one roll, and junk competes in it like everything else. Which tiers count
 * as objects is configured rather than fixed here; see {@link TierKinds}.
 *
 * <p>Nothing here says what an enchantment does to a tier either. That falls out of
 * the configured chances and the fish-or-object split, so a server that adds a tier
 * or moves a number does not also have to change any code.
 */
public enum Rarity implements ConfigValue {

    /** The everyday catch, and the first thing spent on better ones. */
    UNCOMMON,

    RARE,

    /** Junk. Usually an object rather than a fish, and lifted by nothing. */
    MISC,

    EPIC,

    LEGENDARY,

    /** Worth having, and usually an object: enchanted books and the like. */
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

    /** Whether this tier rolls its length at the top of the entry's range. */
    public boolean outsized() {
        return this == MYTHIC;
    }
}
