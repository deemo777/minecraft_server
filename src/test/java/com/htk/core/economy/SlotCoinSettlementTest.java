package com.htk.core.economy;

import com.htk.core.slots.SlotPayment;
import java.nio.file.*;
import java.util.UUID;
import java.util.logging.Logger;

public final class SlotCoinSettlementTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("slot-payment-");
        var file = directory.resolve("money.yml").toFile();
        var logger = Logger.getLogger("SlotCoinSettlementTest");
        UUID player = UUID.randomUUID(); var wallet = new Economy(file, logger);
        wallet.set(player, 1_000); wallet.save();
        int[] coins = {64};
        check(SlotPayment.attempt(() -> coins[0]--, () -> wallet.settleSlotCoin(player, 300), () -> coins[0]++), "Successful payout");
        check(coins[0] == 63 && wallet.balance(player) == 1_300, "Exactly one coin consumed, total prize credited");
        check(new Economy(file, logger).balance(player) == 1_300, "Prize is durable before animation/disconnect");
        check(SlotPayment.attempt(() -> coins[0]--, () -> wallet.settleSlotCoin(player, 0), () -> coins[0]++), "Losing draw settles");
        check(coins[0] == 62 && wallet.balance(player) == 1_300, "Loss consumes only one coin and does not debit balance");
        check(!SlotPayment.attempt(() -> coins[0]--, () -> false, () -> coins[0]++), "Failure rejected");
        check(coins[0] == 62, "Failed settlement restores coin");
        try {
            SlotPayment.attempt(() -> { coins[0]--; throw new IllegalStateException("inventory save"); },
                    () -> { throw new AssertionError("Must not pay after failed consume"); }, () -> coins[0]++);
            throw new AssertionError("Expected inventory exception");
        } catch (IllegalStateException expected) { check(coins[0] == 62, "Inventory failure restores coin"); }
        try {
            SlotPayment.attempt(() -> coins[0]--, () -> { throw new IllegalStateException("payment"); }, () -> coins[0]++);
            throw new AssertionError("Expected payment exception");
        } catch (IllegalStateException expected) { check(coins[0] == 62, "Payment exception restores coin"); }
        wallet.set(player, Long.MAX_VALUE - 2); wallet.save();
        check(!wallet.settleSlotCoin(player, 3) && wallet.balance(player) == Long.MAX_VALUE - 2, "Overflow never silently truncates prize");
        check(wallet.settleSlotCoin(player, 2) && new Economy(file, logger).balance(player) == Long.MAX_VALUE, "Exact maximum persists");
        check(!wallet.settleSlotCoin(player, -1), "Negative prize rejected");
        // A nonempty directory at the destination forces atomic replacement to fail.
        Path blocked = directory.resolve("blocked.yml");
        var failing = new Economy(blocked.toFile(), logger); failing.set(player, 12);
        Files.createDirectory(blocked); Files.writeString(blocked.resolve("keep.txt"), "occupied");
        check(!SlotPayment.attempt(() -> coins[0]--, () -> failing.settleSlotCoin(player, 100), () -> coins[0]++), "Disk failure rejected");
        check(coins[0] == 62 && failing.balance(player) == 12, "Disk failure restores both inventory and balance");
        check(Files.readString(blocked.resolve("keep.txt")).equals("occupied"), "Unrelated file retained");
        System.out.println("Slot settlement checks passed: " + checks);
    }
}
