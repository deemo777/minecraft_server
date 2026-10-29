package com.htk.core.common;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;

/**
 * 리소스팩 폰트(servercore:gui) 그림 글자와 아이템 모델 이름.
 *
 * ⚠ tools/build_pack.py 와 "약속"이에요. 한쪽을 바꾸면 다른 쪽도 같이 바꿔야 해요.
 *
 *   \uE020~\uE023  도감 배경 4조각 (전체 176 x (20+222), 조각 88 x 121: 왼위, 오른위, 왼아래, 오른아래)
 *   \uE024~\uE027  메뉴 배경 4조각 (전체 176 x (20+204), 조각 88 x 112)
 *     ※ 폰트 그림 한 장이 256px를 넘으면 마인크래프트가 못 그려서 조각으로 나눴어요
 *   \uE002  진행도 게이지 조각 (4 x 5, 윗변 y=114)
 *   \uE010~\uE019  숫자 0~9 (4 x 7, 윗변 y=113),  \uE01A  '/',  \uE01B  ',',  \uE01C  'G'
 *   \uE028~\uE02B  스킬 창 배경 4조각 (전체 176 x (20+222))
 *   \uE02C~\uE02F  집 지도 창 배경 4조각 (전체 176 x (20+222))
 *   \uE03C~\uE03F  이웃 허가 창 배경 4조각 (전체 176 x (20+222))
 *   \uE060~\uE064  스킬 창 게이지 조각 (줄마다 높이가 다른 4 x 5)
 *   servercore:skill1~5  스킬 창 줄마다 높이가 다른 기본 글꼴 (Lv.·경험치 숫자)
 *   \uE030~        상점 배경 4조각씩 (SHOP_THEMES 순서: fish \uE030~33, crops \uE034~37, ores \uE038~3B)
 *   \uF801~\uF808  왼쪽으로 1,2,4,...,128px  /  \uF811~\uF818  오른쪽으로
 *
 *   아이템 모델: servercore:checked/<아이템>, servercore:arrow_left(_off), servercore:arrow_right(_off),
 *               servercore:invisible, servercore:gold_coin (G 금화),
 *               servercore:player_head_front (메뉴의 내 머리, 얼굴 정면),
 *               servercore:land_plus(_off) (집 지도의 "+ 넓히기"),
 *               servercore:checked_head (체크 표시가 붙은 플레이어 머리 — 이웃 허가)
 */
public final class Glyphs {

    public static final Key FONT = Key.key("servercore", "gui");

    /** 배경 4조각의 첫 글자 (왼위) — 나머지는 +1, +2, +3 */
    public static final char DOGAM_BG = '\uE020';
    public static final char MENU_BG = '\uE024';
    public static final char SKILL_BG = '\uE028';
    public static final char LAND_BG = '\uE02C';
    public static final char MEMBERS_BG = '\uE03C';
    public static final char BAR_PIECE = '\uE002';
    public static final char DIGIT_0 = '\uE010';
    public static final char SLASH = '\uE01A';
    public static final char COMMA = '\uE01B';
    public static final char LETTER_G = '\uE01C';

    /** 상점 배경 테마 (⚠ tools/build_pack.py 의 SHOP_THEMES 와 같은 순서) */
    public static final java.util.List<String> SHOP_THEMES = java.util.List.of("fish", "crops", "ores");
    private static final char SHOP_BG_FIRST = '\uE030';

    /** 상점 배경 첫 조각. 모르는 테마면 도감 배경 */
    public static char shopBackground(String theme) {
        int i = SHOP_THEMES.indexOf(theme == null ? "" : theme.toLowerCase());
        return i < 0 ? DOGAM_BG : (char) (SHOP_BG_FIRST + 4 * i);
    }

    public static final int BG_WIDTH = 176;
    public static final int BG_TILE_WIDTH = BG_WIDTH / 2;
    public static final int BAR_PIECE_WIDTH = 4;
    public static final int DIGIT_WIDTH = 4;

    /** 마인크래프트 기본 글꼴(ascii.png) 글자 폭+1, 문자 33('!')~126('~') — 26.3 jar 에서 잰 값 */
    private static final String ASCII_ADV =
            "2466662444626266666666666225656766666666466666666666666666464663666665662653666666646666664247";

    /** 기본 글꼴로 썼을 때의 폭 (px) */
    public static int asciiWidth(String s) {
        int w = 0;
        for (char ch : s.toCharArray()) {
            if (ch == ' ') w += 4;
            else if (ch >= 33 && ch <= 126) w += ASCII_ADV.charAt(ch - 33) - '0';
            else w += 6;
        }
        return w;
    }

    /** 상자 GUI 제목이 그려지기 시작하는 x 위치 (마인크래프트 고정값) */
    public static final int TITLE_X = 8;

    private Glyphs() {}

    public static NamespacedKey model(String path) {
        return new NamespacedKey("servercore", path);
    }

