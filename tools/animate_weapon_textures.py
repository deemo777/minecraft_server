"""Build UV-preserving weapon surface loops; never overwrite the supplied art."""
import colorsys
import json
import math
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
# Tile order in the shared ImageGen effect atlas. Color comes from each weapon.
THEMES = {
    'metal': (0, (205, 226, 238), 4, 0.32),
    'shadow': (1, (148, 115, 220), 5, 0.32),
    'frost': (2, (182, 240, 255), 3, 0.40),
    'flame': (3, (255, 184, 75), 2, 0.42),
    'blood': (4, (255, 62, 104), 4, 0.38),
    'rune': (5, (132, 231, 255), 4, 0.40),
    'nature': (6, (150, 245, 125), 5, 0.36),
    'celestial': (7, (208, 225, 255), 5, 0.38),
}
# Explicit art direction for every skin without a supplied animated texture.
SKINS = {
    'dagger': {
        'amethyst_greatblade_dagger': 'shadow', 'calamity_blade_dagger': 'flame',
        'demons_blood_blade_dagger': 'blood', 'gloomsteel_knife': 'shadow',
        'iron_dagger': 'metal', 'iron_sai': 'metal', 'nature_sword': 'nature',
        'nocturne_dagger': 'celestial', 'solstice_dagger': 'celestial',
        'talonbrand_dagger': 'flame', 'vindicator': 'metal',
    },
    'greatsword': {
        'berserkers_cleaver': 'flame', 'black_iron_greatsword': 'metal',
        'claymore': 'metal', 'crimson_cleaver': 'blood', 'demonic_sword': 'blood',
        'emerald_greatcleaver': 'nature', 'grand_claymore': 'metal',
        'ravenous_blade': 'blood', 'revenants_gravecleaver': 'flame',
        'runic_piercer': 'rune', 'vesper': 'shadow', 'winterthorn': 'frost',
    },
    'tachi': {
        'calamity_blade_thin': 'flame', 'crystal_frostblade': 'frost',
        'demonslayers_greatsword': 'metal', 'ethereal_frostblade': 'frost',
        'gloomsteel_katana': 'shadow', 'lycanbane': 'metal',
        'moonlight': 'celestial', 'oculus': 'rune', 'uchigatana': 'metal',
        'valhakyra': 'blood', 'vengeance_blade': 'flame',
        'void_oculus': 'shadow', 'zenith': 'nature',
    },
}
ORIGINAL = {
    ('dagger', 'azure_dagger'), ('dagger', 'dragon_sword_dagger'),
    ('dagger', 'spider_sword_dagger'), ('greatsword', 'azure_greatsword'),
    ('greatsword', 'vaelith'), ('tachi', 'azure_sabre'),
}
FRAMES = 16


def texture_paths(model_path):
    model = json.loads(model_path.read_text(encoding='utf8'))
    return sorted({model_path.parent / (ref.rsplit('/', 1)[-1] + '.png')
                   for ref in model['textures'].values() if not ref.startswith('#')})


def animate(source, destination, theme, atlas, emissive=None):
    """Only tint existing opaque detail; retain silhouette, UVs, and frame zero."""
    image = Image.open(source).convert('RGBA')
    width, height = image.size
    if width != height:
        raise ValueError(f'Static texture must be square: {source}')
    tile, tint, frametime, strength = THEMES[theme]
    tw, th = atlas.width // 4, atlas.height // 2
    pattern = atlas.crop(((tile % 4) * tw, (tile // 4) * th,
                          (tile % 4 + 1) * tw, (tile // 4 + 1) * th))
    pattern = pattern.resize((width, height), Image.Resampling.BILINEAR)
    pattern_pixels = list(pattern.getdata())
    # Normalize within a tile so the generated atlas's exposure cannot wash out art.
    lo, hi = min(pattern_pixels), max(pattern_pixels)
    pattern_pixels = [(p - lo) / max(1, hi - lo) for p in pattern_pixels]
    pixels = list(image.getdata())
    emission = list(Image.open(emissive).convert('RGBA').getdata()) if emissive else None
    mask = []
    for i, (r, g, b, a) in enumerate(pixels):
        _, saturation, value = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        # Bright steel or existing colored detail; dark leather stays untouched.
        weight = max(0.0, min(1.0, (value - 0.20) / 0.5))
        if theme == 'metal':
            weight *= max(0.0, 1.0 - saturation * 2)
        else:
            weight *= 0.25 + saturation * 0.75
        if emission and emission[i][3] > 0:
            weight = max(weight, max(emission[i][:3]) / 255 * emission[i][3] / 255)
        mask.append(weight if a else 0.0)
    frames = []
    for frame in range(FRAMES):
        phase = frame / FRAMES
        # Periodic envelope: frame zero is the exact supplied art; wrap is smooth.
        envelope = math.sin(math.pi * phase) ** 2
        out = []
        for i, (r, g, b, a) in enumerate(pixels):
            x, y = i % width, i // width
            scroll = (int(y + phase * height) % height) * width + x
            pattern_value = pattern_pixels[scroll]
            if theme == 'metal':
                # A narrow moving glint, rather than making the whole blade blink.
                distance = ((x / width + y / height - phase * 2) % 1)
                detail = max(0, 1 - min(distance, 1 - distance) / 0.14)
                detail *= 0.55 + pattern_value * 0.45
            elif theme in ('blood', 'rune', 'nature'):
                detail = 0.35 + 0.65 * pattern_value
            else:
                detail = pattern_value ** 1.5
            amount = strength * mask[i] * envelope * detail
            # Preserve the weapon's native hue, tinting only toward its own theme.
            target = tuple(min(255, c + 0.55 * t) for c, t in zip((r, g, b), tint))
            out.append(tuple(round(c + (t - c) * amount) for c, t in zip((r, g, b), target)) + (a,))
        result = Image.new('RGBA', image.size)
        result.putdata(out)
        frames.append(result)
    strip = Image.new('RGBA', (width, height * FRAMES))
    for index, result in enumerate(frames):
        strip.paste(result, (0, index * height))
    strip.save(destination)
    destination.with_name(destination.name + '.mcmeta').write_text(json.dumps({
        'animation': {'frametime': frametime, 'interpolate': True,
                      'width': width, 'height': height},
    }, indent=2) + '\n', encoding='utf8')


def install_animations(source, pack):
    atlas = Image.open(HERE / 'art/weapon_effect_atlas.png').convert('L')
    if atlas.width % 4 or atlas.height % 2:
        raise ValueError('Effect atlas must contain a 4 by 2 grid')
    models = {(p.parent.name, p.stem) for p in source.glob('*/*.json')}
    generated = {(category, name) for category, skins in SKINS.items() for name in skins}
    if generated & ORIGINAL or models != generated | ORIGINAL:
        raise ValueError('Every weapon must have exactly one animation policy')
    count = 0
    for category, name in sorted(generated):
        model = source / category / (name + '.json')
        for texture in texture_paths(model):
            if texture.with_name(texture.name + '.mcmeta').exists():
                raise ValueError(f'Refusing to replace original animation: {texture}')
            destination = pack / 'assets/servercore/textures/item/weapons' / category / texture.name
            emission = texture.with_name(texture.stem + '_e.png')
            animate(texture, destination, SKINS[category][name], atlas,
                    emission if emission.exists() else None)
            count += 1
    print(f'  Weapon animation: {len(ORIGINAL)} original skins preserved, '
          f'{len(generated)} themed skins / {count} animated textures')
