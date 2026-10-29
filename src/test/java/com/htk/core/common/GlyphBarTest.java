package com.htk.core.common;

import net.kyori.adventure.text.TextComponent;

/** Simulate bitmap font advances to catch bars that extend past their painted track. */
public final class GlyphBarTest {
    public static void main(String[] args) {
        int checks = 0;
        long[][] values = {{0,93}, {147,197}, {138,142}, {69,93}, {1,1}, {999,1}, {-1,100}, {10,0}};
        for (int row = 0; row < 5; row++) for (long[] value : values) {
            char piece = (char)(0xE060 + row);
            var canvas = new Glyphs.Canvas().bar(piece,29,45,value[0],value[1]);
            String text = ((TextComponent)canvas.build(null)).content();
            int x = Glyphs.TITLE_X, count = 0, right = 29;
            for (char ch : text.toCharArray()) {
                if (ch >= 0xF801 && ch <= 0xF808) x -= 1 << (ch - 0xF801);
                else if (ch >= 0xF811 && ch <= 0xF818) x += 1 << (ch - 0xF811);
                else if (ch == piece) {
                    if (x != 29 + count * 3) throw new AssertionError("bar pieces must overlap by 1px");
                    right = x + 4; x += 5; count++;
                } else throw new AssertionError("unexpected glyph");
            }
            int expected = value[1] <= 0 ? 0 : Math.clamp((int)Math.round((double)value[0]/value[1]*45),0,45);
            if (count != expected || right > 165 || x != Glyphs.TITLE_X)
                throw new AssertionError("bar bounds/count/title origin mismatch");
            checks++;
        }
        var collection = new Glyphs.Canvas().bar(29,45,1,1);
        if (collection.pen() != 164) throw new AssertionError("collection bar uses the same overlap");
        System.out.println("Glyph bar regression checks passed: " + (checks + 1));
    }
}
