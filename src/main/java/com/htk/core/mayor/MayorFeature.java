package com.htk.core.mayor;

import com.htk.core.ServerCore;
import com.htk.core.common.Dialogs;
import com.htk.core.common.Feature;
import com.htk.core.common.Text;
import com.htk.core.economy.Economy;
import com.htk.core.land.LandFeature;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 이장 NPC (로비)
 *   - 첫 인사/재방문 인사 아래 집터 수령·전액 빚 상환 버튼.
 *   - 집터 안내는 매 장 다 읽음 확인 후 수령. 잔액 부족은 비활성 버튼과 툴팁.
 *   /mayornpc create      지금 서 있는 곳에 이장 세우기 (나를 바라보게)
 *   /mayornpc remove      5칸 안의 이장 없애기
 *   /mayornpc reset <name>  그 사람의 첫 대화를 처음부터 다시 (테스트용)
 * 인사·집터 안내·상환 UI는 서버 코드에서 관리한다.
 */
public final class MayorFeature implements Feature, Listener {

    private final ServerCore plugin;
    private final Economy economy;
    private final java.util.Map<java.util.UUID,Long> lastTalk=new java.util.HashMap<>();
    private final NamespacedKey npcKey;
    private final NamespacedKey pageKey;      // 첫 대화 몇 장째까지 봤는지
    private final NamespacedKey doneKey;      // 첫 대화를 끝내고 집터를 받았는지
    private final NamespacedKey metKey;
    private final NamespacedKey legacyKitKey; // 예전 방식(처음 접속하면 집터)으로 이미 받은 사람

    public MayorFeature(ServerCore plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.metKey = new NamespacedKey(plugin, "mayor_met");
        this.npcKey = new NamespacedKey(plugin, "mayor_npc");
        this.pageKey = new NamespacedKey(plugin, "mayor_plot_guide_page_v2");
        this.doneKey = new NamespacedKey(plugin, "mayor_intro_done");
        this.legacyKitKey = new NamespacedKey(plugin, "land_kit_given");
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.npcModels().registerLegacy(e -> isMayor(e)?"mayor":null);
        PluginCommand cmd = plugin.getCommand("mayornpc");
        if (cmd != null) {
            NpcCommand c = new NpcCommand();
            cmd.setExecutor(c);
            cmd.setTabCompleter(c);
        }
    }

    @Override
    public void reload() { }

    @Override
    public void disable() { lastTalk.clear(); }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private String s(String key, String def) {
        return cfg().getString("mayor." + key, def);
    }

    private LandFeature land() {
        return plugin.feature(LandFeature.class);
    }

    // ---------------- 첫 대화 진행 상태 ----------------

    /** 첫 대화를 끝냈는지 (예전에 집터를 이미 받았거나 집이 있는 사람도 끝낸 걸로) */
    public boolean introDone(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (pdc.has(doneKey, PersistentDataType.BYTE)) return true;
        if (pdc.has(legacyKitKey, PersistentDataType.BYTE)) return true;
        LandFeature land = land();
        return land != null && land.landOf(p.getUniqueId()) != null;
    }

