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
    private final Map<UUID,java.util.Set<String>> accountUnlocks=new java.util.HashMap<>();
    // Read-only compatibility with already-deducted balances from the removed feature.
    private record LegacyStake(UUID player, long amount) {}
    private final Map<String, LegacyStake> legacyStakes = new java.util.HashMap<>();
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
        var unlocks=yml.getConfigurationSection("account-unlocks");
        if(yml.contains("account-unlocks") && unlocks==null)
            throw new IllegalStateException("계정 해금 기록의 형식이 잘못되었습니다");
        if(unlocks!=null)for(String id:unlocks.getKeys(false)) {
            try {
                if(!unlocks.isList(id) || unlocks.getList(id).stream().anyMatch(v->!(v instanceof String)))
                    throw new IllegalArgumentException("Invalid unlock list");
                accountUnlocks.put(UUID.fromString(id),new java.util.HashSet<>(unlocks.getStringList(id)));
            } catch(IllegalArgumentException failure){throw new IllegalStateException("계정 해금 기록을 읽지 못했습니다",failure);}
        }
        var pending = yml.getConfigurationSection("wagers");
        if (yml.contains("wagers") && pending == null)
            throw new IllegalStateException("참가금 보관 기록의 형식이 잘못되어 경제 데이터 초기화를 중지합니다");
        if (pending != null) for (String key : pending.getKeys(false)) {
            try {
                UUID player = UUID.fromString(pending.getString(key + ".player", ""));
                long amount = pending.getLong(key + ".amount");
                if (amount <= 0) throw new IllegalArgumentException("Invalid legacy stake amount");
                legacyStakes.put(key, new LegacyStake(player, amount));
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
        accountUnlocks.forEach((id,values)->yml.set("account-unlocks."+id,
            values.stream().sorted().toList()));
        legacyStakes.forEach((key, stake) -> {
            yml.set("wagers." + key + ".player", stake.player().toString());
            yml.set("wagers." + key + ".amount", stake.amount());
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
    public enum UnlockPurchase { SUCCESS, OWNED, INSUFFICIENT, SAVE_FAILED }
    public synchronized java.util.Set<String> unlocks(UUID id){
        return java.util.Set.copyOf(accountUnlocks.getOrDefault(id,java.util.Set.of()));
    }
    /** Balance and permanent ownership commit together in one atomic money.yml save. */
    public synchronized UnlockPurchase purchaseUnlock(UUID id,long price,String key) {
        if(price<=0 || key==null || key.isBlank())throw new IllegalArgumentException("Invalid unlock purchase");
        if(unlocks(id).contains(key))return UnlockPurchase.OWNED;
        if(balance(id)<price)return UnlockPurchase.INSUFFICIENT;
        long previous=balance(id);
        balances.put(id,previous-price);
        accountUnlocks.computeIfAbsent(id,ignored->new java.util.HashSet<>()).add(key);
        dirty=true;
        if(persist())return UnlockPurchase.SUCCESS;
        balances.put(id,previous);accountUnlocks.get(id).remove(key);
        if(accountUnlocks.get(id).isEmpty())accountUnlocks.remove(id);
        dirty=true;return UnlockPurchase.SAVE_FAILED;
    }
    /** Settle a physical slot coin immediately, including a losing (zero payout) draw.
     * No saturation: callers reserve room for the largest prize before consuming a coin. */
    public synchronized boolean settleSlotCoin(UUID id, long payout) {
        if (payout < 0 || balance(id) > Long.MAX_VALUE - payout) return false;
        Long previous = balances.get(id);
        if (payout > 0) balances.put(id, balance(id) + payout);
        dirty = true;
        if (persist()) return true;
        if (previous == null) balances.remove(id); else balances.put(id, previous);
        dirty = true;
        return false;
    }
    /** Refund old principal only, clearing the legacy records in the same atomic save. No new stakes can be created. */
    public synchronized boolean refundLegacyStakes() {
        if (legacyStakes.isEmpty()) return true;
        Map<UUID, Long> previous = new java.util.HashMap<>(balances);
        Map<String, LegacyStake> pending = new java.util.HashMap<>(legacyStakes);
        pending.values().forEach(stake -> deposit(stake.player(), stake.amount()));
        legacyStakes.clear();
        dirty = true;
        if (persist()) return true;
        balances.clear(); balances.putAll(previous);
        legacyStakes.putAll(pending);
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
