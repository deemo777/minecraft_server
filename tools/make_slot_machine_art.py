"""Build the one-block-wide, two-block-high brass/red slot cabinet (front = +Z)."""
import json
from pathlib import Path
from PIL import Image, ImageDraw


def slot_model():
    elements = []

    def box(name, start, end, texture, front=None):
        faces = {side: {"texture": "#" + (front if side == "south" and front else texture),
                        "uv": [0, 0, 16, 16]}
                 for side in ("north", "south", "east", "west", "up", "down")}
        elements.append({"name": name, "from": start, "to": end, "faces": faces})

    # The lower shell covers the real lodestone, avoiding exposed vanilla edges/z-fighting.
    box("lower_cabinet", [-.025, -.025, -.025], [16.025, 16.025, 16.025], "body")
    box("brass_foot", [-.05, -.04, -.05], [16.05, .85, 16.05], "gold")
    box("foot_inlay", [.6, .9, 16.03], [15.4, 1.5, 16.15], "dark")
    box("lower_front", [.9, 2, 16.03], [15.1, 12.7, 16.15], "dark")
    box("front_left_trim", [.4, 1.5, 16.03], [.95, 13.6, 16.22], "gold")
    box("front_right_trim", [15.05, 1.5, 16.03], [15.6, 13.6, 16.22], "gold")
    box("payout_rim", [2.1, 3, 16.16], [13.9, 6.2, 16.45], "gold")
    box("payout_opening", [2.7, 3.6, 16.46], [13.3, 5.65, 16.5], "dark", "payout")
    box("payout_lip", [2.1, 2.8, 16.45], [13.9, 3.6, 17.1], "gold")
    box("coin_surround", [9.7, 8.4, 16.16], [13.4, 12.2, 16.45], "gold")
    box("coin_slot", [10.15, 8.8, 16.46], [12.95, 11.8, 16.5], "dark", "coin")
    box("play_button_mount", [2.4, 9.15, 16.16], [7.2, 11.7, 16.5], "gold")
    box("play_button", [2.9, 9.55, 16.51], [6.7, 11.3, 16.85], "green")
    box("control_deck", [-.05, 13.5, 3], [16.05, 15.3, 16.4], "gold")
    box("control_deck_inlay", [.65, 15.31, 3.6], [15.35, 15.5, 15.6], "body")
    box("upper_cabinet", [.65, 15.5, 2], [14.45, 29.1, 14.5], "body")
    box("screen_bezel", [.9, 18.5, 14.51], [14.2, 26.5, 15.1], "gold")
    box("screen_recess", [1.5, 19.1, 15.11], [13.6, 25.9, 15.25], "dark")
    for reel in range(3):
        x = 2 + reel * 3.85
        box(f"ivory_reel_{reel}", [x, 19.7, 15.26], [x + 3.35, 25.3, 15.35], "ivory", f"reel{reel}")
        box(f"payline_{reel}", [x, 22.35, 15.36], [x + 3.35, 22.5, 15.4], "gold")
    box("marquee_frame", [.3, 27.1, 1.7], [14.8, 32, 15], "gold")
    box("marquee_glass", [1, 27.8, 15.01], [14.1, 31.3, 15.15], "dark", "marquee")
    for x in (1.5, 4.35, 7.2, 10.05, 12.9):
        box(f"marquee_bulb_{x}", [x, 31.45, 15.05], [x + .65, 31.9, 15.35], "ivory")
    box("lever_pivot", [14.46, 17.5, 9.3], [15.6, 20.5, 12.3], "gold")
    box("lever_stem", [15.05, 19.5, 10.25], [15.65, 26.2, 10.85], "gold")
    box("lever_red_grip", [14.75, 25.4, 9.8], [16, 27.1, 11.3], "red")
    for x in (1.4, 13.1):
        box(f"upper_side_trim_{x}", [x, 15.6, 14.51], [x + .5, 18.3, 14.8], "gold")
    textures = ("body", "gold", "dark", "payout", "coin", "green", "ivory", "reel0", "reel1", "reel2", "marquee", "red")
    return {
        "credit": "ServerCore original slot cabinet; 1x2 blocks; front +Z",
        "textures": {t: f"servercore:block/slot_{t}" for t in textures}
                    | {"particle": "servercore:block/slot_body"},
        "elements": elements,
        "display": {
            "gui": {"rotation": [20, 30, 0], "translation": [0, -3, 0], "scale": [.43, .43, .43]},
            "ground": {"translation": [0, 2, 0], "scale": [.22, .22, .22]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, -3, 0], "scale": [.4, .4, .4]},
            "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 1, 0], "scale": [.3, .3, .3]},
            "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [.3, .3, .3]},
            "firstperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [.3, .3, .3]},
        },
    }


