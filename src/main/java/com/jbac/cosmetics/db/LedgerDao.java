package com.jbac.cosmetics.db;

import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** coin_ledger: every credit and debit of SkyCoins. Static SQL helpers; always called on the DB thread. */
public final class LedgerDao {

    public static final String KIND_GIVE = "give";
    public static final String KIND_TAKE = "take";
    public static final String KIND_PURCHASE = "purchase";
    public static final String KIND_REFUND = "refund";
    public static final String KIND_CONVERT = "convert";

    private LedgerDao() {}

    /**
     * Inserts a row. When {@code extTxn} is given and already present, inserts nothing and returns false:
     * that is how store retries become no-ops.
     */
    public static boolean insert(Connection c, @Nullable UUID player, int amount, String kind, String source,
                                 @Nullable String extTxn, @Nullable String ref) throws SQLException {
        if (extTxn != null) {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM coin_ledger WHERE ext_txn = ?")) {
                ps.setString(1, extTxn);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return false;
                    }
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO coin_ledger(player_uuid, amount, kind, source, ext_txn, ref, created_at) VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1, player == null ? null : player.toString());
            ps.setInt(2, amount);
            ps.setString(3, kind);
            ps.setString(4, source);
            ps.setString(5, extTxn);
            ps.setString(6, ref);
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        }
        return true;
    }

    /** kind -> signed total. Minted minus spent should match what is in circulation. */
    public static Map<String, Long> totalsByKind(Connection c) throws SQLException {
        Map<String, Long> out = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT kind, SUM(amount) FROM coin_ledger GROUP BY kind ORDER BY kind");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getLong(2));
            }
        }
        return out;
    }
}
