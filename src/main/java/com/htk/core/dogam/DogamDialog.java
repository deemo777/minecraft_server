package com.htk.core.dogam;

import com.htk.core.common.Dialogs;
import com.htk.core.common.Glyphs;
import com.htk.core.common.Text;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 도감 대화창 한 페이지 (기본 15칸 × 7줄)
 *
 *   도감 진행도 (91/300)   2 / 3
 *   [■■■■■■■■□□□□□□□□□□□□]
 *   □ □ □ □ □ □ □ □ □ □ □ □ □ □ □
 *   □ □ □ □ □ □ □ □ □ □ □ □ □ □ □
 *   □ □ □ □ □ □ □ □ □ □ □ □ □ □ □
 *   ◀ □ □ □ □ □ □ □ □ □ □ □ □ □ ▶   ← 가운데 줄 양 끝이 페이지 버튼
 *   □ □ □ □ □ □ □ □ □ □ □ □ □ □ □
 *   ...
 *                [닫기]
 */
public final class DogamDialog {

    private static final TextColor COUNT_DONE = TextColor.color(0xD8D8D8);
    private static final TextColor COUNT_TODO = TextColor.color(0xFF5555);

    private final DogamFeature feature;
    private final Player player;
    private final boolean pack;
    private final int columns, rows, width, perPage, pages, page, prevCell, nextCell;
    private final boolean showOne;

    public DogamDialog(DogamFeature feature, Player player, int requestedPage) {
        this.feature = feature;
        this.player = player;
        this.pack = feature.plugin().resourcePackEnabled();

        var cfg = feature.config();
        this.columns = Math.max(3, cfg.getInt("grid.columns", 15));
        this.rows = Math.max(1, cfg.getInt("grid.rows", 7));
        this.width = Dialogs.clampWidth(cfg.getInt("grid.button-width", 20));
        this.showOne = cfg.getBoolean("show-count-when-one", true);

        int arrowRow = rows / 2;
        this.prevCell = arrowRow * columns;
        this.nextCell = arrowRow * columns + columns - 1;
        this.perPage = columns * rows - 2;

        int total = feature.registry().all().size();
        this.pages = Math.max(1, (total + perPage - 1) / perPage);
        this.page = Math.max(0, Math.min(requestedPage, pages - 1));
    }

    public Dialog build() {
        List<CollectionRegistry.Entry> all = feature.registry().all();
        PlayerDataStore.PlayerData data = feature.store().get(player.getUniqueId());

        List<ActionButton> buttons = new ArrayList<>(columns * rows);
        int slot = 0;
        for (int cell = 0; cell < columns * rows; cell++) {
            if (cell == prevCell) {
                buttons.add(arrow(false));
            } else if (cell == nextCell) {
                buttons.add(arrow(true));
            } else {
                int i = page * perPage + slot++;
                buttons.add(i < all.size() ? entryButton(all.get(i), data) : blank());
            }
        }

        // 동작 없음(null) = 서버 응답을 기다리지 않고 바로 닫혀요
        ActionButton close = ActionButton.create(
                Text.mm(feature.config().getString("close-label", "닫기")), null, 100, null);

        DialogBase base = DialogBase.builder(title())
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE) // 클릭 후 서버가 새 창을 보낼 때까지 대기
                .body(List.of(DialogBody.plainMessage(progressBar())))
                .build();

        return Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(buttons, close, columns)));
    }

    // ---------- 제목 / 진행도 ----------

    private Component title() {
        int found = feature.foundTotal(player), total = feature.registry().all().size();
        String t = feature.config().getString("title", "도감 진행도 (%found%/%total%)")
                .replace("%found%", String.valueOf(found))
                .replace("%total%", String.valueOf(total));
        String p = feature.config().getString("page-format", "%page% / %pages%")
                .replace("%page%", String.valueOf(page + 1))
                .replace("%pages%", String.valueOf(pages));
        return Text.mm(t + "   " + p);
    }

    private Component progressBar() {
        int found = feature.foundTotal(player), total = feature.registry().all().size();
        if (pack) return Glyphs.progressBar(found, total);
        int filled = total == 0 ? 0 : Math.round(found * 20f / total);
        return Text.mm("<#cdbf8e>" + "■".repeat(filled) + "<#3a3530>" + "■".repeat(20 - filled));
    }

    // ---------- 칸 ----------

    private ActionButton entryButton(CollectionRegistry.Entry e, PlayerDataStore.PlayerData data) {
        boolean done = data.has(e.id());
        int have = done ? 0 : feature.count(player, e.material());
        return Dialogs.button(feature.plugin(), label(e, done), tooltip(e, done, have), width,
                p -> feature.click(p, e, page));
    }

    /** [아이콘] + (등록됐으면 체크를 같은 자리에 겹침) + 오른쪽 아래 숫자 */
    private Component label(CollectionRegistry.Entry e, boolean done) {
        if (!pack) {
            return done ? Component.text("✔", TextColor.color(0x55FF55))
                        : Component.text(Text.plain(e.name()).isEmpty() ? "?" : Text.plain(e.name()).substring(0, 1),
                                         NamedTextColor.GRAY);
        }
        StringBuilder g = new StringBuilder().append(Glyphs.icon(e.index()));
        if (done) g.append(Glyphs.space(-Glyphs.ICON_ADVANCE)).append(Glyphs.CHECK);
        Component label = Glyphs.of(g.toString());

        if (e.required() > 1 || showOne) {
            String digits = Glyphs.digits(e.required());
            int w = digits.length() * Glyphs.DIGIT_ADVANCE;
            label = label.append(Glyphs.of(Glyphs.space(-w) + digits, done ? COUNT_DONE : COUNT_TODO));
        }
        return label;
    }

    private Component tooltip(CollectionRegistry.Entry e, boolean done, int have) {
        List<String> lines = new ArrayList<>();
        lines.add(e.name());
        CollectionRegistry.Category cat = feature.registry().category(e.categoryId());
        if (cat != null) lines.add("<dark_gray>" + Text.plain(cat.name()));
        if (!e.description().isEmpty()) {
            lines.add("");
            lines.addAll(e.description());
        }
        lines.add("");
        if (done) {
            lines.add(feature.msg("tooltip-registered"));
        } else {
            lines.add(feature.msg("tooltip-unregistered")
                    .replace("%required%", String.valueOf(e.required()))
                    .replace("%have%", String.valueOf(have)));
            lines.add(feature.msg("tooltip-click"));
        }
        return Text.lines(lines);
    }

    private ActionButton arrow(boolean next) {
        boolean active = next ? page < pages - 1 : page > 0;
        Component label;
        if (pack) {
            char ch = next ? (active ? Glyphs.ARROW_RIGHT : Glyphs.ARROW_RIGHT_OFF)
                           : (active ? Glyphs.ARROW_LEFT : Glyphs.ARROW_LEFT_OFF);
            label = Glyphs.of(String.valueOf(ch));
        } else {
            label = Component.text(next ? "▶" : "◀", active ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY);
        }
        Component tip = active ? Text.mm(feature.msg(next ? "next-page" : "prev-page")) : null;
        int target = active ? (next ? page + 1 : page - 1) : page;
        return Dialogs.button(feature.plugin(), label, tip, width, p -> feature.open(p, target));
    }

    /** 마지막 페이지의 빈 칸 (누르면 그냥 같은 페이지) */
    private ActionButton blank() {
        return Dialogs.button(feature.plugin(), Component.empty(), null, width, p -> feature.open(p, page));
    }
}
