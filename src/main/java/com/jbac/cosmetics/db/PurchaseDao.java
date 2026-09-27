package com.jbac.cosmetics.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** purchases: one row per purchase attempt, keyed by our own txn id. DB thread only. */
public final class PurchaseDao {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_COMPLETE = "complete";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_REVOKED = "revoked";

    private PurchaseDao() {}

    public static void insert(Connection c, String txn, UUID player, String itemId, int price, String status)
            throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO purchases(txn_id, player_uuid, item_id, price, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1, txn);
            ps.setString(2, player.toString());
            ps.setString(3, itemId);
            ps.setInt(4, price);
            ps.setString(5, status);
            ps.setLong(6, now);
            ps.setLong(7, now);
            ps.executeUpdate();
        }
    }

    public static void setStatus(Connection c, String txn, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE purchases SET status = ?, updated_at = ? WHERE txn_id = ?")) {
            ps.setString(1, status);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, txn);
            ps.executeUpdate();
        }
    }

    /** Item ids the player has a completed purchase for. */
    public static Set<String> completedItems(Connection c, UUID player) throws SQLException {
        Set<String> out = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT DISTINCT item_id FROM purchases WHERE player_uuid = ? AND status = ?")) {
            ps.setString(1, player.toString());
            ps.setString(2, STATUS_COMPLETE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    /** Marks every completed purchase of the item as revoked. Returns how many rows changed. */
    public static int revoke(Connection c, UUID player, String itemId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE purchases SET status = ?, updated_at = ? WHERE player_uuid = ? AND item_id = ? AND status = ?")) {
            ps.setString(1, STATUS_REVOKED);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, player.toString());
            ps.setString(4, itemId);
            ps.setString(5, STATUS_COMPLETE);
            return ps.executeUpdate();
        }
    }
}
