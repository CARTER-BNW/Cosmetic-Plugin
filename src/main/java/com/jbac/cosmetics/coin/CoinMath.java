package com.jbac.cosmetics.coin;

import java.util.Arrays;
import java.util.Comparator;

/** Pure arithmetic over per-slot coin counts. No Bukkit types, fully unit-tested. */
public final class CoinMath {

    private CoinMath() {}

    public static int count(int[] amounts) {
        int total = 0;
        for (int a : amounts) {
            total += Math.max(0, a);
        }
        return total;
    }

    /**
     * Per-slot deductions that remove exactly {@code n} coins, emptying the smallest stacks first so the
     * inventory ends up with fewer partial stacks. Returns null when there are not enough coins.
     */
    public static int[] plan(int[] amounts, int n) {
        int[] plan = new int[amounts.length];
        if (n <= 0) {
            return plan;
        }
        if (count(amounts) < n) {
            return null;
        }
        Integer[] order = new Integer[amounts.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingInt((Integer i) -> amounts[i]).thenComparingInt(i -> i));
        int remaining = n;
        for (int slot : order) {
            if (remaining == 0) {
                break;
            }
            int take = Math.min(Math.max(0, amounts[slot]), remaining);
            plan[slot] = take;
            remaining -= take;
        }
        return plan;
    }

    public static int sum(int[] plan) {
        return count(plan);
    }
}
