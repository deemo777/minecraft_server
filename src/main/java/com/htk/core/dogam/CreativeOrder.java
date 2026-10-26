package com.htk.core.dogam;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 크리에이티브 인벤토리 순서 (미리 뽑아 둔 목록).
 *   건축 블록 → 색깔 블록 → 자연 블록 → 기능 블록 → 레드스톤 → 도구 → 전투 → 음식 → 재료 → 생성 알 → 관리자
 *
 * 목록은 플러그인 jar 안의 creative_order.txt 예요 (tools/make_creative_order.py 로 마인크래프트 jar에서 뽑음).
 * 서버를 켤 때 게임 코드를 건드리지 않고 이 파일만 읽어요.
 * 마인크래프트를 업데이트해서 순서가 바뀌면 그 스크립트를 다시 실행하고 플러그인을 다시 빌드하면 돼요.
 */
final class CreativeOrder {

    private CreativeOrder() {}

    static Map<Material, Integer> load(JavaPlugin plugin) {
        Logger log = plugin.getLogger();
        Map<Material, Integer> order = new HashMap<>();
        InputStream in = plugin.getResource("creative_order.txt");
        if (in == null) {
            log.warning("creative_order.txt 가 jar 안에 없어서 dogam.yml 순서로 보여줘요.");
            return order;
        }
        int n = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                Material m = Material.matchMaterial(line);
                if (m != null && !order.containsKey(m)) order.put(m, n);
                n++;
            }
        } catch (Exception e) {
            log.warning("creative_order.txt 를 읽다가 문제가 생겼어요: " + e);
        }
        return order;
    }
}
