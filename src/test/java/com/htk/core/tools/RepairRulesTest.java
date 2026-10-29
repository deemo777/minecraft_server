package com.htk.core.tools;

public final class RepairRulesTest {
    private static int checks;
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(RepairRules.cost(0, 250, 500) == 0, "undamaged tools cost nothing");
        check(RepairRules.cost(1, 250, 500) == 2, "iron one-point damage");
        check(RepairRules.cost(125, 250, 500) == 250, "half damage costs half");
        check(RepairRules.cost(1, 1561, 500) == 1, "fractional gold rounds upward");
        check(RepairRules.cost(1560, 1561, 500) == 500, "near-broken tool caps at full cost");
        check(RepairRules.cost(9999, 250, 500) == 500, "excess damage cannot exceed configured cost");
        check(RepairRules.cost(-5, 250, 500) == 0 && RepairRules.cost(5, 0, 500) == 0, "invalid durability safe");
        check(RepairRules.cost(10, 250, 0) == 0 && RepairRules.cost(10, 250, -5) == 0, "free configured repair");
        check(RepairRules.cost(Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE) == 1_000_000_000L, "overflow-safe bounded price");
        long previous = 0;
        for (int damage = 0; damage <= 1561; damage++) {
            long cost = RepairRules.cost(damage, 1561, 500);
            check(cost >= previous && cost <= 500, "price grows with damage and stays bounded"); previous = cost;
        }
        long[] gold = {1000}; int[] damage = {125};
        var wallet = new EnhancementPayment.Wallet() {
            public boolean withdraw(long cost) { if (gold[0] < cost) return false; gold[0] -= cost; return true; }
            public void refund(long cost) { gold[0] += cost; }
        };
        long fee = RepairRules.cost(damage[0], 250, 500);
        check(EnhancementPayment.attempt(wallet, fee, () -> damage[0] = 0, () -> damage[0] = 125), "valid repair commits");
        check(gold[0] == 750 && damage[0] == 0, "exact charge and full repair");
        check(!EnhancementPayment.attempt(wallet, 1000, () -> damage[0] = 0, () -> damage[0] = 125), "insufficient gold blocks transaction");
        damage[0] = 125;
        boolean failed = false;
        try { EnhancementPayment.attempt(wallet, fee, () -> { damage[0] = 0; throw new IllegalStateException(); }, () -> damage[0] = 125); }
        catch (IllegalStateException expected) { failed = true; }
        check(failed && gold[0] == 750 && damage[0] == 125, "failed commit restores damage and refunds gold");
        System.out.println("Repair rules/payment checks passed: " + checks);
    }
}
