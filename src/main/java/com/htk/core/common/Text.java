package com.htk.core.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;

/** MiniMessage 문자열 → 화면에 보이는 글자(Component) */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {}

    public static Component mm(String miniMessage) {
        return MM.deserialize(CommandNames.inText(miniMessage));
    }

    /** 여러 줄을 줄바꿈으로 이어 붙인 하나의 Component (툴팁용) */
    public static Component lines(List<String> lines) {
        return mm(String.join("\n", lines));
    }

    public static List<String> list(String... lines) {
        return new ArrayList<>(List.of(lines));
    }

    /** MiniMessage 태그를 뺀 순수 글자 */
    public static String plain(String miniMessage) {
        return MM.stripTags(miniMessage == null ? "" : miniMessage);
    }
}
