package com.htk.core.jumppad;

import com.htk.core.common.Messages;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Text;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 점프 패드: 중량 무게 압력판과 똑같이 생긴 "점프 패드" 아이템을 설치하면,
 * 밟는 순간 바라보는 방향 앞쪽 + 위쪽으로 한 번 튕겨 줘요. (모든 월드)
 *
 * - 아이템: 중량 무게 압력판 + 아이템 데이터 표시(servercore:jump_pad). 이름만 달라요.
 * - 설치한 자리는 청크 데이터(servercore:jump_pads)에 기억해요. 일반 압력판은 그대로 일반 압력판이에요.
 * - 부수면 점프 패드 아이템으로 돌려줘요. 피스톤으로 밀거나 폭발로 사라지는 건 막아요.
 * - 수치(세기, 쿨타임, 소리, 파티클)는 config.yml 의 jump-pad 에서 바꾸고 /servercore reload
 */
public final class JumpPadFeature implements Feature, Listener {

    private static final Material PLATE = Material.HEAVY_WEIGHTED_PRESSURE_PLATE;

    private final ServerCore plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey chunkKey;
    private final Map<UUID, Long> lastLaunch = new HashMap<>();
    private final Map<UUID, Long> noFallUntil = new HashMap<>();

    // 설정
    private String itemName;
    private double forward, up;
    private boolean useLookPitch;
    private long cooldownMs;
    private boolean noFallDamage;
    private String sound;
    private float soundVolume, soundPitch;
    private Particle particle;
    private int particleCount;

