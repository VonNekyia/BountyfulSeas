package com.nekyia.bountyfulSeas.fishing;

import com.nekyia.bountyfulSeas.fish.Fish;

/**
 * How likely one fish is at a given spot.
 *
 * @param weight  the fish's spawn weight, the raw number behind the percentage
 * @param percent share of the catches at this spot, 0 to 100
 */
public record Chance(Fish fish, int weight, double percent) {
}
