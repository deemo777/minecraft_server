package com.htk.core.slots;

/** One independent draw per coin; multipliers include the original stake. */
public final class SlotRules {
    public static final int SPIN_TICKS = 90;
    public static final int RESULT_TICKS = 60;
    private SlotRules() { }

    public static int multiplier(int roll) {
        if (roll < 0 || roll >= 100) throw new IllegalArgumentException("Roll must be 0..99");
        if (roll < 25) return 2;
        if (roll < 35) return 3;
        if (roll < 40) return 4;
        if (roll < 41) return 5;
        return 0;
    }

    /** Reserve room for every possible outcome before drawing, avoiding biased retries. */
    public static boolean canPlay(long coin, long balance) {
        return coin > 0 && coin <= Long.MAX_VALUE / 5 && balance >= 0
                && balance <= Long.MAX_VALUE - coin * 5;
    }

    public static long payout(long coin, int multiplier) {
        if (coin <= 0 || (multiplier != 0 && (multiplier < 2 || multiplier > 5)))
            throw new IllegalArgumentException("Invalid slot payment");
        return Math.multiplyExact(coin, multiplier);
    }

    /** Winners have three matching symbols; losses never display a winning combination. */
    public static int[] symbols(int multiplier, int variant) {
        int first = Math.floorMod(variant, 4);
        if (multiplier == 0) return new int[]{first, (first + 1) % 4, (first + 2) % 4};
        if (multiplier < 2 || multiplier > 5) throw new IllegalArgumentException("Invalid multiplier");
        return new int[]{multiplier - 2, multiplier - 2, multiplier - 2};
    }

    public static int stopTick(int reel) {
        if (reel < 0 || reel > 2) throw new IllegalArgumentException("Invalid reel");
        return SPIN_TICKS - 24 + reel * 12;
    }

    public static int pack(int x, int y, int z) {
        return (x & 15) | ((z & 15) << 4) | ((y + 4096) << 8);
    }
}
