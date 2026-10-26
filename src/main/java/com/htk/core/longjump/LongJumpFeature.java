package com.htk.core.longjump;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 도약: 달리면서 점프하면 바라보는 방향으로 멀리 뛰어요. 쿨타임 동안은 일반 점프 (화면 문구 없음).
 * 수치는 config.yml 의 long-jump 에서 바꾸고 /servercore reload
 */
public final class LongJumpFeature implements Feature, Listener {

    private final ServerCore plugin;
    private final Map<UUID, Long> readyAt = new HashMap<>();      // 다시 쓸 수 있는 시각
    private final Map<UUID, Long> noFallUntil = new HashMap<>();

    // 설정
    private List<String> worlds;
    private double forward, up;
    private long cooldownMs;
    private boolean noFallDamage;
    private String sound;
    private float soundVolume, soundPitch;
    private Particle particle;
    private int particleCount;

    public LongJumpFeature(ServerCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void reload() {
        loadConfig();
    }

    @Override
    public void disable() {
    }

    private void loadConfig() {
        var c = plugin.getConfig();
        worlds = c.getStringList("long-jump.worlds");
        forward = c.getDouble("long-jump.forward", 1.3);
        up = c.getDouble("long-jump.up", 0.55);
        cooldownMs = (long) (c.getDouble("long-jump.cooldown-seconds", 5) * 1000);
        noFallDamage = c.getBoolean("long-jump.no-fall-damage", true);
        sound = c.getString("long-jump.sound", "minecraft:entity.breeze.jump");
        soundVolume = (float) c.getDouble("long-jump.sound-volume", 0.8);
        soundPitch = (float) c.getDouble("long-jump.sound-pitch", 1.2);
        particleCount = c.getInt("long-jump.particle-count", 12);
        String p = c.getString("long-jump.particle", "CLOUD");
        try {
            particle = p == null || p.isBlank() ? null : Particle.valueOf(p.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("long-jump.particle '" + p + "' 를 모르겠어요. 파티클 없이 동작해요.");
            particle = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent e) {
        Player p = e.getPlayer();
        if (!p.isSprinting() || !canLeap(p)) return;

        long now = System.currentTimeMillis();
        long ready = readyAt.getOrDefault(p.getUniqueId(), 0L);
        if (now < ready) return;   // 쿨타임 중: 일반 점프 그대로
        readyAt.put(p.getUniqueId(), now + cooldownMs);
        if (noFallDamage) noFallUntil.put(p.getUniqueId(), now + 10_000L);

        // 점프가 끝난 다음 틱에 속도를 덮어써요 (원래 점프 힘에 섞이지 않게)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 1.0E-4) return;
            Vector v = dir.normalize().multiply(forward);
            v.setY(up);
            p.setVelocity(v);
            var at = p.getLocation();
            if (sound != null && !sound.isBlank()) p.getWorld().playSound(at, sound, soundVolume, soundPitch);
            if (particle != null) {
                try {
                    p.getWorld().spawnParticle(particle, at, particleCount, 0.3, 0.05, 0.3, 0.02);
                } catch (IllegalArgumentException ignored) {
                    // 추가 데이터가 필요한 파티클은 건너뜀
                }
            }
        });
    }

    private boolean canLeap(Player p) {
        if (!p.hasPermission("servercore.longjump")) return false;
        if (p.getGameMode() == GameMode.SPECTATOR) return false;
        if (p.isFlying() || p.isGliding() || p.isSwimming() || p.isInsideVehicle()) return false;
        return worlds.isEmpty() || worlds.contains(p.getWorld().getName());
    }

    /** 도약한 뒤 첫 낙하 피해는 없애요 (10초 안) */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        Long until = noFallUntil.remove(p.getUniqueId());
        if (until != null && until >= System.currentTimeMillis()) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        readyAt.remove(id);
        noFallUntil.remove(id);
    }
}
