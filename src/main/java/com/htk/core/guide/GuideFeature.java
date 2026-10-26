package com.htk.core.guide;

import com.htk.core.ServerCore;
import com.htk.core.common.Feature;
import com.htk.core.common.Placeholders;
import com.htk.core.common.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 서버 안내서 (글이 쓰인 책)
 *   - 처음 접속하면 1권 (플레이어마다 한 번만)
 *   - /guide (별칭 /가이드, /안내서) 로 언제든 다시 받기
 * 내용은 plugins/ServerCore/guide.yml 에서 바꿔요 (바꾼 뒤 /servercore reload)
 */
public final class GuideFeature implements Feature, Listener {

    private final ServerCore plugin;
    private final NamespacedKey givenKey;
    private final NamespacedKey bookKey;
    private String title = "서버 안내서";
    private String author = "마을 관리소";
    private List<String> pages = List.of();
    private boolean giveOnFirstJoin = true;

    public GuideFeature(ServerCore plugin) {
        this.plugin = plugin;
        this.givenKey = new NamespacedKey(plugin, "guide_given");
        this.bookKey = new NamespacedKey(plugin, "guide_book");
    }

    @Override
    public void enable() {
        load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand cmd = plugin.getCommand("guide");
        if (cmd != null) {
            cmd.setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("게임 안에서만 쓸 수 있어요.");
                    return true;
                }
                give(p);
                p.sendMessage(Text.mm(msg("given", "<#f3c060>📖 <gray>서버 안내서를 받았어요. 손에 들고 우클릭해서 읽어 보세요!")));
                return true;
            });
        }
    }

    @Override
    public void reload() {
        load();
    }

    @Override
    public void disable() { }

    private void load() {
        YamlConfiguration yml = plugin.loadYaml("guide.yml");
        title = yml.getString("title", "서버 안내서");
        author = yml.getString("author", "마을 관리소");
        giveOnFirstJoin = yml.getBoolean("give-on-first-join", true);
        pages = yml.getStringList("pages");
        if (pages.isEmpty()) pages = plugin.loadDefaultYaml("guide.yml").getStringList("pages");
    }

    private String msg(String key, String def) {
        return plugin.loadYaml("guide.yml").getString("messages." + key, def);
    }

    /** 안내서 한 권 (가방이 꽉 차면 발밑에) */
    public void give(Player p) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        if (book.getItemMeta() instanceof BookMeta meta) {
            meta.title(Text.mm(title));
            meta.author(Text.mm(author));
            List<Component> list = new ArrayList<>();
            for (String page : pages) list.add(Text.mm(Placeholders.apply(p, page.stripTrailing())));
            meta.addPages(list.toArray(new Component[0]));
            meta.getPersistentDataContainer().set(bookKey, PersistentDataType.BYTE, (byte) 1);
            book.setItemMeta(meta);
        }
        var left = p.getInventory().addItem(book);
        for (ItemStack it : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), it);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!giveOnFirstJoin) return;
        var pdc = p.getPersistentDataContainer();
        if (pdc.has(givenKey, PersistentDataType.BYTE)) return;
        pdc.set(givenKey, PersistentDataType.BYTE, (byte) 1);
        // 접속 메시지·리소스팩 안내가 끝난 뒤에 주려고 조금 늦게
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            give(p);
            p.sendMessage(Text.mm(msg("first-join", "<#f3c060>📖 <gray>서버 안내서를 받았어요! 손에 들고 우클릭해서 먼저 읽어 주세요. <dark_gray>(다시 받기: /guide)")));
        }, 60L);
    }
}
