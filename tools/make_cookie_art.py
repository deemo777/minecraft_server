"""Deemo777 스킨 테마의 귀 달린 둥근 아이싱 쿠키. 네트워크 요청 없음."""
from pathlib import Path
from PIL import Image, ImageDraw
import json
HERE = Path(__file__).resolve().parent

def install_cookie_assets(pack):
    # 둥근 회색 동물 얼굴을 쿠키 틀과 아이싱으로 표현한다.
    # 사각 스킨을 붙이지 않고 귀·볼·크림 주둥이까지 새로 그린다.
    im = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(im)
    outline = "#694c3d"
    baked = "#b98151"
    dough = "#e3b77d"
    # 둥근 귀도 쿠키 반죽과 이어지는 외곽 형태.
    for box in [(3, 1, 12, 12), (19, 1, 28, 12)]:
        d.ellipse(box, fill=outline)
    d.ellipse((2, 5, 29, 30), fill=outline)
    for box in [(4, 2, 11, 11), (20, 2, 27, 11)]:
        d.ellipse(box, fill=dough)
    d.ellipse((3, 6, 28, 29), fill=baked)
    d.ellipse((3, 6, 28, 27), fill=dough)
    # 차콜 흑임자 아이싱, 얇은 반죽 테두리와 위쪽 반사광.
    for box in [(5, 3, 10, 10), (21, 3, 26, 10)]:
        d.ellipse(box, fill="#696268")
    for box in [(6, 4, 9, 9), (22, 4, 25, 9)]:
        d.ellipse(box, fill="#d59ca1")
    d.ellipse((4, 7, 27, 27), fill="#57525a")
    d.ellipse((4, 7, 27, 25), fill="#777078")
    d.arc((5, 7, 26, 25), 190, 295, fill="#a39aa1", width=1)
    # 부드러운 크림색 주둥이와 스킨의 작은 검정 코.
    d.ellipse((9, 16, 22, 26), fill="#b1a6a0")
    d.ellipse((9, 17, 22, 25), fill="#f0dfc7")
    d.rectangle((12, 17, 19, 21), fill="#f0dfc7")
    d.rectangle((14, 17, 17, 18), fill="#35303a")
    d.point((15, 19), fill="#35303a")
    d.line((15, 20, 15, 22), fill="#8c695e")
    d.point((14, 22), fill="#8c695e")
    d.point((16, 22), fill="#8c695e")
    # 작은 초콜릿 눈, 하이라이트와 딸기 아이싱 볼.
    for x in [10, 20]:
        d.rectangle((x, 12, x+1, 15), fill="#302c34")
        d.point((x, 12), fill="#fff1da")
    for x in [6, 23]:
        d.line((x, 17, x+2, 17), fill="#ce9297", width=2)
    # 스킨 하단의 금색 장식을 작은 꿀색 아이싱으로 반영.
    for x in [8, 22]:
        d.rectangle((x, 22, x+1, 24), fill="#d7aa54")
        d.point((x, 22), fill="#ffe2a1")
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
