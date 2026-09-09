package com.nekyia.bountyfulSeas.database;

import com.nekyia.bountyfulSeas.stats.CatchOutcome;
import com.nekyia.bountyfulSeas.stats.CatchStore;
import com.nekyia.bountyfulSeas.stats.FishStats;
import com.nekyia.bountyfulSeas.stats.PlayerTotal;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps catch totals in one MariaDB table.
 *
 * <p>Deliberately one table, {@code bs_player_fish}, holding a running total per
 * player and fish. Server-wide figures are aggregated from it on demand rather
 * than stored a second time, so there is no second copy to keep in step and no row
 * per catch to accumulate. A player fishing all year adds rows equal to the number
 * of distinct fish, not to the number of catches.
 *
 * <p>Every method blocks on the database and must be called off the server thread.
 */
public final class HikariCatchStore implements CatchStore, AutoCloseable {

    private static final String UPSERT = """
            INSERT INTO bs_player_fish
                (player, fish_id, catches, longest)
            VALUES (?, ?, 1, ?)
            ON DUPLICATE KEY UPDATE
                catches = catches + 1,
                longest = GREATEST(longest, ?)
            """;

    /**
     * What one fish's totals looked like before this catch, in one pass.
     *
     * <p>Both the player's own best and the server's come off the same row set -
     * the fish index already has those rows in hand, so asking for the two figures
     * separately would read them twice for nothing.
     */
    private static final String BESTS_BEFORE = """
            SELECT COALESCE(MAX(longest), 0)                              AS server_longest,
                   COALESCE(MAX(CASE WHEN player = ? THEN catches END), 0) AS own_catches,
                   COALESCE(MAX(CASE WHEN player = ? THEN longest END), 0) AS own_longest
            FROM bs_player_fish WHERE fish_id = ?
            """;

    private static final String BY_PLAYER_AND_FISH = """
            SELECT fish_id, catches, longest
            FROM bs_player_fish WHERE player = ? AND fish_id = ?
            """;

    private static final String BY_PLAYER = """
            SELECT fish_id, catches, longest
            FROM bs_player_fish WHERE player = ?
            """;

    private static final String SERVER_TOTALS = """
            SELECT fish_id,
                   SUM(catches) AS catches,
                   MAX(longest) AS longest
            FROM bs_player_fish GROUP BY fish_id
            """;

    private static final String SERVER_TOTAL_FOR_FISH = SERVER_TOTALS.replace(
            "FROM bs_player_fish GROUP BY fish_id",
            "FROM bs_player_fish WHERE fish_id = ? GROUP BY fish_id");

    private static final String TOP_BY_FISH = """
            SELECT player, catches, longest
            FROM bs_player_fish WHERE fish_id = ?
            ORDER BY catches DESC, longest DESC LIMIT ?
            """;

    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS bs_player_fish (
                player   CHAR(36)     NOT NULL,
                fish_id  VARCHAR(64)  NOT NULL,
                catches  BIGINT       NOT NULL DEFAULT 0,
                longest  DOUBLE       NOT NULL DEFAULT 0,
                PRIMARY KEY (player, fish_id),
                KEY idx_fish (fish_id)
            )
            """;

    private final HikariDataSource dataSource;

    /**
     * Opens the pool and makes sure the table is there.
     *
     * @throws SQLException when the database cannot be reached or prepared
     */
    public HikariCatchStore(DatabaseSettings settings) throws SQLException {
        HikariConfig config = new HikariConfig();

        // Named explicitly rather than left to DriverManager. The driver arrives
        // through Paper's library loader, in the plugin's own classloader, and
        // DriverManager only sees drivers registered with the system one - so
        // without this Hikari fails with "Failed to get driver instance".
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setPoolName("BountyfulSeasPool");

        // Small on purpose: this pool serves occasional catches and the odd guide
        // lookup, not a request load.
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5_000);
        config.setValidationTimeout(3_000);
        config.setIdleTimeout(300_000);
        config.setMaxLifetime(900_000);

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "64");

        this.dataSource = new HikariDataSource(config);
        prepareTables();
    }

    private void prepareTables() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(SCHEMA);
        }
    }

    @Override
    public CatchOutcome record(UUID player, String fishId, double length) {
        try (Connection connection = dataSource.getConnection()) {
            // Read before writing: afterwards the old bests are gone, overwritten
            // by this very catch, and there would be nothing left to compare with.
            long ownCatches = 0;
            double ownBest = 0;
            double serverBest = 0;

            try (PreparedStatement bests = connection.prepareStatement(BESTS_BEFORE)) {
                bests.setString(1, player.toString());
                bests.setString(2, player.toString());
                bests.setString(3, fishId);
                try (ResultSet rows = bests.executeQuery()) {
                    if (rows.next()) {
                        serverBest = rows.getDouble("server_longest");
                        ownCatches = rows.getLong("own_catches");
                        ownBest = rows.getDouble("own_longest");
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                statement.setString(1, player.toString());
                statement.setString(2, fishId);
                // The inserted length, then the same one again for the update branch.
                statement.setDouble(3, length);
                statement.setDouble(4, length);
                statement.executeUpdate();
            }

            // A first catch beats nothing, so a best of zero stays zero.
            return new CatchOutcome(ownCatches + 1,
                    ownBest > 0 && length > ownBest ? ownBest : 0,
                    serverBest > 0 && length > serverBest ? serverBest : 0);
        } catch (SQLException failure) {
            throw new IllegalStateException("could not record a catch of " + fishId, failure);
        }
    }

    @Override
    public FishStats forPlayer(UUID player, String fishId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(BY_PLAYER_AND_FISH)) {
            statement.setString(1, player.toString());
            statement.setString(2, fishId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? read(rows) : FishStats.none(fishId);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("could not read stats for " + fishId, failure);
        }
    }

    @Override
    public Map<String, FishStats> forPlayer(UUID player) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(BY_PLAYER)) {
            statement.setString(1, player.toString());
            return readAll(statement);
        } catch (SQLException failure) {
            throw new IllegalStateException("could not read stats for " + player, failure);
        }
    }

    @Override
    public FishStats forServer(String fishId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SERVER_TOTAL_FOR_FISH)) {
            statement.setString(1, fishId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? read(rows) : FishStats.none(fishId);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("could not read server stats for " + fishId, failure);
        }
    }

    @Override
    public Map<String, FishStats> forServer() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SERVER_TOTALS)) {
            return readAll(statement);
        } catch (SQLException failure) {
            throw new IllegalStateException("could not read server stats", failure);
        }
    }

    @Override
    public List<PlayerTotal> topBy(String fishId, int limit) {
        List<PlayerTotal> totals = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(TOP_BY_FISH)) {
            statement.setString(1, fishId);
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    totals.add(new PlayerTotal(
                            UUID.fromString(rows.getString("player")),
                            fishId,
                            rows.getLong("catches"),
                            rows.getDouble("longest")));
                }
            }
            return List.copyOf(totals);
        } catch (SQLException failure) {
            throw new IllegalStateException("could not read the ranking for " + fishId, failure);
        }
    }

    private static Map<String, FishStats> readAll(PreparedStatement statement) throws SQLException {
        Map<String, FishStats> stats = new LinkedHashMap<>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                FishStats read = read(rows);
                stats.put(read.fishId(), read);
            }
        }
        return Map.copyOf(stats);
    }

    private static FishStats read(ResultSet rows) throws SQLException {
        return new FishStats(
                rows.getString("fish_id"),
                rows.getLong("catches"),
                rows.getDouble("longest"));
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
