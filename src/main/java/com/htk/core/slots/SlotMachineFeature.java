package com.htk.core.slots;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Glyphs;
import com.htk.core.common.Messages;
import com.htk.core.common.Text;
import com.htk.core.economy.Economy;
import com.htk.core.economy.EconomyFeature;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Shared, fixed-facing floating slot boards anchored to a placeable lodestone. */
public final class SlotMachineFeature implements Feature, Listener, TabExecutor {
    private static final Material BASE = Material.LODESTONE;
    private static final Material[] SYMBOLS = {Material.EMERALD, Material.GOLD_INGOT, Material.DIAMOND, Material.NETHER_STAR};
    private final ServerCore plugin;
    private final EconomyFeature coins;
    private final NamespacedKey itemKey, machinesKey, displayKey;
    private final Map<Position, Machine> machines = new HashMap<>();
    private BukkitTask task;

    private record Position(UUID world, int x, int y, int z) {
        static Position of(Block b) { return new Position(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ()); }
    }
    private static final class Spin {
        final UUID player;
        final String name;
        final long stake, payout;
        final int multiplier;
        final int[] symbols;
        int ticks;
        boolean announced;
        Spin(Player p, long stake, int multiplier) {
            player = p.getUniqueId(); name = p.getName(); this.stake = stake; this.multiplier = multiplier;
            payout = SlotRules.payout(stake, multiplier);
            symbols = SlotRules.symbols(multiplier, ThreadLocalRandom.current().nextInt(4));
        }
    }
    private static final class Machine {
        final Block block;
        final int direction;
        final List<Display> entities = new ArrayList<>();
        final ItemDisplay[][] reels = new ItemDisplay[3][3];
        final boolean[] stopped = new boolean[3];
        TextDisplay status;
        Spin spin;
        Machine(Block block, int direction) { this.block = block; this.direction = direction; }
    }

    public SlotMachineFeature(ServerCore plugin, EconomyFeature coins) {
        this.plugin = plugin; this.coins = coins;
        itemKey = new NamespacedKey(plugin, "slot_machine_item");
        machinesKey = new NamespacedKey(plugin, "slot_machines");
        displayKey = new NamespacedKey(plugin, "slot_display");
    }

