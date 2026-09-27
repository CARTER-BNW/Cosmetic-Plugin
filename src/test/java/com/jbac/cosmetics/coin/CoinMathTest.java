package com.jbac.cosmetics.coin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CoinMathTest {

    @Test
    void countSumsPositiveSlotsOnly() {
        assertEquals(70, CoinMath.count(new int[] {64, 0, 6}));
        assertEquals(0, CoinMath.count(new int[0]));
        assertEquals(5, CoinMath.count(new int[] {-3, 5}));
    }

    @Test
    void planEmptiesSmallestStacksFirst() {
        int[] plan = CoinMath.plan(new int[] {64, 0, 6}, 6);
        assertArrayEquals(new int[] {0, 0, 6}, plan);
        assertEquals(6, CoinMath.sum(plan));
    }

    @Test
    void planSpansStacksWhenNeeded() {
        int[] plan = CoinMath.plan(new int[] {64, 6, 10}, 20);
        assertArrayEquals(new int[] {4, 6, 10}, plan);
        assertEquals(20, CoinMath.sum(plan));
    }

    @Test
    void planReturnsNullWhenInsufficient() {
        assertNull(CoinMath.plan(new int[] {5, 5}, 11));
    }

    @Test
    void planForZeroOrNegativeIsAllZeros() {
        assertArrayEquals(new int[] {0, 0}, CoinMath.plan(new int[] {5, 5}, 0));
        assertArrayEquals(new int[] {0, 0}, CoinMath.plan(new int[] {5, 5}, -1));
    }

    @Test
    void planRemovesExactAmountAcrossEverything() {
        int[] plan = CoinMath.plan(new int[] {64, 64, 3}, 131);
        assertArrayEquals(new int[] {64, 64, 3}, plan);
    }
}