    private int page(Player p) {
        Integer v = p.getPersistentDataContainer().get(pageKey, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    private void setPage(Player p, int page) {
        p.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, page);
    }

    private void resetIntro(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.remove(metKey);
        pdc.remove(pageKey);
        pdc.remove(doneKey);
        pdc.remove(legacyKitKey);
    }

    /** config 의 mayor.intro : 장(page)마다 줄 목록 */
    private List<List<String>> introPages() {
        return List.of(
            List.of("<white>집터는 village 월드에 설치할 수 있다네.", "<white>부지는 정사각형 2×2, 총 4청크로 고정된다네.", "<gray>집터를 들고 바닥을 바라보면 설치할 부지 경계가 보이네."),
            List.of("<white>집터를 들고 웅크리기 + 우클릭을 하면", "<white>집터 블록이 놓일 모서리가 네 방향으로 바뀐다네.", "<gray>방향과 경계를 확인한 뒤 일반 우클릭으로 설치하게."),
            List.of("<white>처음에는 집터 청크 하나를 사용할 수 있네.", "<white>빚을 모두 갚은 뒤 남은 청크를 하나씩 해방하게.", "<white>집터 설치 후 무기 상인에게 광 도구를 살 수 있다네.", "<gold>안내를 확인했다면 집터 블록을 받게나."));
    }
    @EventHandler public void onQuit(org.bukkit.event.player.PlayerQuitEvent e){lastTalk.remove(e.getPlayer().getUniqueId());}
    public void talk(Player p) {
        long now=System.nanoTime();Long last=lastTalk.get(p.getUniqueId());
        if(last!=null&&now-last<300_000_000L)return;
        lastTalk.put(p.getUniqueId(),now);
        boolean met=p.getPersistentDataContainer().has(metKey,PersistentDataType.BYTE)||introDone(p)||page(p)>0;
        p.getPersistentDataContainer().set(metKey,PersistentDataType.BYTE,(byte)1);
        showOffice(p,met?"반갑다네":"광광서버에 오신걸 환영한다네");
    }
    private void showIntro(Player p,int page) {
        LandFeature land=land();
        if(land==null||land.landOf(p.getUniqueId())!=null||land.hasCoreItem(p)){showOffice(p);return;}
        var pages=introPages();int current=Math.clamp(page,0,pages.size()-1);setPage(p,current);
        boolean last=current==pages.size()-1;
        ActionButton next=Dialogs.checked(plugin,Text.mm(last?"<gold>집터 블록 수령":"<green>다음"),
            pl->{if(last)finishIntro(pl);else showIntro(pl,current+1);},
            pl->{pl.sendMessage(Text.mm("<yellow>다 읽음에 체크해 주세요."));showIntro(pl,current);});
        p.showDialog(dialog(Text.mm("이장님 · 집터 안내 ("+(current+1)+"/"+pages.size()+")"),pages.get(current),
            List.of(next),Dialogs.button(plugin,Text.mm("<gray>돌아가기"),null,100,this::showOffice),1,true));
    }
    private void finishIntro(Player p) {
        LandFeature land=land();
        if(land==null||land.landOf(p.getUniqueId())!=null||land.hasCoreItem(p)){showOffice(p);return;}
        p.getPersistentDataContainer().set(doneKey,PersistentDataType.BYTE,(byte)1);
        land.kit(p);p.closeDialog();
        p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_HARP,.5f,1.4f);
    }
    private ActionButton disabled(String label,String tooltip) {
        return ActionButton.create(Text.mm("<dark_gray>"+label),Text.mm("<gray>"+tooltip),200,null);
    }
    private void showOffice(Player p){showOffice(p,"반갑다네");}
    private void showOffice(Player p,String greeting) {
        LandFeature land=land();long debt=land==null?0:land.debtOf(p.getUniqueId());
        boolean hasLand=land!=null&&land.landOf(p.getUniqueId())!=null;
        long balance=economy.balance(p.getUniqueId());List<ActionButton> buttons=new ArrayList<>();
        if(land==null)buttons.add(disabled("집터 수령","집터 기능을 사용할 수 없습니다"));
        else if(hasLand)buttons.add(disabled("집터 수령","이미 집터를 설치했습니다"));
        else if(land.hasCoreItem(p))buttons.add(disabled("집터 수령","가방에 집터 블록이 있습니다"));
        else buttons.add(Dialogs.button(plugin,Text.mm("<gold>집터 수령"),null,200,pl->showIntro(pl,Math.min(page(pl),introPages().size()-1))));
        if(debt<=0)buttons.add(disabled("빚 갚기","갚을 빚이 없습니다"));
        else if(balance<debt)buttons.add(disabled("빚 전부 갚기 ("+Economy.format(debt)+")","돈이 부족합니다"));
        else buttons.add(Dialogs.button(plugin,Text.mm("<yellow>빚 전부 갚기 ("+Economy.format(debt)+")"),null,200,pl->{
            var current=land();if(current!=null)current.payDebt(pl);showOffice(pl);
        }));
        p.showDialog(dialog(Text.mm("이장님"),List.of("<white>"+greeting,"<gray>빚: <gold>"+Economy.format(debt)+" <gray>· 보유: <gold>"+Economy.format(balance)),
            buttons,Dialogs.close(plugin,Text.mm("<gray>닫기"),100),1));
    }

