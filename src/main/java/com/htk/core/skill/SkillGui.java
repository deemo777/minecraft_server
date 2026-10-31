package com.htk.core.skill;

import com.htk.core.common.Glyphs;
import com.htk.core.common.Gui;
import com.htk.core.common.Items;
import com.htk.core.common.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 스킬 창 (6줄 상자) — 메뉴에서 직업 경험치를 누르거나 /skill
 *
 *   0~4줄: 분야마다 한 줄. 0열 = 아이콘, 1~8열 = 카드 ("농사" 글씨는 배경 그림, Lv·경험치·게이지는 제목 글자로)
 *   5줄:   0열 ◀ (메뉴로)
 *
 * ⚠ 위치는 tools/make_art.py 의 draw_skill(), tools/build_pack.py 의 SKILL_ROW_TOP 과 약속이에요.
 *   줄 r 칸 윗변 y = 17 + 18r,  글자 윗변 y+2 (폰트 servercore:skill{r+1}),  게이지 안쪽 y+11 ~ y+15, x 29 부터
 */
final class SkillGui extends Gui {

    static final int BACK_SLOT = 45;
    private static final int LV_X = 52;          // "Lv.33" 시작
    private static final int XP_RIGHT = 165;     // "962/4,637" 오른쪽 끝
    private static final int BAR_X = 29;         // 게이지 시작
    private static final int BAR_PIECES = 45;    // 4px 조각 45개 (1px씩 겹침) = 136px
    private static final char BAR_FIRST = '';
    private static final TextColor INK = TextColor.color(0x6b4226);
    private static final TextColor INK_SOFT = TextColor.color(0x9a6a3e);
    private static final Material[] ICONS = {
            Material.WHEAT, Material.IRON_PICKAXE, Material.IRON_AXE, Material.FISHING_ROD, Material.IRON_SWORD};

    private final SkillFeature feature;

    SkillGui(SkillFeature feature, Player player) {
        this.feature = feature;
        boolean pack = feature.plugin().resourcePackEnabled();
        UUID id = player.getUniqueId();

        int total = 0;
        for (Skill s : Skill.values()) total += feature.progress(id, s).level;

        Component title;
        if (pack) {
            Glyphs.Canvas cv = new Glyphs.Canvas().background(Glyphs.SKILL_BG);
            for (Skill s : Skill.values()) {
                int r = s.ordinal();
                Key font = Key.key("servercore", "skill" + (r + 1));
                SkillStore.Progress pr = feature.progress(id, s);
                boolean max = pr.level >= feature.maxLevel();
                long need = feature.need(s,pr.level);
                cv.text(LV_X, "Lv." + pr.level, font, INK);
                cv.textRight(XP_RIGHT, max ? "MAX" : fmt(pr.xp) + "/" + fmt(need), font, INK_SOFT);
                cv.bar((char) (BAR_FIRST + r), BAR_X, BAR_PIECES, max ? 1 : pr.xp, max ? 1 : need);
            }
            title = cv.build(Text.mm("<#6b4226>총 레벨 " + total));
        } else {
            title = Text.mm("<dark_gray>스킬 (총 레벨 " + total + ")");
        }
        create(6, title);

        for (Skill s : Skill.values()) {
            SkillStore.Progress pr = feature.progress(id, s);
            boolean max = pr.level >= feature.maxLevel();
            String name = "<#7acb52><bold>" + s.korean + "</bold> <white>Lv." + pr.level;
            List<String> lore = new java.util.ArrayList<>();
            if (!pack) lore.add(max ? "<gray>최고 레벨" : "<gray>경험치 <white>" + fmt(pr.xp) + "/" + fmt(feature.need(s,pr.level)));
            String cur = feature.currentTitleText(id, s);
            lore.add("<gray>칭호: " + (cur == null ? "<dark_gray>없음" : "<white>" + cur));
            var next = feature.nextTitle(id, s);
            if (next != null) lore.add("<dark_gray>다음 칭호: Lv." + next.getKey() + " " + Text.plain(next.getValue()));
            lore.add("");
            if (s == Skill.COMBAT) lore.add("<gray>몹에게 주는 피해 <#7acb52>+" + pct(feature.combatBonus(id)));
            else lore.add("<gray>" + (s == Skill.FISHING ? "한 마리 더 낚을" : "2배로 얻을") + " 확률 <#7acb52>"
                    + pct(feature.doubleDropChance(id, s)));
            String shopName = s == Skill.FARMING ? "농작물" : s == Skill.MINING ? "광물" : s == Skill.FISHING ? "생선" : null;
            if (shopName != null) lore.add("<gray>" + shopName + " 가게 판매가 <#7acb52>+" + pct(feature.sellBonusOf(id, s)));
            inventory.setItem(Gui.slot(s.ordinal(), 0), Items.of(ICONS[s.ordinal()], 1, name, lore, null));
        }
        ItemStack back = pack
                ? Items.of(Material.PAPER, 1, "", List.of(), Glyphs.model("arrow_left"))
                : Items.of(Material.ARROW, 1, "<gray>메뉴로", List.of(), null);
        inventory.setItem(BACK_SLOT, Items.hideTooltip(back));
    }

    private static String pct(double v) {
        return String.format(Locale.US, "%.2f%%", v * 100);
    }

    private static String fmt(long v) {
        return NumberFormat.getIntegerInstance(Locale.US).format(v);
    }

    @Override
    public void onClick(Player player, int slot, InventoryClickEvent event) {
        if (slot != BACK_SLOT) return;
        Bukkit.getScheduler().runTask(feature.plugin(), () -> {
            if (player.isOnline()) player.performCommand("menu");
        });
    }
}
