package com.nekyia.bountyfulSeas.stats;

import java.util.UUID;

/**
 * One player's standing for one fish, for rankings.
 *
 * @param rarest the lowest odds of any single catch of it, 1 when none was measured
 */
public record PlayerTotal(UUID player, String fishId, long catches, double longest, double rarest) {
}
