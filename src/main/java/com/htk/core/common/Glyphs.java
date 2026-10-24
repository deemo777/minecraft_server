package com.htk.core.common;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * 리소스팩 폰트(servercore:gui)의 그림 글자들.
 *
 * ⚠ 이 번호들은 tools/build_pack.py 와 "약속"이에요. 한쪽을 바꾸면 다른 쪽도 같이 바꿔야 해요.
 *
 *           체크 표시 (16x16)
 *           진행도 막대 틀 (152x9)
 *           진행도 막대 채움 조각 (5x7)
 *   / 화살표 ◀ / ▶ (16x16)
 *   / 화살표 ◀ / ▶ 비활성 (16x16)
 *   ~ 숫자 0~9 (4x7, 흰색 → 글자 색으로 칠해짐)
 *    + n    도감 n번째 항목 아이콘 (16x16, dogam.yml 순서)
 *   ~ 왼쪽으로 1,2,4,...,128px
 *   ~ 오른쪽으로 1,2,4,...,128px
 */
public final class Glyphs {

    public static final Key FONT = Key.key("servercore", "gui");

    public static final char CHECK = '';
    public static final char BAR_TRACK = '';
    public static final char BAR_FILL = '';
    public static final char ARROW_LEFT = '';
    public static final char ARROW_RIGHT = '';
    public static final char ARROW_LEFT_OFF = '';
    public static final char ARROW_RIGHT_OFF = '';
    public static final char DIGIT_0 = '';
    public static final char ICON_BASE = '';

    /** 아이콘·체크·화살표 한 칸의 이동 거리 (16px 그림 + 글자 간격 1px) */
    public static final int ICON_ADVANCE = 17;
    /** 숫자 한 칸의 이동 거리 (4px + 1px) */
    public static final int DIGIT_ADVANCE = 5;
    /** 진행도 막대: 틀 이동 거리, 채움 조각 개수, 조각 너비 */
    public static final int BAR_TRACK_ADVANCE = 153;
    public static final int BAR_PIECES = 30;
    public static final int BAR_PIECE_WIDTH = 5;

    private Glyphs() {}

    public static char icon(int index) {
        return (char) (ICON_BASE + index);
    }

    /** px만큼 이동하는 보이지 않는 글자 (음수 = 왼쪽) */
    public static String space(int px) {
        StringBuilder sb = new StringBuilder();
        int n = Math.abs(px);
        char base = px < 0 ? '' : '';
        for (int bit = 7; bit >= 0; bit--) {
            int v = 1 << bit;
            while (n >= v) {
                sb.append((char) (base + bit));
                n -= v;
            }
        }
        return sb.toString();
    }

    public static String digits(int number) {
        StringBuilder sb = new StringBuilder();
        for (char ch : String.valueOf(number).toCharArray()) sb.append((char) (DIGIT_0 + (ch - '0')));
        return sb.toString();
    }

    /** 리소스팩 폰트로 그려지는 글자 (흰색이어야 그림 색이 그대로 나와요) */
    public static Component of(String glyphs) {
        return of(glyphs, NamedTextColor.WHITE);
    }

    public static Component of(String glyphs, TextColor color) {
        return Component.text(glyphs).font(FONT).color(color);
    }

    /**
     * 진행도 막대: 틀을 그리고 → 왼쪽으로 돌아가서 → 조각을 채우고 → 남은 만큼 오른쪽으로.
     * 전체 폭이 항상 같아서 가운데 정렬이 흔들리지 않아요.
     */
    public static Component progressBar(int found, int total) {
        int filled = total <= 0 ? 0 : Math.round((float) found / total * BAR_PIECES);
        filled = Math.max(0, Math.min(BAR_PIECES, filled));
        StringBuilder sb = new StringBuilder();
        sb.append(BAR_TRACK).append(space(-(BAR_TRACK_ADVANCE - 1)));
        for (int i = 0; i < filled; i++) sb.append(BAR_FILL).append(space(-1));
        sb.append(space((BAR_PIECES - filled) * BAR_PIECE_WIDTH + 1));
        return of(sb.toString());
    }
}