    /** px만큼 이동하는 보이지 않는 글자 (음수 = 왼쪽) */
    public static String space(int px) {
        StringBuilder sb = new StringBuilder();
        int n = Math.abs(px);
        char base = px < 0 ? '\uF801' : '\uF811';
        for (int bit = 7; bit >= 0; bit--) {
            int v = 1 << bit;
            while (n >= v) {
                sb.append((char) (base + bit));
                n -= v;
            }
        }
        return sb.toString();
    }

    /**
     * 상자 GUI 제목 자리에 그림을 "좌표로" 배치하는 도구.
     * x는 GUI 왼쪽 끝 기준 픽셀이에요. 그림 글자는 (그림 너비 + 1)만큼 이동한다는 규칙으로 계산해요.
     */
    public static final class Canvas {
        private final StringBuilder glyphs = new StringBuilder();
        private final java.util.List<Component> parts = new java.util.ArrayList<>();
        private int pen = TITLE_X;

        /** 지금 x 위치 */
        public int pen() {
            return pen;
        }

        /** 다른 글꼴(예: 줄마다 높이를 바꾼 기본 글꼴)의 글자를 x 에서부터 */
        public Canvas text(int x, String s, Key font, TextColor color) {
            moveTo(x);
            parts.add(Component.text(glyphs.toString()).font(FONT));
            glyphs.setLength(0);
            parts.add(Component.text(s).font(font).color(color));
            pen += asciiWidth(s);
            return this;
        }

        /** 오른쪽 끝을 right 에 맞춰서 */
        public Canvas textRight(int right, String s, Key font, TextColor color) {
            return text(right - asciiWidth(s) + 1, s, font, color);
        }

        /** 게이지 조각 글자를 골라서 (줄마다 높이가 다른 조각용) */
        public Canvas bar(char piece, int x, int pieces, long found, long total) {
            int filled = total <= 0 ? 0 : (int) Math.round((double) found / total * pieces);
            filled = Math.max(0, Math.min(pieces, filled));
            moveTo(x);
            for (int i = 0; i < filled; i++) {
                draw(piece, BAR_PIECE_WIDTH);
                moveTo(pen - 2); // width+1 advance → overlap the previous piece by 1px
            }
            return this;
        }

        public Canvas moveTo(int x) {
            glyphs.append(space(x - pen));
            pen = x;
            return this;
        }

        public Canvas draw(char glyph, int width) {
            glyphs.append(glyph);
            pen += width + 1;
            return this;
        }

        /** 배경 4조각 깔기 (GUI 왼쪽 끝 기준) */
        public Canvas background(char first) {
            for (int i = 0; i < 4; i++) {
                moveTo(i % 2 == 0 ? 0 : BG_TILE_WIDTH);
                draw((char) (first + i), BG_TILE_WIDTH);
            }
            return this;
        }

        /** 진행도 막대: x부터 pieces칸, 그중 filled칸을 채워요 (조각끼리 1px 겹쳐 이음) */
        public Canvas bar(int x, int pieces, int found, int total) {
            int filled = total <= 0 ? 0 : Math.round((float) found / total * pieces);
            filled = Math.max(0, Math.min(pieces, filled));
            moveTo(x);
            for (int i = 0; i < filled; i++) {
                draw(BAR_PIECE, BAR_PIECE_WIDTH);
                moveTo(pen - 2);
            }
            return this;
        }

        /** 숫자 글자들 (0~9, '/', ',', 'G' — 예: "2/12", "1,234G"), centerX를 가운데로 */
        public Canvas number(String text, int centerX) {
            int w = text.length() * (DIGIT_WIDTH + 1) - 1;
            int x = centerX - w / 2;
            for (char ch : text.toCharArray()) {
                char g;
                if (ch >= '0' && ch <= '9') g = (char) (DIGIT_0 + (ch - '0'));
                else if (ch == '/') g = SLASH;
                else if (ch == ',') g = COMMA;
                else if (ch == 'G') g = LETTER_G;
                else g = 0;
                if (g != 0) {
                    moveTo(x);   // 글자마다 자리를 다시 맞춰요 (글자 폭이 조금 달라도 줄이 안 밀리게)
                    draw(g, DIGIT_WIDTH);
                }
                x += DIGIT_WIDTH + 1;
            }
            return this;
        }

        /** 그림들을 모두 그린 뒤 제목 글자는 원래 자리(x=8)에서 시작하게 */
        public Component build(Component titleText, TextColor glyphColor) {
            moveTo(TITLE_X);
            Component art = Component.text(glyphs.toString()).font(FONT).color(glyphColor);
            if (!parts.isEmpty()) {
                // 글꼴이 섞여 있으면: 그림 조각(흰색) + 글자 조각(각자 색)을 순서대로
                Component all = Component.empty().color(glyphColor);
                for (Component c : parts) all = all.append(c);
                art = all.append(art);
            }
            // 제목 글자를 art의 "자식"으로 붙이면 그림 폰트를 물려받아 □로 나와요 → 형제로 나란히
            return titleText == null ? art : Component.empty().append(art).append(titleText);
        }

        public Component build(Component titleText) {
            return build(titleText, NamedTextColor.WHITE);
        }
    }
}
