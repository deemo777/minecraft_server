package com.htk.core.land;

import com.htk.core.common.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 땅 보호: 주인·이웃이 아니면 아무것도 못 해요.
 * 그리고 땅 밖에서 들어오는 피해(폭발, 피스톤, 물·용암, 불, 몹이 블록 바꾸기, 나무 자람)도 막아요.
 */
final class LandProtection implements Listener {

    private final LandFeature f;
    private final LandStore store;
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    /** 블록을 바꾸는 몹 (엔더맨이 블록 들기, 좀비가 문 부수기 등) */
    private static final Set<EntityType> GRIEFERS = Set.of(EntityType.ENDERMAN, EntityType.RAVAGER,
            EntityType.WITHER, EntityType.WITHER_SKULL, EntityType.SILVERFISH, EntityType.ZOMBIE,
            EntityType.HUSK, EntityType.ZOMBIE_VILLAGER, EntityType.VINDICATOR, EntityType.EVOKER);

    LandProtection(LandFeature f) {
        this.f = f;
        this.store = f.store();
    }

    /** 막아야 하면 true (막으면서 가끔 안내) */
    private boolean deny(Player p, Location where) {
        Land land = store.at(where);
        if (f.canBuild(p, land)) return false;
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(p.getUniqueId());
        if (last == null || now - last > 1500) {
            lastWarn.put(p.getUniqueId(), now);
            p.sendActionBar(Text.mm(f.msg("denied", "<red>%owner%님의 집이에요. 허가가 있어야 해요.")
                    .replace("%owner%", land.ownerName)));
        }
        return true;
    }

    private void deny(Player p, Location where, Cancellable e) {
        if (deny(p, where)) e.setCancelled(true);
    }