    @Override public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        var command = plugin.getCommand("slotmachine");
        if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
        for (World world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks()) load(chunk);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }
    @Override public void reload() { }
    @Override public void disable() {
        if (task != null) task.cancel();
        for (Machine m : machines.values()) { announce(m); removeDisplay(m); }
        machines.clear();
    }

    public boolean isMachine(Block b) {
        if (b.getType() != BASE) return false;
        int packed = SlotRules.pack(b.getX(), b.getY(), b.getZ());
        int[] entries = entries(b.getChunk());
        for (int i = 0; i + 1 < entries.length; i += 2) if (entries[i] == packed) return true;
        return false;
    }
    private int[] entries(Chunk chunk) {
        int[] entries = chunk.getPersistentDataContainer().get(machinesKey, PersistentDataType.INTEGER_ARRAY);
        return entries == null ? new int[0] : entries;
    }
    private void remember(Block b, Integer direction) {
        int packed = SlotRules.pack(b.getX(), b.getY(), b.getZ());
        List<Integer> next = new ArrayList<>();
        int[] old = entries(b.getChunk());
        for (int i = 0; i + 1 < old.length; i += 2)
            if (old[i] != packed) { next.add(old[i]); next.add(old[i + 1]); }
        if (direction != null) { next.add(packed); next.add(direction); }
        if (next.isEmpty()) b.getChunk().getPersistentDataContainer().remove(machinesKey);
        else b.getChunk().getPersistentDataContainer().set(machinesKey, PersistentDataType.INTEGER_ARRAY,
                next.stream().mapToInt(Integer::intValue).toArray());
    }
    private boolean isItem(ItemStack item) {
        return item != null && item.getType() == BASE && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }
    private ItemStack item(int count) {
        ItemStack item = new ItemStack(BASE, count);
        var meta = item.getItemMeta();
        meta.displayName(Text.mm("<gold><bold>슬롯 머신").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(null);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        if (plugin.resourcePackEnabled()) meta.setItemModel(Glyphs.model("slot_machine"));
        item.setItemMeta(meta); return item;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void checkSpace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        // Reserve the visual upper half, while keeping a single ordinary block for recovery.
        if (isMachine(b.getRelative(org.bukkit.block.BlockFace.DOWN))) {
            e.setCancelled(true); Messages.send(e.getPlayer(), "슬롯 머신 윗부분은 비워 주세요."); return;
        }
        if (isItem(e.getItemInHand()) && (b.getY() + 1 >= b.getWorld().getMaxHeight()
                || !b.getRelative(org.bukkit.block.BlockFace.UP).getType().isAir())) {
            e.setCancelled(true); Messages.send(e.getPlayer(), "슬롯 머신은 높이 2칸의 빈 공간이 필요해요.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (isItem(e.getItemInHand())) {
            int direction = Math.floorMod(Math.round((e.getPlayer().getYaw() + 180) / 90f), 4);
            remember(b, direction);
            Machine m = new Machine(b, direction);
            machines.put(Position.of(b), m); show(m);
        } else if (b.getType() == BASE) remember(b, null);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void preventBusyBreak(BlockBreakEvent e) {
        Machine m = machines.get(Position.of(e.getBlock()));
        if (isMachine(e.getBlock()) && m != null && m.spin != null) {
            e.setCancelled(true); Messages.send(e.getPlayer(), "슬롯이 끝난 뒤 회수해 주세요.");
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void broken(BlockBreakEvent e) {
        if (!isMachine(e.getBlock())) return;
        Machine m = machines.remove(Position.of(e.getBlock()));
        if (m != null) { announce(m); removeDisplay(m); }
        remember(e.getBlock(), null); e.setDropItems(false);
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE)
            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(.5, .5, .5), item(1));
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void entityExplosion(EntityExplodeEvent e) { e.blockList().removeIf(this::isMachine); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void blockExplosion(BlockExplodeEvent e) { e.blockList().removeIf(this::isMachine); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void extend(BlockPistonExtendEvent e) { if (e.getBlocks().stream().anyMatch(this::isMachine)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void retract(BlockPistonRetractEvent e) { if (e.getBlocks().stream().anyMatch(this::isMachine)) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void use(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || b == null || !isMachine(b)) return;
        boolean denied = e.useInteractedBlock() == Event.Result.DENY;
        e.setCancelled(true);
        if (denied || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) {
            Messages.send(p, "서바이벌·모험 모드에서 코인을 사용할 수 있어요."); return;
        }
        Machine m = machines.get(Position.of(b));
        if (m == null) { load(b.getChunk()); m = machines.get(Position.of(b)); }
        if (m == null) return;
        if (m.spin != null) { Messages.send(p, "지금 사용 중이에요. 결과 표시가 끝나면 돌릴 수 있어요."); return; }
        for (Machine other : machines.values()) if (other.spin != null && other.spin.player.equals(p.getUniqueId())) {
            Messages.send(p, "진행 중인 슬롯의 결과 표시를 기다려 주세요."); return;
        }
        ItemStack original = p.getInventory().getItemInMainHand().clone();
        long stake = coins.goldAmount(original);
        if (stake <= 0) { Messages.send(p, "코인을 주손에 들고 우클릭하세요. /money <금액> 으로 꺼낼 수 있어요."); return; }
        if (!SlotRules.canPlay(stake, coins.economy().balance(p.getUniqueId()))) {
            Messages.send(p, "금액이 너무 커서 최대 보상을 안전하게 지급할 수 없어요. 작은 코인을 사용해 주세요."); return;
        }
        // Recreate removed displays before charging, so display cancellation never consumes a coin.
        if (m.entities.isEmpty() || m.entities.stream().anyMatch(d -> !d.isValid())) { removeDisplay(m); show(m); }
        Spin spin = new Spin(p, stake, SlotRules.multiplier(ThreadLocalRandom.current().nextInt(100)));
        int slot = p.getInventory().getHeldItemSlot();
        try {
            boolean paid = SlotPayment.attempt(() -> {
                ItemStack remaining = original.clone(); remaining.setAmount(original.getAmount() - 1);
                p.getInventory().setItem(slot, remaining.getAmount() == 0 ? null : remaining);
                p.saveData();
            }, () -> coins.economy().settleSlotCoin(p.getUniqueId(), spin.payout), () -> {
                p.getInventory().setItem(slot, original); p.saveData();
            });
            if (!paid) { Messages.send(p, "저장에 실패해 코인을 돌려드렸어요. 잠시 뒤 다시 시도해 주세요."); return; }
        } catch (RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "슬롯 코인 처리 실패", failure);
            Messages.send(p, "코인 처리 중 오류가 발생했어요. 관리자에게 알려 주세요."); return;
        }
        // Settlement is durable before animation: quit, unload and shutdown cannot lose a prize.
        m.spin = spin; Arrays.fill(m.stopped, false);
        m.status.text(Text.mm("<yellow>" + spin.name + " · " + Economy.format(stake) + " · 회전 중"));
        Messages.send(p, Text.mm("<gold>슬롯 시작! <gray>코인 1개 · " + Economy.format(stake)));
        sound(m, "minecraft:block.note_block.chime", .7f, 1.2f);
    }

    private void tick() {
        Iterator<Machine> iterator = machines.values().iterator();
        while (iterator.hasNext()) {
            Machine m = iterator.next();
            if (!m.block.getWorld().isChunkLoaded(m.block.getX() >> 4, m.block.getZ() >> 4) || !isMachine(m.block)) {
                announce(m); removeDisplay(m); iterator.remove(); continue;
            }
            if (m.spin == null) continue;
            Spin s = m.spin; s.ticks += 2;
            for (int reel = 0; reel < 3; reel++) {
                int stop = SlotRules.stopTick(reel);
                if (s.ticks >= stop) {
                    if (!m.stopped[reel]) {
                        rest(m, reel, s.symbols[reel]); m.stopped[reel] = true;
                        sound(m, "minecraft:block.note_block.hat", .7f, 1f + reel * .2f);
                    }
                } else {
                    double t = (double) s.ticks / stop;
                    double distance = (9 + reel * 2) * (1 - Math.pow(1 - t, 2));
                    for (int row = 0; row < 3; row++) {
                        double progress = distance + row;
                        float offset = (float) ((progress % 3) - 1.5);
                        float scale = Math.clamp((.69f - Math.abs(offset)) / .24f, 0f, 1f) * .82f;
                        ItemDisplay d = m.reels[reel][row];
                        d.setItemStack(new ItemStack(SYMBOLS[Math.floorMod((int) Math.floor(progress), 4)]));
                        boardTransform(m, d, (reel - 1) * 1.3f, 3.6f - offset, .08f, scale, scale, scale);
                    }
                }
            }
            if (s.ticks < SlotRules.SPIN_TICKS && s.ticks % 8 == 0)
                sound(m, "minecraft:block.note_block.hat", .22f, 1.8f);
            if (s.ticks >= SlotRules.SPIN_TICKS && !s.announced) {
                announce(m);
                m.status.text(Text.mm(s.multiplier == 0 ? "<gray>꽝 · 다음 기회에!"
                        : "<gold><bold>" + s.multiplier + "배 당첨! <yellow>" + Economy.format(s.payout)));
                sound(m, s.multiplier == 0 ? "minecraft:block.note_block.bass" : "minecraft:entity.player.levelup", .8f, 1f);
            }
            if (s.ticks >= SlotRules.SPIN_TICKS + SlotRules.RESULT_TICKS) { m.spin = null; idle(m); }
        }
    }
    private void announce(Machine m) {
        Spin s = m.spin;
        if (s == null || s.announced) return;
        s.announced = true;
        Player p = Bukkit.getPlayer(s.player);
        if (p != null) Messages.send(p, Text.mm(s.multiplier == 0
                ? "<gray>슬롯 꽝 · 사용 " + Economy.format(s.stake)
                : "<gold>슬롯 " + s.multiplier + "배 당첨! <yellow>총 " + Economy.format(s.payout) + " <gray>잔액에 지급했어요."));
        plugin.getLogger().info("슬롯 결과: " + s.player + " stake=" + s.stake + " multiplier=" + s.multiplier + " payout=" + s.payout);
    }
    private void idle(Machine m) {
        m.status.text(Text.mm("<yellow>코인을 들고 아래 블록 우클릭"));
    }
    private void sound(Machine m, String sound, float volume, float pitch) {
        m.block.getWorld().playSound(m.block.getLocation().add(.5, 2, .5), sound, volume, pitch);
    }

    private void load(Chunk chunk) {
        // Only our transient displays are removed; block records live in the chunk PDC.
        for (Entity e : chunk.getEntities()) if (e.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE)) e.remove();
        Iterator<Map.Entry<Position, Machine>> iterator = machines.entrySet().iterator();
        while (iterator.hasNext()) {
            Machine m = iterator.next().getValue();
            if (inChunk(m, chunk)) { announce(m); removeDisplay(m); iterator.remove(); }
        }
        int[] entries = entries(chunk);
        for (int i = 0; i + 1 < entries.length; i += 2) {
            int packed = entries[i], y = (packed >>> 8) - 4096;
            if (y < chunk.getWorld().getMinHeight() || y >= chunk.getWorld().getMaxHeight()) continue;
            Block b = chunk.getBlock(packed & 15, y, (packed >>> 4) & 15);
            if (b.getType() != BASE) { remember(b, null); continue; }
            Machine m = new Machine(b, Math.floorMod(entries[i + 1], 4));
            machines.put(Position.of(b), m); show(m);
        }
    }
    private boolean inChunk(Machine m, Chunk c) {
        return m.block.getWorld().equals(c.getWorld()) && (m.block.getX() >> 4) == c.getX() && (m.block.getZ() >> 4) == c.getZ();
    }
    @EventHandler public void chunkLoad(ChunkLoadEvent e) { load(e.getChunk()); }
    @EventHandler public void worldLoad(WorldLoadEvent e) { for (Chunk c : e.getWorld().getLoadedChunks()) load(c); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void worldUnload(WorldUnloadEvent e) {
        var it = machines.values().iterator();
        while (it.hasNext()) {
            Machine m = it.next();
            if (m.block.getWorld().equals(e.getWorld())) { announce(m); removeDisplay(m); it.remove(); }
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chunkUnload(ChunkUnloadEvent e) {
        var it = machines.values().iterator();
        while (it.hasNext()) { Machine m = it.next(); if (inChunk(m, e.getChunk())) { announce(m); removeDisplay(m); it.remove(); } }
    }
    private void removeDisplay(Machine m) { for (Display d : m.entities) d.remove(); m.entities.clear(); }
    private Quaternionf rotation(Machine m) { return new Quaternionf().rotateY((float) Math.toRadians(-m.direction * 90)); }
    private void transform(Machine m, Display d, float x, float y, float z, float sx, float sy, float sz) {
        Vector3f translation = new Vector3f(x, y, z).rotate(rotation(m));
        d.setInterpolationDelay(0);
        d.setTransformation(new Transformation(translation, rotation(m), new Vector3f(sx, sy, sz), new Quaternionf()));
    }
    private void boardTransform(Machine m, Display d, float x, float y, float z, float sx, float sy, float sz) {
        transform(m, d, x, y + 1, z, sx, sy, sz);
    }
    private <T extends Display> T display(Machine m, Class<T> type) {
        T d = m.block.getWorld().spawn(m.block.getLocation().add(.5, 0, .5), type, e -> {
            e.setPersistent(false); e.setInvulnerable(true); e.setGravity(false); e.setSilent(true);
            e.setBillboard(Display.Billboard.FIXED); e.setBrightness(new Display.Brightness(15, 15));
            e.setViewRange(1.25f); e.setDisplayWidth(6); e.setDisplayHeight(8);
            e.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 1);
        });
        m.entities.add(d); return d;
    }
    private void panel(Machine m, Material material, float x, float y, float z, float w, float h, float depth) {
        BlockDisplay d = display(m, BlockDisplay.class); d.setBlock(material.createBlockData());
        boardTransform(m, d, x, y, z, w, h, depth);
    }
    private void panelCabinet(Machine m, Material material, float x, float y, float z, float w, float h, float depth) {
        BlockDisplay d = display(m, BlockDisplay.class); d.setBlock(material.createBlockData());
        transform(m, d, x, y, z, w, h, depth);
    }
    private TextDisplay label(Machine m, String text, float y, float scale) {
        TextDisplay d = display(m, TextDisplay.class);
        d.text(Text.mm(text)); d.setAlignment(TextDisplay.TextAlignment.CENTER);
        d.setDefaultBackground(false); d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        d.setShadowed(true); d.setSeeThrough(false); d.setLineWidth(800);
        boardTransform(m, d, 0, y, .18f, scale, scale, scale); return d;
    }
    private void show(Machine m) {
        try {
            if (plugin.resourcePackEnabled()) {
                ItemDisplay cabinet = display(m, ItemDisplay.class);
                ItemStack model = new ItemStack(BASE);
                var meta = model.getItemMeta(); meta.setItemModel(Glyphs.model("slot_machine")); model.setItemMeta(meta);
                cabinet.setItemStack(model); cabinet.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                transform(m, cabinet, 0, .5f, 0, 1, 1, 1);
            } else {
                // A compact brass/red cabinet remains visible when the pack is disabled.
                panelCabinet(m, Material.RED_TERRACOTTA, -.5f, 1, -.4f, 1, .85f, .8f);
                panelCabinet(m, Material.GOLD_BLOCK, -.5f, 1.85f, -.4f, 1, .15f, .8f);
            }
            panel(m, Material.BLACK_CONCRETE, -2.3f, 1.45f, -.2f, 4.6f, 3.6f, .15f);
            panel(m, Material.YELLOW_CONCRETE, -2.42f, 1.33f, -.2f, 4.84f, .12f, .28f);
            panel(m, Material.YELLOW_CONCRETE, -2.42f, 5.05f, -.2f, 4.84f, .12f, .28f);
            panel(m, Material.YELLOW_CONCRETE, -2.42f, 1.45f, -.2f, .12f, 3.6f, .28f);
            panel(m, Material.YELLOW_CONCRETE, 2.3f, 1.45f, -.2f, .12f, 3.6f, .28f);
            label(m, "<gold><bold>GOLDEN SLOT", 4.55f, 1.15f);
            for (int reel = 0; reel < 3; reel++) {
                panel(m, Material.GRAY_CONCRETE, (reel - 1) * 1.3f - .56f, 2.88f, -.04f, 1.12f, 1.45f, .05f);
                for (int row = 0; row < 3; row++) {
                    ItemDisplay d = display(m, ItemDisplay.class);
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI); d.setInterpolationDuration(2);
                    m.reels[reel][row] = d;
                }
                rest(m, reel, reel);
            }
            m.status = label(m, "", 2.48f, .65f); idle(m);
            label(m, "<gray>코인 1개 = 한 판 · 당첨금은 잔액으로 지급", 2.02f, .47f);
            label(m, "<green>에메랄드 ×2 25%  <gold>금 ×3 10%\n<aqua>다이아 ×4 5%  <white>별 ×5 1%  <gray>꽝 59%", 1.53f, .44f);
        } catch (RuntimeException failure) {
            removeDisplay(m); throw failure;
        }
    }
    private void rest(Machine m, int reel, int symbol) {
        for (int row = 0; row < 3; row++) {
            ItemDisplay d = m.reels[reel][row]; d.setItemStack(new ItemStack(SYMBOLS[symbol]));
            float scale = row == 1 ? .9f : 0;
            boardTransform(m, d, (reel - 1) * 1.3f, 3.6f, .08f, scale, scale, scale);
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("servercore.admin")) { Messages.send(sender, Text.mm(plugin.msg("no-permission"))); return true; }
        if (args.length > 2) { Messages.send(sender, "사용법: /slotmachine [1~64] [player]"); return true; }
        int count = 1;
        try { if (args.length > 0) count = Integer.parseInt(args[0]); }
        catch (NumberFormatException ignored) { count = 0; }
        if (count < 1 || count > 64) { Messages.send(sender, "개수는 1~64로 입력해 주세요."); return true; }
        Player target = args.length == 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
        if (target == null) { Messages.send(sender, "받을 플레이어를 지정해 주세요. /slotmachine [1~64] [player]"); return true; }
        target.getInventory().addItem(item(count)).values().forEach(left -> target.getWorld().dropItem(target.getLocation(), left));
        Messages.send(sender, "슬롯 머신 " + count + "개를 " + target.getName() + "에게 지급했어요."); return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("servercore.admin")) return List.of();
        if (args.length == 1) return List.of("1", "4", "16");
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}
