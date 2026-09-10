package com.nekyia.bountyfulSeas.stats;

import java.util.UUID;

/**
 * The longest of one fish anybody has landed, and who landed it.
 *
 * @param fishId the fish the record belongs to
 * @param holder whoever holds it
 * @param length  how long it was, in cm
 * @param anglers how many people have landed this fish at all, so a place has
 *                something to be out of
 */
public record FishRecord(String fishId, UUID holder, double length, int anglers) {
}
