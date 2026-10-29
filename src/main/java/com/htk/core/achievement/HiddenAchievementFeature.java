package com.htk.core.achievement;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.htk.core.ServerCore;
import com.htk.core.common.*;
import com.htk.core.economy.EconomyFeature;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import java.io.File;
import java.io.IOException;
import java.util.*;

/** Hidden until the first server completion. Jump progress lives on each player, titles on the server. */
public final class HiddenAchievementFeature implements Feature,Listener,TabExecutor {
    private static final int TARGET=10_000;
    private final ServerCore plugin;
    private final NamespacedKey jumps;
    private AchievementStore store;
    private final Map<UUID,Component> prefixes=new java.util.concurrent.ConcurrentHashMap<>();
    public HiddenAchievementFeature(ServerCore plugin){this.plugin=plugin;jumps=new NamespacedKey(plugin,"hidden_jump_count");}
    @Override public void enable(){
        try{store=new AchievementStore(new File(plugin.getDataFolder(),"achievements.yml"));}
        catch(Exception failure){plugin.getLogger().severe("히든 업적 기록을 읽지 못해 보상 중복 방지를 위해 기능을 중지합니다: "+failure.getMessage());return;}
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(String name:List.of("atitle","achievements")) {var cmd=plugin.getCommand(name);if(cmd!=null){cmd.setExecutor(this);cmd.setTabCompleter(this);}}
        for(Player p:Bukkit.getOnlinePlayers()){apply(p);rewardFirst(p);}
    }
    @Override public void reload(){}
    @Override public void disable(){prefixes.clear();}
    public Component prefix(UUID id){return prefixes.getOrDefault(id,Component.empty());}
    public boolean hasTitles(UUID id){return store!=null && store.completed(id);}
    public record OwnedTitle(String id, String name, boolean selected) {}
    public List<OwnedTitle> ownedTitles(UUID id) {
        if (store == null) return List.of();
        return List.of(AchievementStore.NORMAL, AchievementStore.FIRST).stream()
                .filter(title -> store.owns(id, title))
                .map(title -> new OwnedTitle(title, titleName(title), title.equals(store.selected(id)))).toList();
    }
    public boolean clearSelected(Player p){
        if(store==null || store.selected(p.getUniqueId())==null)return true;
        try{store.select(p.getUniqueId(),null);apply(p);return true;}
        catch(IOException failure){Messages.send(p,Text.mm("<red>칭호를 저장하지 못했어요. 다시 시도해 주세요."));return false;}
    }
    private int count(Player p){return Math.clamp(p.getPersistentDataContainer().getOrDefault(jumps,PersistentDataType.INTEGER,0),0,TARGET);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void jump(PlayerJumpEvent event){
        Player p=event.getPlayer();
        if(p.getGameMode()!=GameMode.SURVIVAL && p.getGameMode()!=GameMode.ADVENTURE)return;
        int count=Math.min(TARGET,count(p)+1);
        p.getPersistentDataContainer().set(jumps,PersistentDataType.INTEGER,count);
        if(count==TARGET && !store.completed(p.getUniqueId()))complete(p);
    }
    private void complete(Player p){
        try {
            boolean first=store.complete(p.getUniqueId(),p.getName());
            // Persist the jump threshold too; the server's completion record prevents repeat rewards.
            p.saveData();apply(p);
            Messages.send(p,Text.mm("<gold>히든 업적 달성: <white>구름을 밟는 자 <gray>· 점프 10,000회"));
            p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,SoundCategory.PLAYERS,.6f,1);
            if(first){
                for(Player audience:Bukkit.getOnlinePlayers())Messages.send(audience,Component.text("[히든 업적 공개] "+p.getName()+"님이 처음으로 점프 10,000회를 달성했습니다! 전용 칭호: 하늘을 연 자"));
                rewardFirst(p);
            }
        }catch(IOException failure){plugin.getLogger().severe("히든 업적 저장 실패: "+failure.getMessage());}
    }
    private void rewardFirst(Player p){
        if(!p.getUniqueId().equals(store.first()))return;
        var feature=plugin.feature(EconomyFeature.class);
        if(feature==null)return;
        var wallet=feature.economy();wallet.ensureAccount(p.getUniqueId());
        String receipt="hidden_jump_10000_first";
        if(wallet.hasRewardReceipt(receipt))return;
        if(wallet.depositOnce(p.getUniqueId(),30_000,receipt))
            Messages.send(p,Text.mm("<gold>서버 최초 달성 보상: 30,000G + 전용 칭호 ‘하늘을 연 자’"));
        else plugin.getLogger().warning("최초 업적 보상 저장 실패. 다음 접속 시 재시도: "+p.getUniqueId());
    }
    @EventHandler public void join(PlayerJoinEvent event){
        Player p=event.getPlayer();Bukkit.getScheduler().runTask(plugin,()->{
            if(!p.isOnline())return;
            if(count(p)==TARGET && !store.completed(p.getUniqueId()))complete(p);
            apply(p);rewardFirst(p);
        });
    }
    private static String titleName(String id){return AchievementStore.FIRST.equals(id)?"하늘을 연 자":"구름을 밟는 자";}
    private void apply(Player p){
        String title=store.selected(p.getUniqueId());
        if(title==null)prefixes.remove(p.getUniqueId());else prefixes.put(p.getUniqueId(),Text.mm("<gold>["+titleName(title)+"] <white>"));
        var skills=plugin.feature(com.htk.core.skill.SkillFeature.class);
        if(skills!=null){p.displayName(Component.text(p.getName()));skills.refreshListName(p);return;}
        Component name=Component.text(p.getName());
        if(title!=null)name=Text.mm("<gold>["+titleName(title)+"] <white>").append(name);
        p.displayName(name);p.playerListName(name);
    }
    @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent event){prefixes.remove(event.getPlayer().getUniqueId());}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player p)){Messages.send(sender,"게임 안에서 실행해 주세요.");return true;}
        if(command.getName().equals("achievements")) {
            if(store.first()==null)Messages.send(p,Text.mm("<gray>히든 업적: ??? <dark_gray>누군가 처음 달성하면 공개됩니다."));
            else {
                Messages.send(p,Component.text("구름을 밟는 자 · 점프 10,000회 · 최초 달성: "+store.firstName()));
                Messages.send(p,Component.text(store.completed(p.getUniqueId())?"달성 완료 · 칭호 보유":"진행: "+count(p)+" / 10,000"));
            }
            return true;
        }
        if(args.length==0){
            if(!store.completed(p.getUniqueId())){Messages.send(p,Text.mm("<gray>아직 획득한 칭호가 없어요."));return true;}
            Messages.send(p,Text.mm("<gold>보유 칭호: 구름을 밟는 자"+(p.getUniqueId().equals(store.first())?" · 하늘을 연 자":"")));
            Messages.send(p,Component.text("/atitle equip cloud_walker"+(p.getUniqueId().equals(store.first())?" | sky_opener":"")+" · /atitle clear"));return true;
        }
        String title=args.length==2 && args[0].equalsIgnoreCase("equip")?args[1]:null;
        if(!(args.length==1 && args[0].equalsIgnoreCase("clear")) && (title==null || !store.owns(p.getUniqueId(),title))) {
            Messages.send(p,Text.mm("<red>보유한 칭호만 장착할 수 있어요. /atitle 로 확인하세요."));return true;
        }
        try{store.select(p.getUniqueId(),title);apply(p);Messages.send(p,Text.mm(title==null?"<gray>칭호를 해제했어요.":"<gold>칭호 장착: "+titleName(title)));}
        catch(IOException failure){Messages.send(p,Text.mm("<red>칭호를 저장하지 못했어요. 다시 시도해 주세요."));}
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(command.getName().equals("atitle") && sender instanceof Player p){
            if(args.length==1)return List.of("equip","clear");
            if(args.length==2 && args[0].equalsIgnoreCase("equip"))return List.of(AchievementStore.NORMAL,AchievementStore.FIRST).stream().filter(t->store.owns(p.getUniqueId(),t)).toList();
        }
        return List.of();
    }
}
