# Weapon surface effect atlas

`weapon_effect_atlas.png` is a shared grayscale modulation atlas created using the
built-in ImageGen tool on 2026-10-31. Normalized to 1024×512 grayscale for the pack
builder. It is a build input, not a replacement for any supplied weapon texture.

Prompt: Create a production grayscale game VFX modulation texture atlas with a
4-column, 2-row grid of square tiles, no gaps, labels, borders, text, objects or
weapon silhouettes. Restrained fine pixel texture with predominantly dark tones
and sparse bright detail. Reading order: brushed diagonal metal glints, curling
shadow magic, crystalline frost facets, molten flame veins, organic blood veins,
runic filaments without readable symbols, leaf/vine energy, celestial mist and
star glints. Request tileable patterns with moderate contrast and no large white
areas. Final weapon color, alpha, UVs and loop timing are controlled by the builder.

Reproduce the final outputs with `tools/build_pack.py`; tune per-weapon themes in
`tools/animate_weapon_textures.py`. Source PNGs and existing `.mcmeta` loops remain
untouched. The pattern is scrolled with a periodic envelope that reaches zero at
the wrap, so even a source tile with imperfect edge matching cannot cause a jump
at the loop boundary.
