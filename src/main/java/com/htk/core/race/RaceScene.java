package com.htk.core.race;

import com.htk.core.ServerCore;
import com.htk.core.common.Text;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.*;

/** Temporary world actors only: no block edits, AI, breeding, loot, or saved race mobs. */
final class RaceScene implements Listener {
    private final ServerCore plugin;
    private final RaceFeature feature;
    private final NamespacedKey key;
    private final List<Entity> entities = new ArrayList<>();
    private final List<Chunk> tickets = new ArrayList<>();
    private final List<Mob> runners = new ArrayList<>();
    private Location start;
    private TextDisplay board;
    private int frame;

    RaceScene(ServerCore plugin, RaceFeature feature) {
        this.plugin = plugin; this.feature = feature; key = new NamespacedKey(plugin, "race_actor");
    }
    void enable() {
        for (World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) if (marked(entity)) entity.remove();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    private boolean marked(Entity entity) { return entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE); }
    @EventHandler public void onChunk(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) if (marked(entity) && !entities.contains(entity)) entity.remove();
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDamage(EntityDamageEvent event) {
        if (marked(event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onInteract(PlayerInteractEntityEvent event) {
        if (!marked(event.getRightClicked())) return;
        event.setCancelled(true);
        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) feature.open(event.getPlayer());
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onLeash(PlayerLeashEntityEvent event) {
        if (marked(event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onBlock(EntityChangeBlockEvent event) {
        if (marked(event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDrop(EntityDropItemEvent event) {
        if (marked(event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onPlate(EntityInteractEvent event) {
        if (marked(event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onTarget(EntityTargetEvent event) {
        if (marked(event.getEntity()) || (event.getTarget() != null && marked(event.getTarget()))) event.setCancelled(true);
    }

    static Location point(Location origin, double distance, double lane) {
        Vector forward = origin.getDirection().setY(0).normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        return origin.clone().add(forward.multiply(distance)).add(right.multiply(lane));
    }
    static boolean clear(Location origin) {
        World world = origin.getWorld();
        if (world == null || origin.getY() < world.getMinHeight() + 1 || origin.getY() + 4 >= world.getMaxHeight()) return false;
        for (int distance = 0; distance <= 20; distance++) for (int lane = -3; lane <= 3; lane++) {
            Location at = point(origin, distance, lane);
            if (!world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4) || !world.getWorldBorder().isInside(at)) return false;
            if (!at.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) return false;
            for (int height = 0; height <= 2; height++) {
                var block = at.clone().add(0, height, 0).getBlock();
                if (!block.isPassable() || block.isLiquid()) return false;
            }
        }
        return true;
    }
    boolean start(Player initiator) {
        stop(); frame = 0;
        // Player-started rounds never depend on an administrator's saved point or its world.
        start = initiator == null ? plugin.points().get("race-track")
                : RacePlacement.find(initiator.getLocation(), RaceScene::clear);
        if (start == null) return true;
        start.setPitch(0); start.setYaw(Math.round(start.getYaw() / 90f) * 90f);
        if (!clear(start)) { start = null; return false; }
        try {
            Set<Chunk> chunks = new HashSet<>();
            for (int distance = 0; distance <= 20; distance++) for (int lane = -3; lane <= 3; lane++)
                chunks.add(point(start, distance, lane).getChunk());
            for (Chunk chunk : chunks) {
                chunk.addPluginChunkTicket(plugin); tickets.add(chunk);
            }
            runners.add(actor(start.getWorld().spawn(point(start, 0, -2), Pig.class)));
            runners.add(actor(start.getWorld().spawn(point(start, 0, 0), Rabbit.class)));
            runners.add(actor(start.getWorld().spawn(point(start, 0, 2), Chicken.class)));
            for (int runner = 0; runner < 3; runner++) {
                var animal = runners.get(runner);
                animal.setAI(false); animal.setCollidable(false); animal.setRemoveWhenFarAway(false);
                animal.setCustomNameVisible(true);
                animal.customName(Text.mm(RaceFeature.COLORS[runner] + (runner + 1) + "번 · " + RaceFeature.NAMES[runner]));
                if (animal instanceof Ageable ageable) { ageable.setAdult(); ageable.setAgeLock(true); }
                // With no AI or gravity, movement is entirely controlled by the race plan.
            }
            board = actor(start.getWorld().spawn(point(start, 10, 0).add(0, 3, 0), TextDisplay.class));
            board.setBillboard(Display.Billboard.CENTER); board.setSeeThrough(false);
            board.setBackgroundColor(Color.fromARGB(180, 57, 40, 28)); board.setLineWidth(280);
            board.text(Text.mm("<gold><bold>마을 동물 경주</bold>\n<gray>/race · 무료 관전"));
            var finish = actor(start.getWorld().spawn(point(start, 20, 0).add(0, 2.5, 0), TextDisplay.class));
            finish.setBillboard(Display.Billboard.CENTER); finish.text(Text.mm("<gold><bold>🏁 결 승 선 🏁"));
            return update();
        } catch (RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "경주 장면 생성 실패", failure);
            stop(); return false;
        }
    }
    private <T extends Entity> T actor(T entity) {
        entity.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        entity.setPersistent(false); entity.setInvulnerable(true); entity.setSilent(true); entity.setGravity(false);
        entities.add(entity);
        return entity;
    }
    boolean update() {
        if (start == null) return true;
        frame++;
        if (frame % 10 == 0 && !clear(start)) return false;
        if (entities.stream().anyMatch(entity -> !entity.isValid()) || !start.getWorld().getWorldBorder().isInside(point(start, 20, 0))) return false;
        for (int runner = 0; runner < runners.size(); runner++) {
            double progress = feature.progress(runner);
            Location at = point(start, progress * 20, (runner - 1) * 2);
            if (feature.phase() == RaceFeature.Phase.RUNNING) at.add(0, Math.abs(Math.sin(frame * 0.65 + runner)) * 0.16, 0);
            // All target chunks are ticketed before a round starts.
            if (!runners.get(runner).teleport(at)) return false;
        }
        if (frame % 10 == 0) {
            board.text(Text.mm("<gold><bold>마을 동물 경주</bold>\n<white>" + feature.status() + "\n<gray>/race · 무료 관전"));
            for (int lane = -3; lane <= 3; lane++) start.getWorld().spawnParticle(Particle.END_ROD, point(start, 20, lane).add(0, 0.15, 0), 1, 0, 0, 0, 0);
            for (int distance = 0; distance <= 20; distance += 2) for (int lane : new int[]{-3, 3})
                start.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, point(start, distance, lane).add(0, 0.1, 0), 1, 0, 0, 0, 0);
        }
        return true;
    }
    boolean near(Player player) {
        return start != null && player.getWorld().equals(start.getWorld()) && player.getLocation().distanceSquared(point(start, 10, 0)) <= 48 * 48;
    }
    boolean visible() { return start != null; }
    void celebrate(int winner) {
        if (start == null) return;
        start.getWorld().spawnParticle(Particle.FIREWORK, runners.get(winner).getLocation().add(0, 1, 0), 35, 0.6, 0.8, 0.6, 0.03);
        if (board != null) board.text(Text.mm("<gold><bold>경주 결과</bold>\n" + RaceFeature.COLORS[winner] + RaceFeature.NAMES[winner] + " 우승!\n<gray>적중 시 원금 포함 3배"));
    }
    void stop() {
        for (Entity entity : entities) entity.remove();
        entities.clear(); runners.clear(); board = null; start = null;
        for (Chunk chunk : tickets) chunk.removePluginChunkTicket(plugin);
        tickets.clear();
    }
}
