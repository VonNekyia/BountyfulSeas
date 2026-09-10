package com.nekyia.bountyfulSeas.stats;

/**
 * The catch counts that earn a fish its next numeral.
 *
 * <p>Ten steps, shown as Roman numerals I to X, so a fish's standing reads at a
 * glance without printing the raw count.
 */
public enum Milestone {

    I(1),
    II(5),
    III(10),
    IV(20),
    V(50),
    VI(100),
    VII(200),
    VIII(500),
    IX(750),
    X(1000);

    private final int required;

    Milestone(int required) {
        this.required = required;
    }

    /** Catches needed to reach this step. */
    public int required() {
        return required;
    }

    /** Which step this is, counting from 1, for anything that pays by the step. */
    public int step() {
        return ordinal() + 1;
    }

    /** The Roman numeral, which is simply the name. */
    public String numeral() {
        return name();
    }

    /** The highest step reached with this many catches, or null for none yet. */
    public static Milestone reached(long catches) {
        Milestone reached = null;
        for (Milestone milestone : values()) {
            if (catches >= milestone.required) {
                reached = milestone;
            }
        }
        return reached;
    }

    /**
     * The step this exact count earns, or null when it earns none.
     *
     * <p>Catches arrive one at a time, so landing exactly on a threshold is the
     * moment the step is crossed - which is what a announcement should react to
     * rather than to merely being past it.
     */
    public static Milestone reachedExactly(long catches) {
        for (Milestone milestone : values()) {
            if (catches == milestone.required) {
                return milestone;
            }
        }
        return null;
    }

    /** The step being worked towards, or null once every step is done. */
    public static Milestone next(long catches) {
        for (Milestone milestone : values()) {
            if (catches < milestone.required) {
                return milestone;
            }
        }
        return null;
    }
}