    /** 공격하거나 부순 "사람" (화살 등은 쏜 사람) */
    private static Player culprit(Entity e) {
        if (e instanceof Player p) return p;
        if (e instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    // ---------------- 사람이 하는 일 ----------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        deny(e.getPlayer(), e.getBlock().getLocation(), e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        deny(e.getPlayer(), e.getBlock().getLocation(), e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent e) {
        for (BlockState s : e.getReplacedBlockStates()) {
            if (deny(e.getPlayer(), s.getLocation())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    /** 상자·문·버튼·화로·밭 밟기 등 블록 우클릭/밟기 전부 */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && f.coreAt(b) != null) return;   // 집터는 LandFeature 가 처리
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL) {
            if (e.getAction() == Action.PHYSICAL && b.getType() != Material.FARMLAND
                    && b.getType() != Material.TURTLE_EGG && b.getType() != Material.SNIFFER_EGG) {
                // 압력판·인계철선·점프 패드는 밟아도 돼요 (밭·알만 보호)
                return;
            }
            if (deny(e.getPlayer(), b.getLocation())) {
                e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
                e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                e.setCancelled(true);
            }
        } else if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            // 불 끄기, 용 알 건드리기 등
            Block front = b.getRelative(e.getBlockFace());
            if (front.getType() == Material.FIRE || b.getType() == Material.DRAGON_EGG) {
                deny(e.getPlayer(), b.getLocation(), e);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        deny(e.getPlayer(), e.getBlock().getLocation(), e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        deny(e.getPlayer(), e.getBlock().getLocation(), e);
    }

    /** 동물·주민·액자·갑옷 거치대·보트·수레 우클릭 (먹이 주기, 털 깎기, 끈 묶기, 액자 돌리기 등) */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Entity t = e.getRightClicked();
        if (t instanceof Player || t instanceof Enemy) return;
        deny(e.getPlayer(), t.getLocation(), e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent e) {
        Entity t = e.getRightClicked();
        if (t instanceof Player || t instanceof Enemy) return;
        deny(e.getPlayer(), t.getLocation(), e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        deny(e.getPlayer(), e.getRightClicked().getLocation(), e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLectern(PlayerTakeLecternBookEvent e) {
        deny(e.getPlayer(), e.getLectern().getLocation(), e);
    }

    /** 동물·주민·액자·갑옷 거치대 때리기 (몬스터·사람은 그대로) */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        if (victim instanceof Player || victim instanceof Enemy) return;
        Player p = culprit(e.getDamager());
        if (p != null) {
            deny(p, victim.getLocation(), e);
        } else if (f.protectExplosions() && store.at(victim.getLocation()) != null
                && (e.getDamager().getType() == EntityType.CREEPER || e.getDamager().getType() == EntityType.TNT
                || e.getDamager().getType() == EntityType.TNT_MINECART || e.getDamager().getType() == EntityType.END_CRYSTAL)) {
            e.setCancelled(true);       // 폭발로 동물·액자가 다치지 않게
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreakBy(HangingBreakByEntityEvent e) {
        Player p = culprit(e.getRemover());
        if (p != null) deny(p, e.getEntity().getLocation(), e);
        else if (store.at(e.getEntity().getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        if (e.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION && f.protectExplosions()
                && store.at(e.getEntity().getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() != null) deny(e.getPlayer(), e.getEntity().getLocation(), e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent e) {
        Player p = e.getAttacker() == null ? null : culprit(e.getAttacker());
        if (p != null) deny(p, e.getVehicle().getLocation(), e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent e) {
        Player p = e.getAttacker() == null ? null : culprit(e.getAttacker());
        if (p != null) deny(p, e.getVehicle().getLocation(), e);
    }

    // ---------------- 땅 밖에서 들어오는 피해 ----------------

    /** 같은 땅(또는 둘 다 야생)이면 괜찮음 */
    private boolean sameOwner(Location a, Location b) {
        return store.at(a) == store.at(b);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (f.protectExplosions()) e.blockList().removeIf(b -> store.at(b) != null);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (f.protectExplosions()) e.blockList().removeIf(b -> store.at(b) != null);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        Location piston = e.getBlock().getLocation();
        BlockFace dir = e.getDirection();
        if (!sameOwner(piston, e.getBlock().getRelative(dir).getLocation())) {
            e.setCancelled(true);
            return;
        }
        for (Block b : e.getBlocks()) {
            if (!sameOwner(piston, b.getLocation()) || !sameOwner(piston, b.getRelative(dir).getLocation())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        Location piston = e.getBlock().getLocation();
        for (Block b : e.getBlocks()) {
            if (!sameOwner(piston, b.getLocation())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    /** 물·용암이 다른 땅으로 흘러 들어가지 않게 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (!sameOwner(e.getBlock().getLocation(), e.getToBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (store.at(e.getBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (e.getPlayer() != null) {
            deny(e.getPlayer(), e.getBlock().getLocation(), e);
        } else if (store.at(e.getBlock()) != null) {
            e.setCancelled(true);      // 불 번짐, 번개, 용암 옆 불, 발사기 등
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (e.getNewState().getType() == Material.FIRE && store.at(e.getBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        Entity en = e.getEntity();
        if (en instanceof Player p) {
            deny(p, e.getBlock().getLocation(), e);
            return;
        }
        if (store.at(e.getBlock()) == null) return;
        if (GRIEFERS.contains(en.getType())) {
            e.setCancelled(true);
            return;
        }
        // 화살·삼지창 등으로 다른 사람 땅의 블록 바꾸기 (과녁, 장식 단지 깨기 등)
        if (en instanceof Projectile pr && pr.getShooter() instanceof Player p) deny(p, e.getBlock().getLocation(), e);
    }

    /** 땅 밖에서 자란 나무·버섯이 다른 사람 땅의 블록을 덮지 않게 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent e) {
        Land from = store.at(e.getLocation());
        List<BlockState> blocks = e.getBlocks();
        blocks.removeIf(s -> store.at(s.getLocation()) != from);
    }
}
