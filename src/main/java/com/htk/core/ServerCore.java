package com.htk.core;

import com.htk.core.common.Feature;
import com.htk.core.common.GuiListener;
import com.htk.core.common.Placeholders;
import com.htk.core.common.Points;
import com.htk.core.common.Text;
import com.htk.core.dogam.DogamFeature;
import com.htk.core.economy.EconomyFeature;
import com.htk.core.shop.ShopFeature;
import com.htk.core.skill.SkillFeature;
import com.htk.core.hud.HudFeature;
import com.htk.core.jumppad.JumpPadFeature;
import com.htk.core.land.LandFeature;
import com.htk.core.longjump.LongJumpFeature;
import com.htk.core.menu.MenuFeature;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ServerCore extends JavaPlugin {

    private final List<Feature> features = new ArrayList<>();
    private Points points;
    private com.htk.core.npc.NpcModelService npcModels;
    public com.htk.core.npc.NpcModelService npcModels() { return npcModels; }

    @Override
    public void onEnable() {
        saveDefaultFiles();
        npcModels=new com.htk.core.npc.NpcModelService(this);

        points = new Points(getDataFolder(), getLogger());
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        EconomyFeature economy = null;
        if (getConfig().getBoolean("features.economy", true)) {
            economy = new EconomyFeature(this);
            features.add(economy);
        }
        if (getConfig().getBoolean("features.dogam", true)) features.add(new DogamFeature(this));
        if (getConfig().getBoolean("features.menu", true)) features.add(new MenuFeature(this));
        if (getConfig().getBoolean("features.jumppad", true)) features.add(new JumpPadFeature(this));
        if (getConfig().getBoolean("features.longjump", true)) features.add(new LongJumpFeature(this));
        if (getConfig().getBoolean("features.guide", true)) features.add(new com.htk.core.guide.GuideFeature(this));
        if (getConfig().getBoolean("features.tools", true)) {
            var tools = new com.htk.core.tools.VillageToolFeature(this);
            features.add(tools);
            if (getConfig().getBoolean("features.blacksmith", true))
                features.add(new com.htk.core.tools.BlacksmithFeature(this, tools));
        }
        if (getConfig().getBoolean("features.cookie", true))
            features.add(new com.htk.core.cookie.CookieFeature(this));
        if (getConfig().getBoolean("features.ranking", true))
            features.add(new com.htk.core.ranking.RankingFeature(this));
        SkillFeature skills = null;
        if (getConfig().getBoolean("features.skills", true)) {
            skills = new SkillFeature(this);
            features.add(skills);
        }
        for (Feature f : features) f.enable();
        // 상점은 골드가 있어야 해요 (골드 기능이 켜진 다음에)
        if (economy != null && getConfig().getBoolean("features.shop", true)) {
            ShopFeature shop = new ShopFeature(this, economy);
            features.add(shop);
            shop.enable();
        }
        var villageTools=feature(com.htk.core.tools.VillageToolFeature.class);
        if(economy!=null && villageTools!=null && getConfig().getBoolean("features.weapon-merchant",true)) {
            var merchant=new com.htk.core.merchant.WeaponMerchantFeature(this,economy.economy(),villageTools);
            features.add(merchant);merchant.enable();
        }
        // HUD는 레벨과 골드가 있어야 해요
        if (skills != null && economy != null && getConfig().getBoolean("features.hud", true)) {
            HudFeature hud = new HudFeature(this, skills, economy.economy());
            features.add(hud);
            hud.enable();
        }

        // 집 땅은 골드(빚 갚기)가 있어야 해요
        if (economy != null && getConfig().getBoolean("features.land", true)) {
            LandFeature land = new LandFeature(this, economy.economy());
            features.add(land);
            land.enable();
        }

        // 이장님: 첫 대화로 집터 주기 + 빚 갚기 창 (골드가 있어야 해요)
        if (economy != null && getConfig().getBoolean("features.mayor", true)) {
            com.htk.core.mayor.MayorFeature mayor = new com.htk.core.mayor.MayorFeature(this, economy.economy());
            features.add(mayor);
            mayor.enable();
        }

        PluginCommand admin = getCommand("servercore");
        if (admin != null) admin.setExecutor(new CoreCommand(this));
        PluginCommand npc = getCommand("npc");
        if (npc != null) {
            var command = new com.htk.core.npc.NpcCommand(this);
            npc.setExecutor(command);
            npc.setTabCompleter(command);
        }

        getLogger().info("켜진 기능 " + features.size() + "개");
    }

    @Override
    public void onDisable() {
        for (Feature f : features) f.disable();
        Placeholders.clear();
    }

    public void reloadAll() {
        saveDefaultFiles();          // 지운 설정 파일은 jar 안의 기본 파일로 다시 만들어요
        reloadConfig();
        points.reload();
        for (Feature f : features) f.reload();
    }

    /** 꺼진 기능의 명령어를 입력했을 때 (켜진 기능은 각자 명령어를 가져가요) */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(Text.mm(msg("feature-disabled")));
        return true;
    }

    // ---------- 도우미 ----------

    /** 켜져 있는 기능 찾기 (꺼져 있으면 null) */
    public <T extends Feature> T feature(Class<T> type) {
        for (Feature f : features) if (type.isInstance(f)) return type.cast(f);
        return null;
    }

    /** 이름 붙인 위치 (points.yml) */
    public Points points() {
        return points;
    }

    public boolean resourcePackEnabled() {
        return getConfig().getBoolean("resourcepack.enabled", true);
    }

    public String msg(String key) {
        return getConfig().getString("messages." + key, key);
    }

    public YamlConfiguration loadYaml(String fileName) {
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), fileName));
    }

    /** jar 안에 들어 있는 기본 설정 파일 (서버 파일이 예전 형식일 때 대신 씀) */
    public YamlConfiguration loadDefaultYaml(String fileName) {
        var in = getResource(fileName);
        if (in == null) return new YamlConfiguration();
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(r);
        } catch (Exception e) {
            return new YamlConfiguration();
        }
    }

    /** 없는 설정 파일만 jar 안의 기본 파일로 만들기 (있는 파일은 건드리지 않아요) */
    private void saveDefaultFiles() {
        saveDefaultConfig();
        for (String f : new String[]{"menu.yml", "dogam.yml", "shops.yml", "skills.yml", "guide.yml", "tools.yml", "ranking.yml", "weapon-shop.yml"}) {
            if (!new File(getDataFolder(), f).exists()) {
                saveIfMissing(f);
                getLogger().info(f + " 이(가) 없어서 기본 파일로 만들었어요.");
            }
        }
    }

    private void saveIfMissing(String fileName) {
        if (!new File(getDataFolder(), fileName).exists()) saveResource(fileName, false);
    }
}