    public JumpPadFeature(ServerCore plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "jump_pad");
        this.chunkKey = new NamespacedKey(plugin, "jump_pads");
    }

    @Override
    public void enable() {
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand cmd = plugin.getCommand("jumppad");
        if (cmd != null) {
            cmd.setExecutor((sender, command, label, args) -> {
                // /jumppad [amount] [player]
                int amount = 1;
                Player target = sender instanceof Player p ? p : null;
                try {
                    if (args.length >= 1) amount = Math.max(1, Math.min(64, Integer.parseInt(args[0])));
                } catch (NumberFormatException e) {
                    Messages.send(sender,Text.mm("<red>개수는 숫자로 적어 주세요. 예: /jumppad 16"));
                    return true;
                }
                if (args.length >= 2) target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    Messages.send(sender,Text.mm("<red>받을 플레이어를 찾을 수 없어요."));
                    return true;
                }
                Player to = target;
                // 가방이 꽉 차서 못 넣은 건 발밑에 떨어뜨려요
                to.getInventory().addItem(createItem(amount)).values()
                        .forEach(left -> to.getWorld().dropItem(to.getLocation(), left));
                Messages.send(sender,Text.mm("<green>점프 패드 " + amount + "개를 " + to.getName() + "에게 줬어요."));
                return true;
            });
        }
    }

    @Override
    public void reload() {
        loadConfig();
    }

    @Override
    public void disable() { }

    private void loadConfig() {
        var c = plugin.getConfig();
        itemName = c.getString("jump-pad.item-name", "<#7ad1ff><bold>점프 패드");
        forward = c.getDouble("jump-pad.forward", 1.6);
        up = c.getDouble("jump-pad.up", 0.9);
        useLookPitch = c.getBoolean("jump-pad.use-look-pitch", false);
        cooldownMs = c.getLong("jump-pad.cooldown-ticks", 20) * 50L;
        noFallDamage = c.getBoolean("jump-pad.no-fall-damage", true);
        sound = c.getString("jump-pad.sound", "minecraft:entity.wind_charge.wind_burst");
        soundVolume = (float) c.getDouble("jump-pad.sound-volume", 1.0);
        soundPitch = (float) c.getDouble("jump-pad.sound-pitch", 1.2);
        particleCount = c.getInt("jump-pad.particle-count", 20);
        String p = c.getString("jump-pad.particle", "CLOUD");
        try {
            particle = p == null || p.isBlank() ? null : Particle.valueOf(p.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("jump-pad.particle '" + p + "' 를 모르겠어요. 파티클 없이 동작해요.");
            particle = null;
        }
    }

    // ---------------- 아이템 ----------------

    public ItemStack createItem(int amount) {
        ItemStack item = new ItemStack(PLATE, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(itemName).decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isPadItem(ItemStack item) {
        if (item == null || item.getType() != PLATE) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    // ---------------- 설치 위치 기억 (청크 데이터) ----------------

    private static int pack(Block b) {
        return (b.getX() & 15) | ((b.getZ() & 15) << 4) | ((b.getY() + 4096) << 8);
    }

    private int[] pads(Chunk chunk) {
        int[] arr = chunk.getPersistentDataContainer().get(chunkKey, PersistentDataType.INTEGER_ARRAY);
        return arr == null ? new int[0] : arr;
    }

    private boolean isPad(Block b) {
        if (b.getType() != PLATE) return false;
        int key = pack(b);
        for (int v : pads(b.getChunk())) if (v == key) return true;
        return false;
    }

    private void setPad(Block b, boolean on) {
        Chunk chunk = b.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        int key = pack(b);
        int[] arr = pads(chunk);
        int[] next;
        if (on) {
            for (int v : arr) if (v == key) return;
            next = Arrays.copyOf(arr, arr.length + 1);
            next[arr.length] = key;
        } else {
            next = Arrays.stream(arr).filter(v -> v != key).toArray();
            if (next.length == arr.length) return;
        }
        if (next.length == 0) pdc.remove(chunkKey);
        else pdc.set(chunkKey, PersistentDataType.INTEGER_ARRAY, next);
    }

    // ---------------- 설치 / 부수기 ----------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        // 일반 압력판을 놓으면 혹시 남아 있던 예전 기록을 지워요 (일반 압력판은 패드가 아님)
        setPad(e.getBlockPlaced(), isPadItem(e.getItemInHand()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        // 패드 자체를 부쉈을 때
        if (isPad(b)) {
            breakPad(b, e);
            return;
        }
        // 패드 아래 블록을 부쉈을 때 (받침이 없어지면 압력판도 떨어져요)
        Block above = b.getRelative(BlockFace.UP);
        if (isPad(above)) {
            setPad(above, false);
            above.setType(Material.AIR);
            if (e.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
                above.getWorld().dropItemNaturally(above.getLocation().add(0.5, 0.2, 0.5), createItem(1));
            }
        }
    }

    private void breakPad(Block b, BlockBreakEvent e) {
        setPad(b, false);
        e.setDropItems(false);
        if (e.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.2, 0.5), createItem(1));
        }
    }

    /** 폭발로는 패드(와 그 받침)가 안 부서지게 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        protectFromExplosion(e.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        protectFromExplosion(e.blockList());
    }

    private void protectFromExplosion(List<Block> blocks) {
        blocks.removeIf(b -> isPad(b) || isPad(b.getRelative(BlockFace.UP)));
    }

    /** 피스톤이 패드나 그 받침을 밀고 당기지 못하게 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (touchesPad(e.getBlocks())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (touchesPad(e.getBlocks())) e.setCancelled(true);
    }

    private boolean touchesPad(List<Block> blocks) {
        for (Block b : blocks) if (isPad(b) || isPad(b.getRelative(BlockFace.UP))) return true;
        return false;
    }

    // ---------------- 밟았을 때 ----------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onStep(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL) return;
        Block b = e.getClickedBlock();
        if (b == null || !isPad(b)) return;
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastLaunch.get(p.getUniqueId());
        if (last != null && now - last < cooldownMs) return;   // 한 번 밟을 때 한 번만
        lastLaunch.put(p.getUniqueId(), now);

        Vector dir = p.getLocation().getDirection();
        if (!useLookPitch) dir.setY(0);
        if (dir.lengthSquared() < 1.0E-4) dir = new Vector(0, 0, 0);
        else dir.normalize();
        Vector v = dir.multiply(forward);
        v.setY(v.getY() + up);
        p.setVelocity(v);
        if (noFallDamage) noFallUntil.put(p.getUniqueId(), now + 10_000L);

        Location at = b.getLocation().add(0.5, 0.2, 0.5);
        if (sound != null && !sound.isBlank()) p.getWorld().playSound(at, sound, soundVolume, soundPitch);
        if (particle != null) {
            try {
                p.getWorld().spawnParticle(particle, at, particleCount, 0.3, 0.1, 0.3, 0.02);
            } catch (IllegalArgumentException ignored) {
                // 추가 데이터가 필요한 파티클(DUST 등)은 건너뜀
            }
        }
    }

    /** 튕겨 나간 뒤 첫 낙하 피해는 없애요 (10초 안) */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        Long until = noFallUntil.remove(p.getUniqueId());
        if (until != null && until >= System.currentTimeMillis()) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastLaunch.remove(e.getPlayer().getUniqueId());
        noFallUntil.remove(e.getPlayer().getUniqueId());
    }
}
