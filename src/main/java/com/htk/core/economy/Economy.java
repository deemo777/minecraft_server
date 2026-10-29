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

    private final java.util.logging.Logger logger;
    private final File file;
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();
    private final java.util.Set<String> rewardReceipts=new java.util.HashSet<>();
    public record Wager(UUID player, long amount) {}
    private final Map<String, Wager> wagers = new java.util.HashMap<>();
    private volatile boolean dirty;

    public Economy(ServerCore plugin) {
        this(new File(plugin.getDataFolder(),"money.yml"),plugin.getLogger());
    }
    Economy(File file,java.util.logging.Logger logger) {
        this.logger=logger;this.file=file;
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = new YamlConfiguration();
        try{yml.load(file);}
        catch(IOException | org.bukkit.configuration.InvalidConfigurationException failure){
            throw new IllegalStateException("money.yml을 읽지 못해 잔액·보상 영수증 초기화를 중지합니다",failure);
        }
        rewardReceipts.addAll(yml.getStringList("reward-receipts"));
        var pending = yml.getConfigurationSection("wagers");
        if (yml.contains("wagers") && pending == null)
            throw new IllegalStateException("참가금 보관 기록의 형식이 잘못되어 경제 데이터 초기화를 중지합니다");
        if (pending != null) for (String key : pending.getKeys(false)) {
            try {
                UUID player = UUID.fromString(pending.getString(key + ".player", ""));
                long amount = pending.getLong(key + ".amount");
                if (amount <= 0) throw new IllegalArgumentException("Invalid wager amount");
                wagers.put(key, new Wager(player, amount));
            } catch (IllegalArgumentException failure) {
                throw new IllegalStateException("보관 중인 참가금을 읽지 못해 경제 데이터 초기화를 중지합니다", failure);
            }
        }
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
        persist();
    }
    private boolean persist() {
        YamlConfiguration yml = new YamlConfiguration();
        balances.forEach((id, v) -> yml.set("balances." + id, v));
        yml.set("reward-receipts",new java.util.ArrayList<>(rewardReceipts));
        wagers.forEach((key, wager) -> {
            yml.set("wagers." + key + ".player", wager.player().toString());
            yml.set("wagers." + key + ".amount", wager.amount());
        });
        try {
            java.nio.file.Files.createDirectories(file.toPath().toAbsolutePath().getParent());
            var temporary=file.toPath().resolveSibling(file.getName()+".tmp");
            yml.save(temporary.toFile());
            try{java.nio.file.Files.move(temporary,file.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            catch(java.nio.file.AtomicMoveNotSupportedException unsupported){java.nio.file.Files.move(temporary,file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            dirty = false;
            return true;
        } catch (IOException e) {
            logger.warning("money.yml 저장 실패: " + e.getMessage());
            return false;
        }
    }
    public synchronized boolean hasRewardReceipt(String receipt){return rewardReceipts.contains(receipt);}
    /** Debit and escrow are saved together. A failed save or duplicate cannot charge the player. */
    public synchronized boolean reserveWager(UUID player, long amount, String key) {
        if (amount <= 0 || key == null || !key.matches("[a-zA-Z0-9/_-]+"))
            throw new IllegalArgumentException("Invalid wager");
        if (wagers.containsKey(key) || balance(player) < amount) return false;
        long previous = balance(player);
        balances.put(player, previous - amount);
        wagers.put(key, new Wager(player, amount));
        dirty = true;
        if (persist()) return true;
        balances.put(player, previous);
        wagers.remove(key);
        dirty = true;
        return false;
    }

    public synchronized Map<String, Wager> pendingWagers(String prefix) {
        Map<String, Wager> result = new java.util.HashMap<>();
        wagers.forEach((key, wager) -> { if (key.startsWith(prefix)) result.put(key, wager); });
        return Map.copyOf(result);
    }

    /** Settle the entire round atomically, including offline accounts. Removed holds make retries harmless. */
    public synchronized boolean settleWagers(Map<String, Long> payouts) {
        if (payouts.values().stream().anyMatch(amount -> amount == null || amount < 0))
            throw new IllegalArgumentException("Invalid wager payout");
        Map<String, Wager> removed = new java.util.HashMap<>();
        Map<UUID, Long> previous = new java.util.HashMap<>();
        for (var entry : payouts.entrySet()) {
            Wager wager = wagers.remove(entry.getKey());
            if (wager == null) continue;
            removed.put(entry.getKey(), wager);
            previous.putIfAbsent(wager.player(), balance(wager.player()));
            deposit(wager.player(), entry.getValue());
        }
        if (removed.isEmpty()) return true;
        dirty = true;
        if (persist()) return true;
        previous.forEach(balances::put);
        wagers.putAll(removed);
        dirty = true;
        return false;
    }
    /** Save the payment and its idempotency receipt in the same atomic file replacement. */
    public synchronized boolean depositOnce(UUID id,long amount,String receipt) {
        if(amount<=0 || receipt==null || receipt.isBlank())throw new IllegalArgumentException("Invalid unique reward");
        if(rewardReceipts.contains(receipt))return true;
        Long previous=balances.get(id);
        deposit(id,amount);rewardReceipts.add(receipt);
        if(persist())return true;
        if(previous==null)balances.remove(id);else balances.put(id,previous);
        rewardReceipts.remove(receipt);dirty=true;return false;
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
