"""고정된 Deemo777 스킨을 이용해 도트 쿠키를 생성. 빌드 시 네트워크 불필요."""
from pathlib import Path
from PIL import Image, ImageDraw
import json
HERE = Path(__file__).resolve().parent

def install_cookie_assets(pack):
    skin = Image.open(HERE / "art/deemo777_skin.png").convert("RGBA")
    face = skin.crop((8, 8, 16, 16))
    face.alpha_composite(skin.crop((40, 8, 48, 16)))
    face = face.resize((16, 16), Image.Resampling.NEAREST)
    im = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(im)
    # 角を段階的に削った丸い焼き菓子。外周は焼き色、表面は明るいバター色。
    for y in range(32):
        for x in range(32):
            distance = (x - 15.5)**2 + (y - 15.5)**2
            if distance <= 14.6**2:
                edge = distance > 13.2**2
                palette = [(143, 82, 43), (174, 110, 55)] if edge else [(225, 174, 101), (233, 186, 116), (217, 159, 86)]
                im.putpixel((x, y), (*palette[(x * 7 + y * 11) % len(palette)], 255))
    for x,y in [(8,6),(22,7),(5,14),(25,15),(8,25),(21,26),(15,4)]:
        d.rectangle((x,y,x+1,y+1), fill=(160,102,58,255))
    # 顔は中央に大きく。外側のクッキー生地を残して円形を読みやすくする。
    im.alpha_composite(face,(8,8))
    dest = Path(pack) / "assets/servercore"
    for sub in ["textures/item", "models/item", "items"]: (dest/sub).mkdir(parents=True, exist_ok=True)
    im.save(dest / "textures/item/deemo_cookie.png")
    model = {"parent":"minecraft:item/generated", "textures":{"layer0":"servercore:item/deemo_cookie"}}
    (dest/"models/item/deemo_cookie.json").write_text(json.dumps(model,indent=2),encoding="utf-8")
    item = {"model":{"type":"minecraft:model","model":"servercore:item/deemo_cookie"}}
    (dest/"items/deemo_cookie.json").write_text(json.dumps(item,indent=2),encoding="utf-8")
    return im

if __name__ == "__main__":
    import sys
    install_cookie_assets(sys.argv[1])
