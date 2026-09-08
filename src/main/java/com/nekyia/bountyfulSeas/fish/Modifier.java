package com.nekyia.bountyfulSeas.fish;

/**
 * A modifier on an entry.
 *
 * <p>Two kinds live here, and only the first takes part in matching a spot:
 * <ul>
 *   <li>{@link #CORAL} and {@link #ICE} describe the <b>water</b>, and are matched
 *       against what the map reports at the spot.</li>
 *   <li>{@link #SWARM} describes <b>behaviour</b>: where the fish can be found
 *       changes over time, which is asked separately.</li>
 * </ul>
 */
public enum Modifier implements ConfigValue {

    CORAL,
    ICE,

    /**
     * The fish moves. It gathers in a few water regions at a time and shifts to
     * others on a timer, so it can only be caught where its swarm currently is.
     */
    SWARM;

    /** Whether this says something about the water rather than about the fish. */
    public boolean describesWater() {
        return this == CORAL || this == ICE;
    }
}
