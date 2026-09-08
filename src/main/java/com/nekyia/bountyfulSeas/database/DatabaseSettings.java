package com.nekyia.bountyfulSeas.database;

/**
 * Everything needed to reach the database.
 *
 * <p>A plain record so the database module never reads a config file: whoever has
 * one fills this in and hands it over.
 */
public record DatabaseSettings(
        String host,
        int port,
        String database,
        String username,
        String password
) {

    public String jdbcUrl() {
        return "jdbc:mariadb://" + host + ":" + port + "/" + database;
    }
}