    private Dialog dialog(net.kyori.adventure.text.Component title, List<String> lines,
                          List<ActionButton> buttons, ActionButton exit, int columns) {
        return dialog(title,lines,buttons,exit,columns,false);
    }
    private Dialog dialog(net.kyori.adventure.text.Component title,List<String> lines,
                          List<ActionButton> buttons,ActionButton exit,int columns,boolean read) {
        DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                // 버튼을 눌러도 창은 그대로 → 서버가 다음 장을 보내면 바뀌고, 끝나면 서버가 닫아요.
                // (WAIT_FOR_RESPONSE 는 '서버를 기다리는 중' 화면으로 넘어가는데, 그 화면은 서버가 닫아 줄 수 없어서 멈춰요)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(Text.lines(lines))))
                .inputs(read?List.of(io.papermc.paper.registry.data.dialog.input.DialogInput.bool("read",Text.mm("다 읽음"),false,"true","false")):List.of())
                .build();
        List<ActionButton> list = buttons.isEmpty() ? List.of(exit) : buttons;
        ActionButton exitButton = buttons.isEmpty() ? null : exit;
        return Dialog.create(b -> b.empty().base(base).type(DialogType.multiAction(list, exitButton, columns)));
    }

    // ---------------- NPC ----------------

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onModelInteractAt(org.bukkit.event.player.PlayerInteractAtEntityEvent e) { onClick(e); }

    private boolean isMayor(Entity e) {
        return e.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(PlayerInteractEntityEvent e) {
        if (!isMayor(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        talk(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (isMayor(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTransform(EntityTransformEvent e) {
        if (isMayor(e.getEntity())) e.setCancelled(true);
    }

    /** 아직 이장님을 안 만났으면 안내 */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (introDone(p)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !introDone(p))
                p.sendMessage(Text.mm(s("messages.join-hint", "<#f3c060>🌾 <gray>로비의 <#f3c060>이장님</#f3c060>께 말을 걸어 보세요. 집터를 주실 거예요!")));
        }, 60L);
    }

    private org.bukkit.entity.Interaction spawnNpc(Location loc) {
        return plugin.npcModels().spawn(loc,"mayor",v -> {
            v.getPersistentDataContainer().set(npcKey,PersistentDataType.BYTE,(byte)1);
        });
    }

    private final class NpcCommand implements TabExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("servercore.admin")) {
                sender.sendMessage(Text.mm(plugin.msg("no-permission")));
                return true;
            }
            String sub = args.length == 0 ? "" : args[0].toLowerCase();
            if (sub.equals("reset") && args.length >= 2) {
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    sender.sendMessage(Text.mm("<red>접속 중인 플레이어 이름을 적어 주세요."));
                    return true;
                }
                resetIntro(t);
                sender.sendMessage(Text.mm("<green>" + t.getName() + "의 이장님 첫 대화를 처음으로 되돌렸어요."
                        + " <gray>(집이 이미 있으면 첫 대화는 건너뛰어요)"));
                return true;
            }
            if (!(sender instanceof Player p)) {
                sender.sendMessage("게임 안에서만 쓸 수 있어요.");
                return true;
            }
            if (sub.equals("create")) {
                Location at = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
                at.setYaw(p.getLocation().getYaw() + 180f);
                at.setPitch(0f);
                if(spawnNpc(at)==null){p.sendMessage(Text.mm("<red>NPC 생성이 차단됐어요. 월드 보호 설정을 확인하세요."));return true;}
                p.sendMessage(Text.mm("<green>이장님을 세웠어요."));
                return true;
            }
            if (sub.equals("remove") || sub.equals("face")) {
                Entity nearest = null;
                double best = 25;
                for (Entity e : p.getNearbyEntities(5, 5, 5)) {
                    if (!isMayor(e)) continue;
                    double d = e.getLocation().distanceSquared(p.getLocation());
                    if (d < best) {
                        best = d;
                        nearest = e;
                    }
                }
                if (nearest == null) {
                    p.sendMessage(Text.mm("<red>5칸 안에 이장님이 없어요."));
                    return true;
                }
                if(sub.equals("face")){plugin.npcModels().face(nearest,p.getLocation().getYaw()+180);p.sendMessage(Text.mm("<green>이장님 방향을 맞췄어요."));return true;}
                plugin.npcModels().remove(nearest);
                p.sendMessage(Text.mm("<green>이장님을 없앴어요."));
                return true;
            }
            p.sendMessage(Text.mm("<gray>사용법: /" + label + " create | remove | face | reset <player>"));
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (!sender.hasPermission("servercore.admin")) return List.of();
            if (args.length == 1) return List.of("create", "remove", "face", "reset");
            if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
                List<String> names = new ArrayList<>();
                for (Player pl : Bukkit.getOnlinePlayers()) names.add(pl.getName());
                return names;
            }
            return List.of();
        }
    }
}
