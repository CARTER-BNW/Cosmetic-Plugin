package com.jbac.cosmetics.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** pending_deliveries: coins owed to a player who was offline or had a full inventory. DB thread only. */
public final class DeliveryDao {

    private DeliveryDao() {}

    public static void add(Connection c, UUID player, int amount, String reason) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO pending_deliveries(player_uuid, amount, reason, created_at) VALUES (?,?,?,?)")) {
            ps.setString(1, player.toString());
            ps.setInt(2, amount);
            ps.setString(3, reason);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public static int pending(Connection c, UUID player) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(amount), 0) FROM pending_deliveries WHERE player_uuid = ?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public static int pendingAll(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(SUM(amount), 0) FROM pending_deliveries");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Deletes every queued row for the player and returns their total. Run inside a transaction. */
    public static int takeAll(Connection c, UUID player) throws SQLException {
        int total = pending(c, player);
        if (total > 0) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM pending_deliveries WHERE player_uuid = ?")) {
                ps.setString(1, player.toString());
                ps.executeUpdate();
            }
        }
        return total;
    }
}
