package com.htk.core.merchant;

import com.htk.core.ServerCore;
import com.htk.core.common.*;
import com.htk.core.economy.Economy;
import com.htk.core.tools.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import java.util.*;

public final class WeaponMerchantFeature implements Feature,Listener,TabExecutor {
    private final ServerCore plugin;
    private final Economy economy;
    private final VillageToolFeature tools;
    private final NamespacedKey npcKey,visualKey,yawKey,introKey;
    private final EnumMap<ToolType,Long> prices=new EnumMap<>(ToolType.class);
    private final Map<UUID,Long> clicks=new HashMap<>();
    private final Map<UUID,BukkitTask> conversations=new HashMap<>();
    private final Map<UUID,Long> interactions=new HashMap<>();
    private static final List<String> INTRO=List.of(
        "…이런 곳까지 발길이 닿다니. 길을 잃은 건가, 낯선 이여?",
        "한때는 내 망치가 빚은 물건을 전설이라 불렀지. 이제는 내 이름조차 기억하는 이가 없네.",
        "힘은 예전 같지 않아도, 쓸 만한 도구를 고르는 눈까지 녹슬지는 않았네.",
        "마침 남겨 둔 물건들이 있군. 자네의 여정에 쓸 것이 있다면 하나 골라 보게."
    );
    private boolean active;
    public WeaponMerchantFeature(ServerCore plugin,Economy economy,VillageToolFeature tools) {
        this.plugin=plugin;this.economy=economy;this.tools=tools;
        npcKey=new NamespacedKey(plugin,"weapon_merchant_npc");
        visualKey=new NamespacedKey(plugin,"weapon_merchant_visual");
        yawKey=new NamespacedKey(plugin,"weapon_merchant_yaw");
        introKey=new NamespacedKey(plugin,"weapon_merchant_intro_done");
    }
    @Override public void enable() {
        reload();active=true;Bukkit.getPluginManager().registerEvents(this,plugin);
        for(String name:List.of("weaponmerchantnpc","weaponshop")) {
            var cmd=plugin.getCommand(name);if(cmd!=null){cmd.setExecutor(this);cmd.setTabCompleter(this);}
        }
        for(World w:Bukkit.getWorlds())for(Chunk c:w.getLoadedChunks())repair(c);
    }
    @Override public void reload() {
        prices.clear();for(ToolType t:ToolType.values()) prices.put(t,1000L);
        prices.put(ToolType.HOE,500L);prices.put(ToolType.WATERING_CAN,500L);
    }
    @Override public void disable() {
        active=false;clicks.clear();interactions.clear();
        conversations.values().forEach(BukkitTask::cancel);conversations.clear();
        for(Player p:Bukkit.getOnlinePlayers()) if(p.getOpenInventory().getTopInventory().getHolder() instanceof ShopGui)p.closeInventory();
    }
    private boolean isNpc(Entity e) { return e.getPersistentDataContainer().has(npcKey,PersistentDataType.BYTE); }
    private boolean canBuy(Player p) {
        var land=plugin.feature(com.htk.core.land.LandFeature.class);
        return land!=null&&land.landOf(p.getUniqueId())!=null;
    }
    private void open(Player p) {
        if(!canBuy(p)){Messages.send(p,Text.mm("<yellow>집터를 먼저 설치해야 도구를 구매할 수 있어요."));return;}
        if(active)new ShopGui().open(p);
    }
    private void meet(Player p,Entity npc) {
        if(!active || conversations.containsKey(p.getUniqueId()))return;
        long now=System.nanoTime();Long last=interactions.put(p.getUniqueId(),now);
        if(last!=null && now-last<350_000_000L)return;
        if(p.getPersistentDataContainer().has(introKey,PersistentDataType.BYTE)){open(p);return;}
        p.closeInventory();
        int[] line={0};
        BukkitTask task=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            if(!active || !p.isOnline() || p.isDead() || !npc.isValid()
                    || !p.getWorld().equals(npc.getWorld()) || p.getLocation().distanceSquared(npc.getLocation())>64){
                stopConversation(p.getUniqueId());return;
            }
            if(line[0]<INTRO.size()){
                Messages.send(p,Text.mm("<#d8ad73><bold>잊혀진 명장</bold> <dark_gray>» <#e0d5c5>"+INTRO.get(line[0]++)));
                p.playSound(p.getLocation(),Sound.ENTITY_VILLAGER_AMBIENT,SoundCategory.PLAYERS,.35f,.7f);
            }else{
                p.getPersistentDataContainer().set(introKey,PersistentDataType.BYTE,(byte)1);
                stopConversation(p.getUniqueId());open(p);
            }
        },0L,70L);
        conversations.put(p.getUniqueId(),task);
    }
    private void stopConversation(UUID id){
        BukkitTask task=conversations.remove(id);if(task!=null)task.cancel();
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEntityEvent e) {
        if(!isNpc(e.getRightClicked()))return;e.setCancelled(true);
        if(e.getHand()==EquipmentSlot.HAND)meet(e.getPlayer(),e.getRightClicked());
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void interactAt(PlayerInteractAtEntityEvent e){interact(e);}
    @EventHandler public void damage(EntityDamageEvent e){if(isNpc(e.getEntity()))e.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent e){
        UUID id=e.getPlayer().getUniqueId();clicks.remove(id);interactions.remove(id);stopConversation(id);
    }
    @EventHandler public void chunkLoad(ChunkLoadEvent e){Bukkit.getScheduler().runTask(plugin,()->{if(e.getChunk().isLoaded())repair(e.getChunk());});}
    private final class ShopGui extends Gui {
        // Five equally spaced displays: tools/make_blacksmith_art.py columns 0,2,4,6,8.
        private final Map<Integer,ToolType> slots=Map.of(9,ToolType.PICKAXE,11,ToolType.AXE,13,ToolType.FISHING_ROD,15,ToolType.HOE,17,ToolType.WATERING_CAN);
        private final EnumMap<ToolType,Long> shown=new EnumMap<>(prices);
        ShopGui(){
            boolean pack=plugin.resourcePackEnabled();
            var title=pack
                ? net.kyori.adventure.text.Component.text(Glyphs.space(-8)+"\uE074"+Glyphs.space(-1)+"\uE075"
                    +Glyphs.space(-177)+"\uE076"+Glyphs.space(-1)+"\uE077")
                    .font(net.kyori.adventure.key.Key.key("servercore:forge"))
                    .color(net.kyori.adventure.text.format.NamedTextColor.WHITE)
                : Text.mm("<#d8ad73><bold>잊혀진 명장 · 장비 구매");
            create(3,title);
            ItemStack pane=Items.hideTooltip(Items.of(pack?Material.PAPER:Material.BLACK_STAINED_GLASS_PANE,
                1," ",List.of(),pack?Glyphs.model("invisible"):null));
            for(int i=0;i<27;i++)inventory.setItem(i,pane);
            for(var entry:slots.entrySet()) {
                ItemStack icon=tools.createTool(entry.getValue(),0);var meta=icon.getItemMeta();
                var lore=new ArrayList<net.kyori.adventure.text.Component>();if(meta.lore()!=null)lore.addAll(meta.lore());
                long price=shown.get(entry.getValue());lore.add(Text.mm(price>0?"<#ffd577>가격: "+Economy.format(price):"<red>판매 중지"));
                meta.lore(lore);icon.setItemMeta(meta);inventory.setItem(entry.getKey(),icon);
            }
            inventory.setItem(22,Items.of(pack?Material.PAPER:Material.BARRIER,1,"<#e2bc85>돌아가기",
                List.of(),pack?Glyphs.model("forge_button"):null));
        }
        @Override public void onClick(Player p,int slot,InventoryClickEvent event){
            if(!active || (event.getClick()!=ClickType.LEFT && event.getClick()!=ClickType.RIGHT))return;
            if(slot==22){p.closeInventory();return;}
            ToolType type=slots.get(slot);if(type==null)return;
            long now=System.nanoTime();Long last=clicks.get(p.getUniqueId());if(last!=null && now-last<350_000_000L)return;clicks.put(p.getUniqueId(),now);
            if(!Objects.equals(shown.get(type),prices.get(type))){open(p);Messages.send(p,Text.mm("<yellow>가격이 바뀌었어요. 확인 후 다시 눌러 주세요."));return;}
            if(!canBuy(p)){p.closeInventory();Messages.send(p,Text.mm("<red>집터를 먼저 설치해 주세요."));return;}
            ItemStack product=tools.createTool(type,0);economy.ensureAccount(p.getUniqueId());
            var result=PurchaseTransaction.buy(new PurchaseTransaction.Buyer(){
                public boolean hasSpace(){
                    for(ItemStack i:p.getInventory().getStorageContents())if(i==null || i.getType().isAir() || (i.isSimilar(product) && i.getAmount()<Math.min(i.getMaxStackSize(),p.getInventory().getMaxStackSize())))return true;
                    return false;
                }
                public boolean withdraw(long value){return economy.withdraw(p.getUniqueId(),value);}
                public void refund(long value){economy.deposit(p.getUniqueId(),value);}
                public boolean deliver(){return p.getInventory().addItem(product).isEmpty();}
            },shown.get(type));
            String message=switch(result){
                case SUCCESS -> "<green>"+type.korean+" 0강을 구매했어요. <gray>잔액: "+Economy.format(economy.balance(p.getUniqueId()));
                case FULL -> "<red>장비를 받을 가방 공간이 필요해요.";
                case INSUFFICIENT -> "<red>골드가 부족해요.";
                case DISABLED -> "<red>지금은 판매하지 않는 장비예요.";
                case DELIVERY_FAILED -> "<red>장비를 지급하지 못해 골드를 돌려드렸어요.";
            };
            Messages.send(p,Text.mm(message));
            if(result==PurchaseTransaction.Result.SUCCESS)p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_HARP,SoundCategory.PLAYERS,.3f,1.2f);
        }
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(!sender.hasPermission("servercore.admin")){Messages.send(sender,Text.mm("<red>관리자만 사용할 수 있어요."));return true;}
        if(cmd.getName().equals("weaponshop")){
            Player target=args.length==0?(sender instanceof Player p?p:null):Bukkit.getPlayerExact(args[0]);
            if(target==null)Messages.send(sender,"접속 중인 플레이어를 지정해 주세요.");else open(target);return true;
        }
        if(!(sender instanceof Player p)){Messages.send(sender,"게임 안에서 실행해 주세요.");return true;}
        if(args.length==1 && args[0].equalsIgnoreCase("create")){
            Location at=p.getLocation();at.setX(at.getBlockX()+.5);at.setZ(at.getBlockZ()+.5);at.setYaw(at.getYaw()+180);at.setPitch(0);
            Messages.send(p,Text.mm(spawnNpc(at)!=null?"<green>잊혀진 명장을 세웠어요. 우클릭으로 장비를 구매해요.":"<red>NPC 생성이 차단됐어요. 월드 보호 설정을 확인하세요."));
        }else if(args.length==1 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("face"))){
            Entity npc=p.getWorld().getNearbyEntities(p.getLocation(),5,5,5).stream().filter(this::isNpc).filter(e->e.getLocation().distanceSquared(p.getLocation())<=25)
                .min(Comparator.comparingDouble(e->e.getLocation().distanceSquared(p.getLocation()))).orElse(null);
            if(npc==null)Messages.send(p,Text.mm("<red>5블록 안에 잊혀진 명장이 없어요."));
            else if(args[0].equalsIgnoreCase("face")){npc.getPersistentDataContainer().set(yawKey,PersistentDataType.FLOAT,p.getLocation().getYaw()+180);ensureVisual(npc);Messages.send(p,Text.mm("<green>방향을 현재 시선의 반대로 맞췄어요."));}
            else{removeVisual(npc);npc.remove();Messages.send(p,Text.mm("<green>잊혀진 명장을 제거했어요."));}
        }else Messages.send(p,"/weaponmerchantnpc create|remove|face");
        return true;
    }
    private Interaction spawnNpc(Location at) {
        Interaction root = at.getWorld().spawn(at, Interaction.class, e -> {
            e.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte)1);
            e.getPersistentDataContainer().set(yawKey, PersistentDataType.FLOAT, at.getYaw());
            e.setInteractionWidth(.9f); e.setInteractionHeight(2.25f); e.setResponsive(true);
            e.setPersistent(true); e.setInvulnerable(true); e.setSilent(true);
        });
        if (!root.isValid()) return null;
        if (!ensureVisual(root)) { root.remove(); return null; }
        return root;
    }
    private boolean ensureVisual(Entity root) {
        com.htk.core.npc.NpcModelService.setVisibleName(root, "잊혀진 명장");
        String id = root.getUniqueId().toString();
        List<ItemDisplay> found = root.getWorld().getNearbyEntities(root.getLocation(), 1, 1, 1).stream()
                .filter(e -> e instanceof ItemDisplay && id.equals(e.getPersistentDataContainer().get(visualKey, PersistentDataType.STRING)))
                .map(e -> (ItemDisplay)e).toList();
        if (!found.isEmpty()) {
            applyFacing(root, found.getFirst());
            for (int i=1;i<found.size();i++) found.get(i).remove();
            return true;
        }
        Location at = root.getLocation(); at.setYaw(0); at.setPitch(0);
        ItemDisplay visual = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.getPersistentDataContainer().set(visualKey, PersistentDataType.STRING, id);
            ItemStack model = new ItemStack(Material.PAPER);
            var meta = model.getItemMeta(); meta.setItemModel(Glyphs.model("weapon_merchant")); model.setItemMeta(meta);
            d.setItemStack(model); d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(0,.5625f,0), new Quaternionf(), new Vector3f(1.125f), new Quaternionf()));
            d.setPersistent(true); d.setInvulnerable(true); d.setSilent(true); d.setGravity(false);
            d.setViewRange(1.5f);
        });
        if (visual.isValid()) applyFacing(root, visual);
        return visual.isValid();
    }
    private void applyFacing(Entity root, ItemDisplay visual) {
        // Interaction의 yaw 대신 PDC 방향과 Display 변환을 사용해 재시작 후에도 유지.
        float yaw = root.getPersistentDataContainer().getOrDefault(yawKey, PersistentDataType.FLOAT, 0f);
        Location at = root.getLocation(); at.setYaw(0); at.setPitch(0);
        visual.teleport(at);
        visual.setBillboard(Display.Billboard.FIXED);
        // 모델 정면은 -Z, Bukkit yaw 0은 +Z이며 양의 yaw는 -X를 향한다.
        visual.setTransformation(new Transformation(new Vector3f(0,.5625f,0),
                com.htk.core.npc.NpcFacing.rotation(yaw),
                new Vector3f(1.125f), new Quaternionf()));
    }
    private void removeVisual(Entity root) {
        String id = root.getUniqueId().toString();
        for (Entity e : root.getWorld().getNearbyEntities(root.getLocation(),1,1,1))
            if (id.equals(e.getPersistentDataContainer().get(visualKey,PersistentDataType.STRING))) e.remove();
    }
    private void repair(Chunk chunk) {
        for (Entity e : chunk.getEntities()) {
            if (isNpc(e)) {
                if (e instanceof Interaction) ensureVisual(e);
            } else if (e instanceof ItemDisplay) {
                String id=e.getPersistentDataContainer().get(visualKey,PersistentDataType.STRING);
                if (id != null) {
                    try { Entity root=Bukkit.getEntity(UUID.fromString(id)); if (root==null || !isNpc(root)) e.remove(); }
                    catch (IllegalArgumentException ignored) { e.remove(); }
                }
            }
        }
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String alias,String[] args){
        if(!s.hasPermission("servercore.admin"))return List.of();
        if(c.getName().equals("weaponshop"))return args.length==1?null:List.of();
        return args.length==1?List.of("create","remove","face").stream().filter(v->v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList():List.of();
    }
}
