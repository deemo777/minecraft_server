"""Deemo777 스킨 테마의 실제 얼굴을 보존한 둥근 아이싱 쿠키. 네트워크 요청 없음."""
from pathlib import Path
from PIL import Image, ImageDraw
import json
HERE = Path(__file__).resolve().parent

def install_cookie_assets(pack):
    # 실제 얼굴 8x8과 바깥 레이어를 합성하고, 픽셀을 바꾸지 않고 3배 확대.
    skin = Image.open(HERE / "art/deemo777_skin.png").convert("RGBA")
    face = skin.crop((8, 8, 16, 16))
    face.alpha_composite(skin.crop((40, 8, 48, 16)))
    face = face.resize((24, 24), Image.Resampling.NEAREST)
    im = Image.new("RGBA", (40, 40))
    d = ImageDraw.Draw(im)
    # 둥근 코코아 반죽과 꿀색 가장자리. 회색 아이싱을 얼굴까지 연결.
    d.ellipse((1, 1, 38, 38), fill="#654536")
    d.ellipse((2, 2, 37, 37), fill="#b7804b")
    d.ellipse((2, 2, 37, 35), fill="#dbb078")
    d.ellipse((4, 4, 35, 35), fill="#4a4442")
    d.ellipse((4, 4, 35, 33), fill="#655f5d")
    d.arc((4, 4, 35, 33), 195, 285, fill="#88807a", width=1)
    # 스킨 얼굴은 그대로 유지하고 장식은 바깥 테두리에만 배치.
    for x,y in [(6, 10), (32, 10), (3, 18), (35, 18)]:
        d.point((x, y), fill="#e6bc81")
    for x,y in [(8, 33), (30, 33)]:
        d.line((x, y, x+1, y), fill="#d8978a")
    im.alpha_composite(face, (8, 8))
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
