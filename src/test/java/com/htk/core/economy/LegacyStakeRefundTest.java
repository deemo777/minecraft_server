package com.htk.core.economy;

import com.htk.core.common.Points;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.UUID;
import java.util.logging.Logger;

/** Migration only: fixtures are pre-existing data, no wager-creation or payout APIs remain. */
public final class LegacyStakeRefundTest {
    private static int checks;
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("legacy-refund-regression-");
        var file = directory.resolve("money.yml").toFile();
        var log = Logger.getLogger("LegacyStakeRefundTest");
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), other = UUID.randomUUID();
        var data = new YamlConfiguration();
        data.set("balances." + first, 1800);
        data.set("balances." + second, 2500);
        data.set("balances." + other, 9000);
        data.set("reward-receipts", java.util.List.of("existing-reward"));
        data.set("wagers.race/old/first.player", first.toString()); data.set("wagers.race/old/first.amount", 1000);
        data.set("wagers.race/older/first.player", first.toString()); data.set("wagers.race/older/first.amount", 200);
        data.set("wagers.race/old/second.player", second.toString()); data.set("wagers.race/old/second.amount", 500);
        data.save(file);
        var wallet = new Economy(file, log);
        check(wallet.refundLegacyStakes(), "legacy principal refund succeeds without a game feature");
        check(wallet.balance(first) == 3000 && wallet.balance(second) == 3000, "offline accounts receive exact principal, including multiple records");
        check(wallet.balance(other) == 9000 && wallet.hasRewardReceipt("existing-reward"), "unrelated balances and reward receipts preserved");
        check(!YamlConfiguration.loadConfiguration(file).contains("wagers"), "refunded legacy records removed from disk");
        check(wallet.refundLegacyStakes() && wallet.balance(first) == 3000, "repeat cannot duplicate refund");
        wallet = new Economy(file, log);
        check(wallet.refundLegacyStakes() && wallet.balance(first) == 3000, "restart cannot duplicate refund");

        // An unavailable destination must preserve both original balances and pending records.
        data.save(file); wallet = new Economy(file, log);
        Path backup = directory.resolve("money.backup.yml"); Files.move(file.toPath(), backup);
        Files.createDirectory(file.toPath()); Files.writeString(file.toPath().resolve("block"), "prevent replacement");
        check(!wallet.refundLegacyStakes(), "failed atomic save does not confirm refund");
        check(wallet.balance(first) == 1800 && wallet.balance(second) == 2500, "failed batch refund rolls back all balances");
        Files.delete(file.toPath().resolve("block")); Files.delete(file.toPath()); Files.move(backup, file.toPath());
        check(wallet.refundLegacyStakes(), "retry succeeds once storage recovers");
        wallet = new Economy(file, log);
        check(wallet.balance(first) == 3000 && wallet.balance(second) == 3000, "recovered refund persisted once");

        Path invalid = directory.resolve("invalid.yml");
        Files.writeString(invalid, "wagers: [broken]\n");
        boolean rejected = false;
        try { new Economy(invalid.toFile(), log); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "malformed legacy records cannot be silently discarded");
        Files.writeString(invalid, "wagers:\n  broken:\n    player: invalid\n    amount: 100\n");
        rejected = false;
        try { new Economy(invalid.toFile(), log); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "invalid legacy owner is rejected");

        var pointsData = new YamlConfiguration();
        pointsData.set("points.race-track.world", "old-world"); pointsData.set("points.village.world", "village");
        pointsData.save(directory.resolve("points.yml").toFile());
        var points = new Points(directory.toFile(), log);
        check(points.remove("race-track") && !points.names().contains("race-track"), "retired point removed");
        points = new Points(directory.toFile(), log);
        check(points.names().contains("village") && !points.names().contains("race-track"), "unrelated saved points survive cleanup and restart");
        System.out.println("Legacy refund migration checks passed: " + checks);
    }
}
