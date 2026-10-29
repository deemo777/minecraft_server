package com.htk.core.economy;

import com.htk.core.ServerCore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 골드(G) 잔액. plugins/ServerCore/money.yml 에 UUID별로 저장해요.
 * 바뀐 게 있으면 30초마다, 그리고 서버가 꺼질 때 저장.
 */
public final class Economy {

    private final ServerCore plugin;
    private final File file;
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public Economy(ServerCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "money.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        var sec = yml.getConfigurationSection("balances");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), sec.getLong(key, 0));
            } catch (IllegalArgumentException ignored) {
                // 잘못된 줄은 건너뜀
            }
        }
    }

    public synchronized void save() {
        if (!dirty) return;
        YamlConfiguration yml = new YamlConfiguration();
        balances.forEach((id, v) -> yml.set("balances." + id, v));
        try {
            yml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("money.yml 저장 실패: " + e.getMessage());
        }
    }

    /** 처음 보는 플레이어면 시작 금액을 줘요. 처음이었으면 true */
    public boolean ensureAccount(UUID id) {
        if (balances.containsKey(id)) return false;
        balances.put(id, 3000L);
        dirty = true;
        return true;
    }

    public long balance(UUID id) {
        return balances.getOrDefault(id, 0L);
    }

    public boolean has(UUID id, long amount) {
        return balance(id) >= amount;
    }

    public void deposit(UUID id, long amount) {
        if (amount <= 0) return;
        balances.merge(id, amount, (a, b) -> safeAdd(a, b));
        dirty = true;
    }

    /** 잔액이 모자라면 아무것도 안 하고 false */
    public boolean withdraw(UUID id, long amount) {
        if (amount <= 0) return true;
        long now = balance(id);
        if (now < amount) return false;
        balances.put(id, now - amount);
        dirty = true;
        return true;
    }

    public void set(UUID id, long amount) {
        balances.put(id, Math.max(0, amount));
        dirty = true;
    }

    private static long safeAdd(long a, long b) {
        long r = a + b;
        return r < a ? Long.MAX_VALUE : r;
    }

    /** 1234567 → "1,234,567G" */
    public static String format(long amount) {
        return NumberFormat.getIntegerInstance(Locale.US).format(amount) + "G";
    }
}
