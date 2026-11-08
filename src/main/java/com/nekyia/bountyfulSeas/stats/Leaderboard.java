package com.nekyia.bountyfulSeas.stats;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Every leaderboard, ranked in memory from the raw totals.
 *
 * <p>The whole table is a row per player and fish - a few thousand rows on a busy
 * server - so it is fetched once, cached, and every board is a pass over it here
 * rather than a query of its own.
 */
public final class Leaderboard {

    /**
     * One place on a board.
     *
     * @param detail the fish behind the value where there is one, otherwise null
     */
    public record Standing(UUID player, double value, String detail) {
    }

    private static final Comparator<Standing> HIGHEST_FIRST = Comparator
            .comparingDouble(Standing::value).reversed()
            .thenComparing(standing -> standing.player().toString());

    private Leaderboard() {
    }

    /** Each player's best length of every fish that passes the filter, added up. */
    public static List<Standing> combinedLongest(Collection<PlayerTotal> rows, Predicate<String> fish) {
        return summed(rows, fish, PlayerTotal::longest);
    }

    /** Each player's catches of every fish that passes the filter, added up. */
    public static List<Standing> mostCaught(Collection<PlayerTotal> rows, Predicate<String> fish) {
        return summed(rows, fish, row -> row.catches());
    }

    /** The longest each player has landed of one fish. */
    public static List<Standing> longestOf(Collection<PlayerTotal> rows, String fishId) {
        List<Standing> board = new ArrayList<>();
        for (PlayerTotal row : rows) {
            if (row.fishId().equals(fishId) && row.longest() > 0) {
                board.add(new Standing(row.player(), row.longest(), fishId));
            }
        }
        board.sort(HIGHEST_FIRST);
        return board;
    }

    /** Each player's least likely single catch, least likely first. */
    public static List<Standing> rarest(Collection<PlayerTotal> rows) {
        Map<UUID, Standing> best = new HashMap<>();
        for (PlayerTotal row : rows) {
            if (row.rarest() <= 0 || row.rarest() >= 1) {
                continue;   // never measured
            }
            best.merge(row.player(), new Standing(row.player(), row.rarest(), row.fishId()),
                    (held, offered) -> offered.value() < held.value() ? offered : held);
        }
        List<Standing> board = new ArrayList<>(best.values());
        board.sort(Comparator.comparingDouble(Standing::value)
                .thenComparing(standing -> standing.player().toString()));
        return board;
    }

    /** Where a player stands, counting from 1; 0 when they are not on the board. */
    public static int placeOf(List<Standing> board, UUID player) {
        for (int index = 0; index < board.size(); index++) {
            if (board.get(index).player().equals(player)) {
                return index + 1;
            }
        }
        return 0;
    }

    /** A chance as people say it and as a percentage: 1:345593 (0.000289%). */
    public static String odds(double chance) {
        String percent = new BigDecimal(chance * 100).round(new MathContext(3))
                .stripTrailingZeros().toPlainString();
        return "1:" + Math.round(1 / chance) + " (" + percent + "%)";
    }

    private static List<Standing> summed(Collection<PlayerTotal> rows, Predicate<String> fish,
                                         java.util.function.ToDoubleFunction<PlayerTotal> value) {
        Map<UUID, Double> sums = new HashMap<>();
        for (PlayerTotal row : rows) {
            if (fish.test(row.fishId())) {
                sums.merge(row.player(), value.applyAsDouble(row), Double::sum);
            }
        }
        List<Standing> board = new ArrayList<>();
        sums.forEach((player, sum) -> {
            if (sum > 0) {
                board.add(new Standing(player, sum, null));
            }
        });
        board.sort(HIGHEST_FIRST);
        return board;
    }
}
