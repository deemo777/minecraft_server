package com.htk.core.land;

import org.bukkit.Location;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

final class LandStoreRegression {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void run() {
        try {
            Path base = Path.of("build", "land-regression").toAbsolutePath();
            Files.createDirectories(base);
            Path directory = Files.createTempDirectory(base, "store-");
            var log = Logger.getLogger("land-regression");
            var store = new LandStore(directory.toFile(), log);
            UUID owner = UUID.randomUUID(), trusted = UUID.randomUUID();
            var plot = LandLayout.square(-2, -3, Land.key(-2, -3));
            Land land = store.create(owner, "Owner", "village", -2, -3,
                    new Location(null, -24, 65, -40), plot);
            land.debt = 50000;
            land.members.add(trusted);
            land.hasCore = true;
            land.coreX = -24; land.coreY = 64; land.coreZ = -40;
            store.save();
            check(land.size() == 1 && land.plot.size() == 4, "reservation is not immediate unlocking");
            check(store.at("village", -1, -3) == land && !land.has(-1, -3), "locked chunks remain reserved");
            var loaded = new LandStore(directory.toFile(), log);
            Land restored = loaded.of(owner);
            check(restored.debt == 50000 && restored.members.contains(trusted), "debts and trust survive restart");
            check(restored.firstChunk() == Land.key(-2, -3) && restored.fixedPlot(), "core order and footprint survive restart");
            check(loaded.at("village", -1, -2) == restored && !restored.has(-1, -2), "locked index survives restart");
            loaded.addChunk(restored, -1, -3);
            check(restored.size() == 2 && restored.debt == 50000, "unlock does not duplicate or clear debt in store");
            loaded.removeChunk(restored, -1, -3);
            check(!restored.has(-1, -3) && loaded.at("village", -1, -3) == restored,
                    "admin relock keeps reservation protected");
            restored.debt = 0;
            loaded.addChunk(restored, -1, -3, 150000);
            Land financed = new LandStore(directory.toFile(), log).of(owner);
            check(financed.has(-1, -3) && financed.debt == 150000,
                    "unlock and new debt are persisted together");
            loaded.addChunk(restored, -1, -3, 150000);
            check(restored.debt == 150000, "duplicate unlock cannot double-charge debt");
            check(loaded.backupBeforeMigration(), "migration backup must succeed before conversion");
            loaded.remove(restored);
            for (long chunk : plot) check(loaded.at("village", Land.cx(chunk), Land.cz(chunk)) == null,
                    "abandon/delete releases all four reserved chunks");
            check(new LandStore(directory.toFile(), log).of(owner) == null, "deleted plot remains deleted after restart");
            System.out.println("Land persistence regression checks passed.");
        } catch (Exception failure) {
            throw new AssertionError("Land persistence regression failed", failure);
        }
    }
}
