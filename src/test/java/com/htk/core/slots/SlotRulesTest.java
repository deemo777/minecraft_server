package com.htk.core.slots;

import java.util.Arrays;

public final class SlotRulesTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static void invalid(Runnable work) {
        try { work.run(); throw new AssertionError("Expected invalid input"); }
        catch (IllegalArgumentException | ArithmeticException expected) { checks++; }
    }
    public static void main(String[] args) {
        int[] counts = new int[6]; long paid = 0;
        for (int draw = 0; draw < 100; draw++) {
            int multiplier = SlotRules.multiplier(draw); counts[multiplier]++;
            paid += SlotRules.payout(100, multiplier);
            for (int variant = -4; variant <= 8; variant++) {
                int[] symbols = SlotRules.symbols(multiplier, variant);
                check(Arrays.stream(symbols).allMatch(s -> s >= 0 && s < 4), "Symbols exist");
                check((symbols[0] == symbols[1] && symbols[1] == symbols[2]) == (multiplier > 0), "Display matches prize");
                if (multiplier > 0) check(symbols[0] == multiplier - 2, "Matching symbol has advertised multiplier");
            }
        }
        check(Arrays.equals(counts, new int[]{59, 0, 25, 10, 5, 1}), "Exact mutually exclusive odds");
        check(paid == 10_500, "105% total return, including stake");
        check(SlotRules.payout(100, 2) == 200, "2x is total payout, not net profit");
        check(SlotRules.canPlay(1, 0), "Empty balance may play a physical coin");
        check(!SlotRules.canPlay(0, 0) && !SlotRules.canPlay(-1, 0), "Reject missing or forged negative coins");
        check(SlotRules.canPlay(Long.MAX_VALUE / 5, 0), "Largest safe coin");
        check(!SlotRules.canPlay(Long.MAX_VALUE / 5 + 1, 0), "Multiplication overflow rejected before draw");
        check(SlotRules.canPlay(100, Long.MAX_VALUE - 500), "Exact capacity accepted");
        check(!SlotRules.canPlay(100, Long.MAX_VALUE - 499), "Every outcome must fit before draw");
        check(!SlotRules.canPlay(1, -1), "Invalid balance rejected");
        for (int reel = 0; reel < 3; reel++) check(SlotRules.stopTick(reel) == 66 + reel * 12, "Staggered stops");
        for (int x : new int[]{-33, -16, -1, 0, 15, 16, 257})
            for (int z : new int[]{-17, -1, 0, 15, 31}) for (int y : new int[]{-64, 0, 319}) {
                int packed = SlotRules.pack(x, y, z);
                check((packed & 15) == (x & 15) && ((packed >>> 4) & 15) == (z & 15)
                        && (packed >>> 8) - 4096 == y, "Chunk persistence handles negative coordinates");
            }
        invalid(() -> SlotRules.multiplier(-1)); invalid(() -> SlotRules.multiplier(100));
        invalid(() -> SlotRules.payout(100, 1)); invalid(() -> SlotRules.payout(0, 2));
        invalid(() -> SlotRules.payout(Long.MAX_VALUE, 5)); invalid(() -> SlotRules.stopTick(3));
        System.out.println("Slot rules checks passed: " + checks);
    }
}
