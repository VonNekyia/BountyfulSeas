package com.nekyia.bountyfulSeas.level;

import com.nekyia.bountyfulSeas.fishing.LengthCurve;
import com.nekyia.bountyfulSeas.stats.Leaderboard;
import com.nekyia.bountyfulSeas.stats.PlayerTotal;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The experience arithmetic, the cost list and the length curve, against hand-worked numbers. */
public final class LevelCheck {

    public static void main(String[] args) {
        ExperienceRule rule = new ExperienceRule(Map.of("common", 10L, "epic", 80L), 5, 0.5, 0.10, 5);

        check(rule.forNthCatch("common", 1) == 50, "a first catch pays five catches");
        check(rule.forNthCatch("common", 2) == 10, "a second catch pays one");
        check(rule.bigBonus("epic") == 40, "a big epic adds half of 80");
        check(rule.forCatch("nothing") == 0, "an unpriced tier pays nothing");
        // Five catches: 5 x 10, four more for the first, milestones I and II at 5 and 10.
        check(rule.forTotals("common", 5, 2) == 50 + 40 + 5 + 10, "totals of five commons");
        check(rule.forTotals("common", 0, 0) == 0, "nothing caught is worth nothing");

        LevelCurve curve = LevelCurve.of(List.of(100L, 250L));
        check(curve.maxLevel() == 3, "two costs make three levels");
        check(curve.at(99).level() == 1 && curve.at(99).remaining() == 1, "one short of level 2");
        check(curve.at(100).level() == 2 && curve.at(100).span() == 250, "level 2 spans 250");
        check(curve.at(350).level() == 3 && curve.at(350).capped(), "350 is the top");
        check(curve.at(10_000).level() == 3, "past the top stays at the top");

        LengthCurve sizes = new LengthCurve(1.5, 0.25, 10_000_000, 0.80);
        check(near(sizes.lengthBeatenBy(100, 0.5), 52.8), "median of a 100 cm fish");
        check(near(sizes.lengthBeatenBy(100, 0.001), 87.0), "1 in 1000 sits in the spread-out tail");
        check(near(sizes.lengthBeatenBy(100, 0.000001), 96.7), "1 in a million is still short of the max");
        check(sizes.lengthBeatenBy(100, 0.0000001) == 100, "the record odds reach the max");
        check(sizes.chanceOfAtLeast(100, 100) <= 0.00000011, "the max is one in ten million");
        check(sizes.inTail(100, 80) && !sizes.inTail(100, 79.99), "the tail starts at 80 cm");
        for (double length = 26; length <= 100; length += 0.5) {
            double back = sizes.lengthBeatenBy(100, sizes.chanceOfAtLeast(100, length));
            check(Math.abs(back - length) < 0.02, "the curve reads back at " + length + " as " + back);
        }

        UUID ana = new UUID(0, 1);
        UUID ben = new UUID(0, 2);
        List<PlayerTotal> rows = List.of(
                new PlayerTotal(ana, "cod", 10, 50, 0.01),
                new PlayerTotal(ana, "pike", 2, 80, 1),
                new PlayerTotal(ben, "cod", 30, 70, 0.000004),
                new PlayerTotal(ben, "boot", 5, 0, 0.2));
        List<Leaderboard.Standing> combined = Leaderboard.combinedLongest(rows, id -> !id.equals("boot"));
        check(combined.get(0).player().equals(ana) && combined.get(0).value() == 130, "ana leads on 50 + 80 cm");
        List<Leaderboard.Standing> caught = Leaderboard.mostCaught(rows, id -> true);
        check(caught.get(0).player().equals(ben) && caught.get(0).value() == 35, "ben leads on 35 catches");
        List<Leaderboard.Standing> rarest = Leaderboard.rarest(rows);
        check(rarest.get(0).player().equals(ben) && rarest.get(0).detail().equals("cod"), "ben has the rarest");
        check(rarest.size() == 2 && rarest.get(1).value() == 0.01, "unmeasured odds are left out");
        check(Leaderboard.placeOf(caught, ana) == 2 && Leaderboard.placeOf(caught, new UUID(0, 3)) == 0, "places");
        check(Leaderboard.odds(0.00000289).equals("1:346021 (0.000289%)"), Leaderboard.odds(0.00000289));

        System.out.println("levels, lengths and leaderboards: every check passed");
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < 0.1;
    }

    private static void check(boolean holds, String what) {
        if (!holds) {
            throw new AssertionError(what);
        }
    }

    private LevelCheck() {
    }
}
