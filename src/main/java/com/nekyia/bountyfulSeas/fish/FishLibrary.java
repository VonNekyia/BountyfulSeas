package com.nekyia.bountyfulSeas.fish;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The fish that loaded cleanly, keyed by their id. */
public final class FishLibrary {

    private final Map<String, Fish> byId;

    FishLibrary(Map<String, Fish> byId) {
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    public static FishLibrary empty() {
        return new FishLibrary(Map.of());
    }

    public Fish get(String id) {
        return byId.get(id);
    }

    public boolean contains(String id) {
        return byId.containsKey(id);
    }

    public Collection<Fish> all() {
        return byId.values();
    }

    public int size() {
        return byId.size();
    }
}
