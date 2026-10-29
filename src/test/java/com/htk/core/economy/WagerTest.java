package com.htk.core.economy;

import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

public final class WagerTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("wager-regression-");
        var file = dir.resolve("money.yml").toFile();
        var log = Logger.getLogger("WagerTest");
        var wallet = new Economy(file, log);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        wallet.ensureAccount(a); wallet.ensureAccount(b);
        check(wallet.reserveWager(a, 1000, "race/one/a"), "first bet accepted");
        check(wallet.reserveWager(b, 500, "race/one/b"), "second player accepted");
        check(!wallet.reserveWager(a, 1000, "race/one/a") && wallet.balance(a) == 2000, "duplicate cannot debit twice");
        check(!wallet.reserveWager(a, 9999, "race/one/poor") && wallet.balance(a) == 2000, "insufficient balance unchanged");
        wallet = new Economy(file, log);
        check(wallet.balance(a) == 2000 && wallet.pendingWagers("race/").size() == 2, "debit and escrow survive crash together");
        Map<String, Long> payouts = Map.of("race/one/a", 3000L, "race/one/b", 0L);
        check(wallet.settleWagers(payouts), "round settles for offline players");
        check(wallet.balance(a) == 5000 && wallet.balance(b) == 2500, "winner gets total 3x, loser loses stake");
        wallet = new Economy(file, log);
        check(wallet.settleWagers(payouts) && wallet.balance(a) == 5000, "settlement retry after restart cannot duplicate payout");
        check(wallet.pendingWagers("race/").isEmpty(), "settlement clears holds atomically");
        check(wallet.reserveWager(a, 100, "race/two/a"), "new round accepted");
        wallet = new Economy(file, log);
        var refunds = new HashMap<String, Long>();
        wallet.pendingWagers("race/").forEach((key, hold) -> refunds.put(key, hold.amount()));
        check(wallet.settleWagers(refunds) && wallet.balance(a) == 5000, "interrupted round refunds reserved stake");
        check(wallet.settleWagers(refunds) && wallet.balance(a) == 5000, "refund retry is idempotent");
        // Force a write failure after a successful debit, then repair and retry.
        check(wallet.reserveWager(a, 1000, "race/three/a"), "third hold created");
        check(wallet.reserveWager(b, 500, "race/three/b"), "third round second player reserved");
        Path backup = dir.resolve("backup.yml");
        Files.move(file.toPath(), backup);
        Files.createDirectory(file.toPath());
        Files.writeString(file.toPath().resolve("block"), "prevent replacement");
        check(!wallet.settleWagers(Map.of("race/three/a", 3000L, "race/three/b", 0L)), "failed write cannot confirm payout");
        check(wallet.balance(a) == 4000 && wallet.balance(b) == 2000 && wallet.pendingWagers("race/").size() == 2, "failed settlement restores all winners, losers and holds");
        check(!wallet.reserveWager(b, 100, "race/failed/b") && wallet.balance(b) == 2000, "failed reservation restores balance");
        Files.delete(file.toPath().resolve("block")); Files.delete(file.toPath()); Files.move(backup, file.toPath());
        check(wallet.settleWagers(Map.of("race/three/a", 3000L, "race/three/b", 0L)) && wallet.balance(a) == 7000, "retry succeeds once storage recovers");
        wallet = new Economy(file, log);
        check(wallet.balance(a) == 7000 && wallet.balance(b) == 2000 && wallet.pendingWagers("race/").isEmpty(), "repaired batch settlement persisted");
        Path corrupt = dir.resolve("corrupt.yml");
        Files.writeString(corrupt, "wagers:\n  broken:\n    player: invalid\n    amount: 100\n");
        boolean rejected = false;
        try { new Economy(corrupt.toFile(), log); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "invalid hold cannot silently disappear");
        Files.writeString(corrupt, "wagers: [broken]\n");
        rejected = false;
        try { new Economy(corrupt.toFile(), log); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "invalid escrow root cannot silently discard reserved gold");
        System.out.println("Wager persistence checks passed: " + checks);
    }
}
