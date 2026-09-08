package com.nekyia.bountyfulSeas.stats;

import java.util.UUID;

/** One player's standing for one fish, for rankings. */
public record PlayerTotal(UUID player, String fishId, long catches, double longest) {
}
