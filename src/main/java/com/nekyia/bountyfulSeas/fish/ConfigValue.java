package com.nekyia.bountyfulSeas.fish;

import java.util.Locale;

/**
 * Implemented by the enums that appear in a fish definition, so a value always
 * reads and reports the same way it is written in YAML: lower case.
 */
public interface ConfigValue {

    /** Provided by {@link Enum#name()}. */
    String name();

    default String configName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