def textures():
    palette = {"body": (88, 28, 40), "gold": (190, 133, 48), "dark": (25, 31, 36),
               "ivory": (237, 222, 183), "green": (37, 139, 99), "red": (190, 43, 45)}
    result = {}
    for name, color in palette.items():
        image = Image.new("RGBA", (32, 32), (*color, 255)); d = ImageDraw.Draw(image)
        light = tuple(min(255, int(c * 1.18) + 8) for c in color)
        shadow = tuple(int(c * .62) for c in color)
        d.line((0, 0, 31, 0), fill=light, width=2); d.line((0, 0, 0, 31), fill=light)
        d.line((0, 31, 31, 31), fill=shadow, width=2); d.line((31, 0, 31, 31), fill=shadow)
        if name == "body":
            d.rectangle((3, 3, 28, 28), outline=shadow)
            for x, y in ((2, 2), (28, 2), (2, 28), (28, 28)):
                d.rectangle((x, y, x + 1, y + 1), fill=(208, 159, 79))
            for y in (12, 16, 20): d.rectangle((9, y, 22, y + 1), fill=shadow)
        if name == "gold":
            for y in (7, 18, 26): d.line((2, y, 29, y), fill=(208, 154, 65))
        result[name] = image
    image = Image.new("RGBA", (32, 32), (15, 20, 23, 255)); d = ImageDraw.Draw(image)
    d.rectangle((12, 3, 19, 28), fill=(191, 143, 60)); d.rectangle((14, 5, 17, 27), fill=(4, 7, 10))
    d.line((13, 3, 18, 3), fill=(255, 226, 145)); result["coin"] = image
    image = Image.new("RGBA", (32, 32), (8, 12, 16, 255)); d = ImageDraw.Draw(image)
    d.rectangle((0, 0, 31, 5), fill=(3, 5, 7)); d.line((2, 28, 29, 28), fill=(115, 92, 50), width=2)
    result["payout"] = image
    for index, color in enumerate(((35, 156, 107), (238, 179, 52), (72, 187, 210))):
        image = result["ivory"].copy(); d = ImageDraw.Draw(image)
        for y in (3, 28): d.line((6, y, 25, y), fill=(185, 169, 137))
        d.polygon([(16, 8), (25, 14), (23, 21), (16, 25), (8, 21), (6, 14)], fill=tuple(int(c * .55) for c in color))
        d.polygon([(16, 9), (23, 14), (20, 20), (16, 23), (10, 19), (8, 14)], fill=color)
        d.polygon([(16, 9), (23, 14), (16, 15), (8, 14)], fill=tuple(min(255, c + 65) for c in color))
        d.line((16, 15, 16, 22), fill=tuple(int(c * .8) for c in color)); result[f"reel{index}"] = image
    image = Image.new("RGBA", (64, 16), (40, 65, 58, 255)); d = ImageDraw.Draw(image)
    d.rectangle((0, 0, 63, 15), outline=(17, 30, 31), width=2)
    # Hand-built pixel numerals remain sharp without an external font dependency.
    for x in (11, 27, 43):
        d.rectangle((x, 3, x + 10, 5), fill=(255, 225, 145))
        for y in range(6, 13):
            xx = x + 9 - (y - 6)
            d.rectangle((xx, y, xx + 2, y), fill=(255, 225, 145))
    result["marquee"] = image
    return result


def install_slot_machine_assets(pack_dir):
    root = Path(pack_dir) / "assets/servercore"
    for folder in ("textures/block", "models/item", "items"): (root / folder).mkdir(parents=True, exist_ok=True)
    for name, image in textures().items(): image.save(root / f"textures/block/slot_{name}.png")
    (root / "models/item/slot_machine.json").write_text(json.dumps(slot_model(), indent=2), encoding="utf-8")
    item = {"model": {"type": "minecraft:model", "model": "servercore:item/slot_machine"}}
    (root / "items/slot_machine.json").write_text(json.dumps(item, indent=2), encoding="utf-8")


if __name__ == "__main__":
    install_slot_machine_assets(Path(__file__).resolve().parent / "build/servercore_pack")
