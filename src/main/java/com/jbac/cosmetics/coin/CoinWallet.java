package com.jbac.cosmetics.coin;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.stream.IntStream;

/** Reads and edits the coins in a player's inventory. Main thread only; every call is O(36). */
public final class CoinWallet {

    public static final int OFFHAND_SLOT = 40;
    private static final int[] STORAGE = IntStream.range(0, 36).toArray();
    private static final int[] STORAGE_AND_OFFHAND = IntStream.concat(IntStream.range(0, 36), IntStream.of(OFFHAND_SLOT)).toArray();

    private final CoinItem coin;
    private final boolean countOffhand;

    public CoinWallet(CoinItem coin, boolean countOffhand) {
        this.coin = coin;
        this.countOffhand = countOffhand;
    }

    public CoinItem coin() {
        return coin;
    }

    public int count(Player p) {
        PlayerInventory inv = p.getInventory();
        int total = 0;
        for (int slot : slots()) {
            ItemStack s = inv.getItem(slot);
            if (coin.isCoin(s)) {
                total += s.getAmount();
            }
        }
        return total;
    }

    /** Removes exactly {@code n} coins, smallest stacks first. Returns n, or 0 if the player did not have enough. */
    public int remove(Player p, int n) {
        PlayerInventory inv = p.getInventory();
        int[] slots = slots();
        int[] amounts = new int[slots.length];
        for (int i = 0; i < slots.length; i++) {
            ItemStack s = inv.getItem(slots[i]);
            amounts[i] = coin.isCoin(s) ? s.getAmount() : 0;
        }
        int[] plan = CoinMath.plan(amounts, n);
        if (plan == null) {
            return 0;
        }
        for (int i = 0; i < slots.length; i++) {
            if (plan[i] <= 0) {
                continue;
            }
            ItemStack s = inv.getItem(slots[i]);
            int left = s.getAmount() - plan[i];
            if (left <= 0) {
                inv.setItem(slots[i], null);
            } else {
                s.setAmount(left);
                inv.setItem(slots[i], s);
            }
        }
        return CoinMath.sum(plan);
    }

    /** Adds {@code n} coins. Returns how many did not fit. */
    public int give(Player p, int n) {
        if (n <= 0) {
            return 0;
        }
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(coin.mint(n).toArray(ItemStack[]::new));
        int left = 0;
        for (ItemStack s : leftover.values()) {
            left += s.getAmount();
        }
        return left;
    }

    public int[] slots() {
        return countOffhand ? STORAGE_AND_OFFHAND : STORAGE;
    }
}
