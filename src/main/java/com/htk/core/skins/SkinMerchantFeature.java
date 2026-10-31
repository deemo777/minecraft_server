package com.htk.core.skins;

import com.htk.core.ServerCore;
import com.htk.core.common.*;
import com.htk.core.economy.Economy;
import com.htk.core.tools.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** No dialogue: pick a weapon, buy a permanent skin, equip freely from its collection. */
public final class SkinMerchantFeature implements Feature,Listener,TabExecutor {
    private static final List<ToolType> KINDS=List.of(ToolType.DAGGER,ToolType.TACHI,ToolType.GREATSWORD);
    private static final List<Integer> DRAW_SLOTS=List.of(11,13,15),BOOK_SLOTS=List.of(29,31,33);
    private final ServerCore plugin;
    private final Economy economy;
    private final VillageToolFeature tools;
    private final NamespacedKey npcKey,skinKey;
    private final Map<UUID,Long> interactions=new HashMap<>();
    private boolean active;
    public SkinMerchantFeature(ServerCore plugin,Economy economy,VillageToolFeature tools){
        this.plugin=plugin;this.economy=economy;this.tools=tools;
        npcKey=new NamespacedKey(plugin,"skin_merchant_npc");skinKey=new NamespacedKey(plugin,"weapon_skin");
    }
    @Override public void enable(){
        active=true;Bukkit.getPluginManager().registerEvents(this,plugin);
        for(String name:List.of("skinmerchantnpc","skinshop")){
            var command=plugin.getCommand(name);if(command!=null){command.setExecutor(this);command.setTabCompleter(this);}
        }
    }
    @Override public void disable(){
        active=false;interactions.clear();
        for(Player player:Bukkit.getOnlinePlayers())if(player.getOpenInventory().getTopInventory().getHolder() instanceof SkinGui)player.closeInventory();
    }
    @Override public void reload(){}
    private boolean isNpc(Entity entity){return entity.getPersistentDataContainer().has(npcKey,PersistentDataType.BYTE);}
    @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEntityEvent event){
        if(!isNpc(event.getRightClicked()))return;
        event.setCancelled(true);
        if(!active || event.getHand()!=EquipmentSlot.HAND)return;
        Player player=event.getPlayer();long now=System.nanoTime();
        Long last=interactions.put(player.getUniqueId(),now);
        if(last!=null && now-last<350_000_000L)return;
        Bukkit.getScheduler().runTask(plugin,()->{if(active && player.isOnline())new ShopGui().open(player);});
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void interactAt(PlayerInteractAtEntityEvent event){interact(event);}
    @EventHandler public void damage(EntityDamageEvent event){if(isNpc(event.getEntity()))event.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent event){interactions.remove(event.getPlayer().getUniqueId());}
    private void transition(Player player,SkinGui from,Runnable action){
        Bukkit.getScheduler().runTask(plugin,()->{
            if(active && player.isOnline() && player.getOpenInventory().getTopInventory().getHolder()==from)action.run();
        });
    }
    private Component title(int first,String fallback){
        if(!plugin.resourcePackEnabled())return Text.mm("<#e5c88b><bold>"+fallback);
        return Component.text(Glyphs.space(-8)+(char)first+Glyphs.space(-1)+(char)(first+1)
            +Glyphs.space(-177)+(char)(first+2)+Glyphs.space(-1)+(char)(first+3))
            .font(Key.key("servercore:forge")).color(net.kyori.adventure.text.format.NamedTextColor.WHITE);
    }
    private ItemStack icon(String model,String name,List<String> lore){
        return Items.of(Material.DIAMOND_SWORD,1,name,lore,plugin.resourcePackEnabled()?Glyphs.model(model):null);
    }
    private abstract class SkinGui extends Gui {
        SkinGui(int rows,int glyph,String fallback){
            create(rows,title(glyph,fallback));
            ItemStack blank=Items.invisible(" ",List.of(),plugin.resourcePackEnabled());
            for(int slot=0;slot<rows*9;slot++)inventory.setItem(slot,blank);
        }
        boolean accepts(InventoryClickEvent event){return active && (event.getClick()==ClickType.LEFT || event.getClick()==ClickType.RIGHT);}
        ItemStack button(String name,List<String> lore){
            return plugin.resourcePackEnabled()?Items.of(Material.PAPER,1,name,lore,Glyphs.model("invisible"))
                :Items.of(Material.BOOK,1,name,lore,null);
        }
    }
    private final class ShopGui extends SkinGui {
        private boolean used;
        ShopGui(){
            super(4,0xE0B0,"스킨 상점 · 1회 100,000G");
            for(int index=0;index<KINDS.size();index++){
                ToolType type=KINDS.get(index);
                inventory.setItem(DRAW_SLOTS.get(index),icon(type.model,"<#eed8a3>"+type.korean+" 스킨 뽑기",List.of(
                    "<#ffd477>비용: "+Economy.format(WeaponSkinCatalog.PRICE),
                    "<gray>기본 외형을 제외한 13종", "<gray>미보유 스킨 중 동일 확률 · 중복 없음",
                    "<yellow>클릭하면 즉시 결제하고 뽑습니다")));
                inventory.setItem(BOOK_SLOTS.get(index),button("<#c9b8ef>"+type.korean+" 스킨 도감",List.of("<gray>보유 스킨 확인 · 무료 적용")));
            }
            inventory.setItem(35,button("<gray>닫기",List.of()));
        }
        @Override public void onClick(Player player,int slot,InventoryClickEvent event){
            if(!accepts(event) || used)return;
            if(slot==35){transition(player,this,player::closeInventory);return;}
            int book=BOOK_SLOTS.indexOf(slot);
            if(book>=0){transition(player,this,()->new CollectionGui(player,KINDS.get(book)).open(player));return;}
            int index=DRAW_SLOTS.indexOf(slot);if(index<0)return;
            used=true;
            ToolType type=KINDS.get(index);
            var remaining=WeaponSkinCatalog.remaining(type.id,economy.unlocks(player.getUniqueId()));
            if(remaining.isEmpty()){
                Messages.send(player,"이 종류의 스킨을 모두 모았어요. 추가 비용은 들지 않아요.");
                transition(player,this,()->new CollectionGui(player,type).open(player));return;
            }
            var selected=remaining.get(ThreadLocalRandom.current().nextInt(remaining.size()));
            economy.ensureAccount(player.getUniqueId());
            var purchase=economy.purchaseUnlock(player.getUniqueId(),WeaponSkinCatalog.PRICE,selected.key());
            if(purchase==Economy.UnlockPurchase.SUCCESS){
                // Ownership and cost are durable before opening the cosmetic result screen.
                Messages.send(player,Text.mm("<#e8c57f>"+selected.name()+" <gray>스킨을 도감에 등록했어요. 잔액: "+Economy.format(economy.balance(player.getUniqueId()))));
                player.playSound(player.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,SoundCategory.PLAYERS,.65f,1.1f);
                transition(player,this,()->new ResultGui(type,selected).open(player));
            }else{
                Messages.send(player,Text.mm(purchase==Economy.UnlockPurchase.INSUFFICIENT
                    ?"<red>100,000G가 필요해요. 골드가 부족해요.":"<red>스킨을 저장하지 못했어요. 골드는 차감되지 않았어요."));
                transition(player,this,()->new ShopGui().open(player));
            }
        }
    }
    private final class ResultGui extends SkinGui {
        private final ToolType type;
        ResultGui(ToolType type,WeaponSkinCatalog.Skin skin){
            super(3,0xE0B4,"스킨 획득 · "+skin.name());this.type=type;
            inventory.setItem(13,icon(skin.model(),"<#f4df9d>"+skin.name(),List.of("<gray>"+type.korean+" 스킨", "<#b5d6b3>계정 도감에 영구 등록되었습니다", "<gray>도감에서 같은 종류의 주손 무기에 무료 적용")));
            inventory.setItem(20,button("<#d9c2ef>도감 열기",List.of("<gray>방금 획득한 스킨을 적용합니다")));
            inventory.setItem(24,button("<#e8c57f>상점으로",List.of("<gray>다른 무기 종류를 선택합니다")));
        }
        @Override public void onClick(Player player,int slot,InventoryClickEvent event){
            if(!accepts(event))return;
            if(slot>=19 && slot<=21)transition(player,this,()->new CollectionGui(player,type).open(player));
            if(slot>=23 && slot<=25)transition(player,this,()->new ShopGui().open(player));
        }
    }
    private final class CollectionGui extends SkinGui {
        private final ToolType type;
        private final Map<Integer,WeaponSkinCatalog.Skin> choices=new HashMap<>();
        CollectionGui(Player player,ToolType type){
            super(5,0xE0B8,type.korean+" 스킨 도감");this.type=type;
            var owned=economy.unlocks(player.getUniqueId());
            inventory.setItem(10,icon(type.model,"<#ded8c8>기본 외형",List.of("<gray>클릭: 주손 "+type.korean+"을 기본 외형으로", "<gray>비용 없음 · 보유 스킨 유지")));
            var skins=WeaponSkinCatalog.pool(type.id);
            for(int index=0;index<skins.size();index++){
                int position=index+1,slot=position<7?10+position:19+(position-7);
                var skin=skins.get(index);choices.put(slot,skin);
                boolean unlocked=owned.contains(skin.key());
                inventory.setItem(slot,icon(skin.model(),(unlocked?"<#e5d3a1>":"<#8c8498>")+skin.name(),
                    unlocked?List.of("<#b4d2ab>보유 중 · "+type.korean,"<gray>클릭: 같은 종류의 주손 무기에 무료 적용")
                    :List.of("<#b98292>미보유 · "+type.korean,"<gray>상점에서 미보유 스킨을 뽑아주세요")));
            }
            inventory.setItem(40,button("<gray>상점으로",List.of("<gray>현재 "+(13-WeaponSkinCatalog.remaining(type.id,owned).size())+" / 13종 보유")));
        }
        @Override public void onClick(Player player,int slot,InventoryClickEvent event){
            if(!accepts(event))return;
            if(slot>=39 && slot<=41){transition(player,this,()->new ShopGui().open(player));return;}
            var skin=choices.get(slot);if(slot!=10 && skin==null)return;
            if(skin!=null && !economy.unlocks(player.getUniqueId()).contains(skin.key())){
                Messages.send(player,"아직 보유하지 않은 스킨이에요.");return;
            }
            ItemStack item=player.getInventory().getItemInMainHand();
            if(tools.typeOf(item)!=type || item.getAmount()!=1){
                Messages.send(player,"주손에 적용할 "+type.korean+"을 한 개 들어주세요.");return;
            }
            // Clone the real item; do not rebuild it or touch durability, stats or artisan progress.
            ItemStack changed=item.clone();var meta=changed.getItemMeta();
            if(skin==null)meta.getPersistentDataContainer().remove(skinKey);
            else meta.getPersistentDataContainer().set(skinKey,PersistentDataType.STRING,skin.key());
            if(plugin.resourcePackEnabled())meta.setItemModel(Glyphs.model(skin==null?type.model:skin.model()));
            changed.setItemMeta(meta);player.getInventory().setItemInMainHand(changed);
            Messages.send(player,Text.mm("<#c6d8b3>"+(skin==null?"기본 외형":skin.name())+"<gray>을 적용했어요. 비용은 없어요."));
            player.playSound(player.getLocation(),Sound.ITEM_ARMOR_EQUIP_LEATHER,SoundCategory.PLAYERS,.5f,1.2f);
        }
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission("servercore.admin")){Messages.send(sender,"관리자만 사용할 수 있어요.");return true;}
        if(command.getName().equals("skinshop")){
            Player target=args.length==0?(sender instanceof Player p?p:null):Bukkit.getPlayerExact(args[0]);
            if(target==null)Messages.send(sender,"접속 중인 플레이어를 지정해 주세요.");else if(active)new ShopGui().open(target);
            return true;
        }
        if(!(sender instanceof Player player)){Messages.send(sender,"게임 안에서 실행해 주세요.");return true;}
        if(args.length!=1 || !List.of("create","remove","face").contains(args[0].toLowerCase(Locale.ROOT))){
            Messages.send(sender,"/skinmerchantnpc create|remove|face");return true;
        }
        if(args[0].equalsIgnoreCase("create")){
            Location at=player.getLocation();at.setX(at.getBlockX()+.5);at.setZ(at.getBlockZ()+.5);at.setYaw(at.getYaw()+180);at.setPitch(0);
            var root=plugin.npcModels().spawn(at,"skinmerchant",e->e.getPersistentDataContainer().set(npcKey,PersistentDataType.BYTE,(byte)1));
            Messages.send(player,root==null?"NPC 생성이 차단됐어요.":"스킨 상인을 세웠어요. 우클릭하면 바로 상점이 열려요.");
        }else{
            Entity npc=player.getWorld().getNearbyEntities(player.getLocation(),5,5,5).stream()
                .filter(this::isNpc).filter(e->e.getLocation().distanceSquared(player.getLocation())<=25)
                .min(Comparator.comparingDouble(e->e.getLocation().distanceSquared(player.getLocation()))).orElse(null);
            if(npc==null)Messages.send(player,"5블록 안에 스킨 상인이 없어요.");
            else if(args[0].equalsIgnoreCase("face")){plugin.npcModels().face(npc,player.getLocation().getYaw()+180);Messages.send(player,"스킨 상인의 방향을 바꿨어요.");}
            else{plugin.npcModels().remove(npc);Messages.send(player,"스킨 상인을 제거했어요.");}
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission("servercore.admin"))return List.of();
        if(command.getName().equals("skinshop"))return args.length==1?null:List.of();
        return args.length==1?List.of("create","remove","face").stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList():List.of();
    }
}
