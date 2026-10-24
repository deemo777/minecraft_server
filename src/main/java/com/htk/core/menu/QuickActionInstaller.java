package com.htk.core.menu;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.htk.core.ServerCore;
import com.htk.core.common.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * G 키(빠른 동작)를 누르면 뜨는 대화창을 데이터팩으로 메인 월드에 설치해요.
 *
 * G 키 대화창은 플레이어 게임이 "미리 받아둔" 대화창을 혼자 여는 방식이라,
 * 서버가 켜질 때 등록된 것만 쓸 수 있어요. → menu.yml을 고치면 서버 재시작이 필요해요.
 * 버튼은 명령어를 실행해요 (/dogam, /menu soon ...).
 */
public final class QuickActionInstaller {

    private static final String PACK = "servercore_quick_action";

    private QuickActionInstaller() {}

    public static void install(ServerCore plugin, MenuConfig config) {
        if (Bukkit.getWorlds().isEmpty()) return;
        World main = Bukkit.getWorlds().get(0);
        Path packDir = levelRoot(main).resolve("datapacks").resolve(PACK);

        try {
            String dialog = gson(dialogJson(config));
            Path dialogFile = packDir.resolve("data/servercore/dialog/menu.json");
            boolean changed = !Files.exists(dialogFile)
                    || !Files.readString(dialogFile, StandardCharsets.UTF_8).equals(dialog);

            write(packDir.resolve("pack.mcmeta"), gson(packMeta()));
            write(packDir.resolve("data/minecraft/tags/dialog/quick_actions.json"), gson(tagJson()));
            write(dialogFile, dialog);

            if (changed) {
                plugin.getLogger().warning("G 키 메뉴 데이터팩을 새로 만들었어요. 서버를 한 번 재시작해야 G 키에 반영돼요!");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "G 키 메뉴 데이터팩 설치 실패", e);
        }
    }

    /**
     * 데이터팩은 월드 맨 위 폴더(level.dat 있는 곳)의 datapacks 에 있어야 읽혀요.
     * 26.x 부터 getWorldFolder()가 world/dimensions/minecraft/overworld 를 돌려줘서 위로 올라가며 찾아요.
     */
    private static Path levelRoot(World world) {
        Path dir = world.getWorldFolder().toPath().toAbsolutePath();
        for (Path p = dir; p != null; p = p.getParent()) {
            if (Files.exists(p.resolve("level.dat"))) return p;
        }
        return dir;
    }

    private static JsonObject dialogJson(MenuConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:multi_action");
        root.add("title", component(config.title()));
        root.add("external_title", component(Text.plain(config.title())));
        root.addProperty("pause", false);
        root.addProperty("after_action", "close");
        root.addProperty("columns", config.columns());

        JsonArray actions = new JsonArray();
        for (MenuConfig.Button b : config.buttons()) {
            JsonObject btn = new JsonObject();
            btn.add("label", component(b.label()));
            if (!b.tooltip().isEmpty()) btn.add("tooltip", component(String.join("\n", b.tooltip())));
            btn.addProperty("width", config.buttonWidth());

            String command = b.comingSoon() || b.command().isBlank() ? "menu soon" : b.command();
            JsonObject action = new JsonObject();
            action.addProperty("type", "run_command");
            action.addProperty("command", "/" + command);
            btn.add("action", action);
            actions.add(btn);
        }
        root.add("actions", actions);

        JsonObject exit = new JsonObject();
        exit.add("label", component(config.closeLabel()));
        exit.addProperty("width", 100);
        root.add("exit_action", exit);
        return root;
    }

    private static JsonObject tagJson() {
        JsonObject tag = new JsonObject();
        JsonArray values = new JsonArray();
        values.add("servercore:menu");
        tag.add("values", values);
        return tag;
    }

    /** 버전 번호가 맞지 않아도 넓은 범위로 적어서 대부분 버전에서 읽히게 */
    private static JsonObject packMeta() {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "ServerCore - G 키 마을 메뉴");
        pack.addProperty("pack_format", 80);
        JsonArray supported = new JsonArray();
        supported.add(80);
        supported.add(1000);
        pack.add("supported_formats", supported);
        pack.addProperty("min_format", 80);
        pack.addProperty("max_format", 1000);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }

    private static JsonElement component(String miniMessage) {
        Component c = Text.mm(miniMessage);
        return JsonParser.parseString(GsonComponentSerializer.gson().serialize(c));
    }

    private static String gson(JsonObject obj) {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(obj);
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
