package com.htk.core.tools;

public final class RepairRulesTest {
    private static int checks;
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(RepairRules.cost(0, 250) == 0, "undamaged tools cost nothing");
        check(RepairRules.cost(200, 1300) == 200, "requested example: 200 damage out of 1300 costs 200 gold");
        check(RepairRules.cost(50, 250) == 50, "requested example: 50 damage out of 250 costs 50 gold");
        check(RepairRules.cost(1, 250) == 1 && RepairRules.cost(1, 1561) == 1, "one point costs one gold for every tool");
        check(RepairRules.cost(200, 250) == RepairRules.cost(200, 1300), "same damage costs the same regardless of maximum durability");
        check(RepairRules.cost(1560, 1561) == 1560, "repair cost is not capped at the former 500 gold");
        check(RepairRules.cost(9999, 250) == 250, "invalid excess damage only charges restorable points");
        check(RepairRules.cost(-5, 250) == 0 && RepairRules.cost(5, 0) == 0 && RepairRules.cost(5, -1) == 0, "invalid durability safe");
        check(RepairRules.cost(Integer.MAX_VALUE, Integer.MAX_VALUE) == 2_147_483_647L, "large durability does not overflow");
        for (int damage = 0; damage <= 1561; damage++) {
            check(RepairRules.cost(damage, 1561) == damage, "every restored point costs exactly one gold");
        }
        long[] gold = {1000}; int[] damage = {125};
        var wallet = new EnhancementPayment.Wallet() {
            public boolean withdraw(long cost) { if (gold[0] < cost) return false; gold[0] -= cost; return true; }
            public void refund(long cost) { gold[0] += cost; }
        };
        long fee = RepairRules.cost(damage[0], 250);
        check(EnhancementPayment.attempt(wallet, fee, () -> damage[0] = 0, () -> damage[0] = 125), "valid repair commits");
        check(gold[0] == 875 && damage[0] == 0, "125 damage charges 125 gold and fully repairs");
        check(!EnhancementPayment.attempt(wallet, 1000, () -> damage[0] = 0, () -> damage[0] = 125), "insufficient gold blocks transaction");
        damage[0] = 125;
        boolean failed = false;
        try { EnhancementPayment.attempt(wallet, fee, () -> { damage[0] = 0; throw new IllegalStateException(); }, () -> damage[0] = 125); }
        catch (IllegalStateException expected) { failed = true; }
        check(failed && gold[0] == 875 && damage[0] == 125, "failed commit restores damage and refunds gold");
        System.out.println("Repair rules/payment checks passed: " + checks);
    }
}
