package com.htk.core.ranking;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.stream.IntStream;

public final class RankingFeature implements Feature,Listener,TabExecutor {
    private final ServerCore plugin;
    private final RankingStore store;
    private final NamespacedKey placedKey,legacyKey,displayKey;
    private final EnumMap<RankingLedger.Category,Map<Material,Long>> points=new EnumMap<>(RankingLedger.Category.class);
    private final Set<Material> mature=EnumSet.noneOf(Material.class);
    private final Map<BlockBreakEvent,Boolean> beforeBreak=new WeakHashMap<>();
    private final Map<String,TextDisplay> displays=new HashMap<>();
    private BukkitTask refresh,autosave;
    private boolean accept=true;
    private Set<String> worlds=Set.of();
    private static final Set<Material> GROWING=EnumSet.of(Material.SWEET_BERRY_BUSH,Material.CAVE_VINES,Material.CAVE_VINES_PLANT);
    public RankingFeature(ServerCore plugin) {
        this.plugin=plugin;store=new RankingStore(plugin.getDataFolder().toPath().resolve("ranking-data.yml"));
        placedKey=new NamespacedKey(plugin,"ranking_placed");legacyKey=new NamespacedKey(plugin,"placed");displayKey=new NamespacedKey(plugin,"ranking_board");
    }
    @Override public void enable() {
        reload();Bukkit.getPluginManager().registerEvents(this,plugin);
        for(String name:List.of("rankboard","ranking")) { var c=plugin.getCommand(name);if(c!=null){c.setExecutor(this);c.setTabCompleter(this);} }
        for(World w:Bukkit.getWorlds()) for(Chunk c:w.getLoadedChunks()) clean(c);
        updateBoards();
        refresh=Bukkit.getScheduler().runTaskTimer(plugin,this::updateBoards,100,100);
        autosave=Bukkit.getScheduler().runTaskTimer(plugin,store::save,1200,1200);
    }
    @Override public void reload() {
        var cfg=plugin.loadYaml("ranking.yml");cfg.setDefaults(plugin.loadDefaultYaml("ranking.yml"));
        cfg.options().copyDefaults(true);
        points.clear();mature.clear();
        worlds=new HashSet<>(cfg.getStringList("worlds"));
        for(var c:RankingLedger.Category.values()) {
            Map<Material,Long> table=new EnumMap<>(Material.class);
            var section=cfg.getConfigurationSection("points."+c.name().toLowerCase(Locale.ROOT));
            if(section!=null) for(String key:section.getKeys(false)) {
                Material m=Material.matchMaterial(key);long value=section.getLong(key);
                if(m!=null && value>0) table.put(m,value);
                else if(m==null) plugin.getLogger().warning("알 수 없는 순위 재료: "+key);
            }
            if(c==RankingLedger.Category.WOODCUTTING) {
                long value=Math.max(0,cfg.getLong("woodcutting-points",1));
                table.clear();
                if(value>0) for(Material log:Tag.LOGS.getValues()) table.put(log,value);
            }
            points.put(c,table);
        }
        for(String name:cfg.getStringList("mature-only")) { Material m=Material.matchMaterial(name);if(m!=null)mature.add(m); }
    }
    @Override public void disable() {
        accept=false;if(refresh!=null)refresh.cancel();if(autosave!=null)autosave.cancel();
        displays.values().forEach(Entity::remove);displays.clear();beforeBreak.clear();store.save();
    }
    private boolean eligible(Player p) { return accept && (p.getGameMode()==GameMode.SURVIVAL || p.getGameMode()==GameMode.ADVENTURE) && (worlds.isEmpty() || worlds.contains(p.getWorld().getName())); }
    private void award(Player p,Material m) {
        if(!eligible(p))return;
        for(var c:List.of(RankingLedger.Category.FARMING,RankingLedger.Category.MINING,RankingLedger.Category.WOODCUTTING))
            award(p,c,m);
    }
    private void award(Player p,RankingLedger.Category c,Material m) {
        if(eligible(p) && store.ledger.add(p.getUniqueId(),p.getName(),c,points.get(c).getOrDefault(m,0L)))store.dirty();
    }
    private boolean ranked(Material m) { return points.values().stream().anyMatch(t->t.containsKey(m)); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void place(BlockPlaceEvent e) {
        Block b=e.getBlockPlaced();if(ranked(b.getType()) && !mature.contains(b.getType()) && !GROWING.contains(b.getType())) mark(b,true);
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void snapshot(BlockBreakEvent e) { beforeBreak.put(e,marked(e.getBlock(),placedKey)||marked(e.getBlock(),legacyKey)); }
    @EventHandler(priority=EventPriority.MONITOR)
    public void broken(BlockBreakEvent e) {
        boolean placed=Boolean.TRUE.equals(beforeBreak.remove(e));if(e.isCancelled())return;
        Block b=e.getBlock();mark(b,false);
        if(placed || !e.isDropItems())return;
        if(b.getBlockData() instanceof org.bukkit.block.data.type.CaveVinesPlant vine && !vine.hasBerries())return;
        if(mature.contains(b.getType()) && (!(b.getBlockData() instanceof Ageable age) || age.getAge()<age.getMaximumAge()))return;
        award(e.getPlayer(),b.getType());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void harvest(PlayerHarvestBlockEvent e) {
        if(!e.getItemsHarvested().isEmpty())award(e.getPlayer(),e.getHarvestedBlock().getType());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void fish(PlayerFishEvent e) {
        if(e.getState()!=PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item item))return;
        var caught=item.getItemStack();
        // 손상된 일반 낚싯대는 잡동사니이며 마법 낚싯대만 보물로 인정.
        if(caught.getType()==Material.FISHING_ROD && caught.getEnchantments().isEmpty())return;
        award(e.getPlayer(),RankingLedger.Category.FISHING,caught.getType());
    }
    private static int packed(Block b) { return (b.getX()&15)|((b.getZ()&15)<<4)|((b.getY()+4096)<<8); }
    private boolean marked(Block b,NamespacedKey key) {
        int[] a=b.getChunk().getPersistentDataContainer().get(key,PersistentDataType.INTEGER_ARRAY);
        return a!=null && IntStream.of(a).anyMatch(v->v==packed(b));
    }
    private void mark(Block b,boolean add) {
        var pdc=b.getChunk().getPersistentDataContainer();int[] a=pdc.get(placedKey,PersistentDataType.INTEGER_ARRAY);if(a==null)a=new int[0];
        int value=packed(b);boolean found=IntStream.of(a).anyMatch(v->v==value);if(found==add)return;
        int[] updated=add?IntStream.concat(IntStream.of(a),IntStream.of(value)).toArray():IntStream.of(a).filter(v->v!=value).toArray();
        if(updated.length==0)pdc.remove(placedKey);else pdc.set(placedKey,PersistentDataType.INTEGER_ARRAY,updated);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void push(BlockPistonExtendEvent e) { move(e.getBlocks(),e.getDirection()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void pull(BlockPistonRetractEvent e) { move(e.getBlocks(),e.getDirection().getOppositeFace()); }
    private void move(List<Block> blocks,BlockFace direction) {
        List<Block> placed=blocks.stream().filter(b->marked(b,placedKey)||marked(b,legacyKey)).toList();
        placed.forEach(b->mark(b,false));placed.forEach(b->mark(b.getRelative(direction),true));
    }
    private net.kyori.adventure.text.Component content(RankingLedger.Category c) {
        var text=Text.mm("<#ffd577><bold>🏆 "+c.title()+" 누적 순위</bold>\n<gray>재료별 포인트 합산 · 전체 누적\n");
        var top=store.ledger.top(c,10);
        if(top.isEmpty())return text.append(Text.mm("\n<white>아직 기록이 없어요!\n<gray>지금부터 채집해 보세요."));
        long previous=-1;int rank=0;
        for(int i=0;i<top.size();i++) {
            var e=top.get(i);if(e.score(c)!=previous)rank=i+1;previous=e.score(c);
            String color=rank==1?"<#ffd577>":rank==2?"<#d7dfe8>":rank==3?"<#d9ad88>":"<white>";
            text=text.append(Text.mm("\n"+color+rank+"위  "))
                    .append(net.kyori.adventure.text.Component.text(e.name))
                    .append(Text.mm(" <#8dd8cf>"+String.format(Locale.US,"%,d",e.score(c))+"점"));
        }
        return text.append(Text.mm("\n\n<dark_gray>5초마다 갱신 · 동점은 같은 순위"));
    }
    private void clean(Chunk c) {
        for(Entity e:c.getEntities())if(e instanceof TextDisplay && e.getPersistentDataContainer().has(displayKey,PersistentDataType.STRING))e.remove();
    }
    @EventHandler public void chunkLoad(ChunkLoadEvent e) {
        Bukkit.getScheduler().runTask(plugin,()->{if(e.getChunk().isLoaded()){clean(e.getChunk());updateBoards();}});
    }
    private void updateBoards() {
        for(var b:store.boards.values()) {
            World w=Bukkit.getWorld(b.world());if(w==null || !w.isChunkLoaded(((int)Math.floor(b.x()))>>4,((int)Math.floor(b.z()))>>4))continue;
            TextDisplay d=displays.get(b.id());
            if(d==null || !d.isValid()) {
                d=w.spawn(new Location(w,b.x(),b.y(),b.z()),TextDisplay.class,t->{
                    t.getPersistentDataContainer().set(displayKey,PersistentDataType.STRING,b.id());t.setPersistent(false);t.setInvulnerable(true);
                    t.setBillboard(Display.Billboard.CENTER);t.setShadowed(true);t.setLineWidth(400);
                    t.setBackgroundColor(Color.fromARGB(160,20,25,32));t.setSeeThrough(false);t.setViewRange(1.5f);
                });displays.put(b.id(),d);
            }
            d.text(content(b.category()));
        }
    }
    @Override public boolean onCommand(CommandSender s,Command cmd,String label,String[] args) {
        if(cmd.getName().equals("ranking")) {
            var c=RankingLedger.Category.parse(args.length==0?"farming":args[0]);
            if(c==null){s.sendMessage("/ranking farming|mining|woodcutting|fishing");return true;}
            s.sendMessage(content(c));return true;
        }
        if(!s.hasPermission("servercore.admin")){s.sendMessage(Text.mm("<red>관리자만 사용할 수 있어요."));return true;}
        if(args.length==3 && args[0].equalsIgnoreCase("create") && s instanceof Player p) {
            String id=args[1];var c=RankingLedger.Category.parse(args[2]);
            if(!id.matches("[a-zA-Z0-9_-]{1,32}")||c==null){s.sendMessage("게시판 이름: 영문/숫자/_/- 1~32자, 분야: farming|mining|woodcutting|fishing");return true;}
            if(store.boards.containsKey(id)){s.sendMessage("이미 있는 게시판 이름이에요. 다른 이름을 사용하세요.");return true;}
            Location l=p.getLocation();store.boards.put(id,new RankingStore.Board(id,c,l.getWorld().getUID(),l.getX(),l.getY()+3,l.getZ()));
            store.dirty();store.save();updateBoards();s.sendMessage(Text.mm("<green>현재 위치 위에 "+c.title()+" 순위 게시판을 만들었어요."));
        } else if(args.length==2 && args[0].equalsIgnoreCase("remove")) {
            if(store.boards.remove(args[1])==null){s.sendMessage("해당 게시판이 없어요.");return true;}
            TextDisplay d=displays.remove(args[1]);if(d!=null)d.remove();store.dirty();store.save();s.sendMessage("게시판을 제거했어요. 누적 점수는 유지돼요.");
        } else if(args.length==1 && args[0].equalsIgnoreCase("list")) s.sendMessage("순위 게시판: "+String.join(", ",store.boards.keySet()));
        else s.sendMessage("/rankboard create <이름> farming|mining|woodcutting|fishing | remove <이름> | list");
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender s,Command cmd,String label,String[] args) {
        List<String> options=cmd.getName().equals("ranking")?List.of("farming","mining","woodcutting","fishing"):
            !s.hasPermission("servercore.admin")?List.of():args.length==1?List.of("create","remove","list"):
            args.length==2 && args[0].equalsIgnoreCase("remove")?new ArrayList<>(store.boards.keySet()):
            args.length==3 && args[0].equalsIgnoreCase("create")?List.of("farming","mining","woodcutting","fishing"):List.of();
        String last=args.length==0?"":args[args.length-1];return options.stream().filter(v->v.startsWith(last)).toList();
    }
}
