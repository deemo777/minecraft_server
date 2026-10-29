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
