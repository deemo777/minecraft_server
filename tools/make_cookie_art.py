"""Deemo777 스킨 테마의 실제 얼굴을 보존한 둥근 아이싱 쿠키. 네트워크 요청 없음."""
from pathlib import Path
from PIL import Image, ImageDraw
import json
import math
HERE = Path(__file__).resolve().parent

def install_cookie_assets(pack):
    # 실제 얼굴 8x8과 바깥 레이어를 합성하고, 픽셀을 바꾸지 않고 3배 확대.
    skin = Image.open(HERE / "art/deemo777_skin.png").convert("RGBA")
    face = skin.crop((8, 8, 16, 16))
    face.alpha_composite(skin.crop((40, 8, 48, 16)))
    face = face.resize((24, 24), Image.Resampling.NEAREST)
    im = Image.new("RGBA", (40, 40))
    d = ImageDraw.Draw(im)
    # 캐릭터 팔레트만 사용한 차콜 쿠키와 흰 마시멜로 층.
    d.ellipse((1, 1, 38, 38), fill="#4a4442")
    d.ellipse((2, 2, 37, 37), fill="#9e9691")
    d.ellipse((2, 2, 37, 35), fill="#faf8f3")
    d.ellipse((4, 4, 35, 33), fill="#4a4442")
    d.ellipse((4, 4, 35, 32), fill="#655f5d")
    d.arc((4, 4, 35, 32), 195, 285, fill="#9e9691", width=1)
    # 분홍 장식은 실제 스킨 귀 색을 그대로 사용.
    for x,y in [(6, 10), (32, 10), (8, 33), (30, 33)]:
        d.line((x, y, x+1, y), fill="#d8978a")
    im.alpha_composite(face, (8, 8))
    dest = Path(pack) / "assets/servercore"
    for sub in ["textures/item", "models/item", "items"]: (dest/sub).mkdir(parents=True, exist_ok=True)
    im.save(dest / "textures/item/deemo_cookie.png")
    # 앞뒤 쿠키 사이에 흰 크림이 있는 실제 두께의 모델.
    for name, color in [("cookie_charcoal", "#4a4442"), ("cookie_cream", "#faf8f3")]:
        Image.new("RGBA", (2, 2), color).save(dest / f"textures/item/{name}.png")
    elements = []
    for y in range(40):
        pixels = [x for x in range(40) if im.getpixel((x,y))[3] > 0]
        if not pixels: continue
        lo, hi = min(pixels), max(pixels) + 1
        uv = [lo*.4, y*.4, hi*.4, (y+1)*.4]
        for z0,z1,side in [(6.5,7.2,"#charcoal"), (7.2,8.8,"#cream"), (8.8,9.5,"#charcoal")]:
            faces = {f:{"texture":side,"uv":[0,0,16,16]} for f in ["up","down","east","west","north","south"]}
            if z0 == 6.5: faces["north"] = {"texture":"#layer0", "uv":[uv[2],uv[1],uv[0],uv[3]]}
            if z1 == 9.5: faces["south"] = {"texture":"#layer0", "uv":uv}
            elements.append({"from":[lo*.4,16-(y+1)*.4,z0],"to":[hi*.4,16-y*.4,z1],"faces":faces})
    model = {"textures":{"particle":"servercore:item/deemo_cookie", "layer0":"servercore:item/deemo_cookie", "charcoal":"servercore:item/cookie_charcoal", "cream":"servercore:item/cookie_cream"},
        "elements":elements,"gui_light":"front",
        "display":{"gui":{"rotation":[0,0,0],"scale":[1,1,1]},
        "firstperson_righthand":{"rotation":[0,-8,-5],"translation":[0,5,-1],"scale":[.38,.38,.38]},
        "firstperson_lefthand":{"rotation":[0,-8,-5],"translation":[0,5,-1],"scale":[.38,.38,.38]},
        "thirdperson_righthand":{"rotation":[0,0,0],"translation":[0,3,1],"scale":[.34,.34,.34]},
        "thirdperson_lefthand":{"rotation":[0,0,0],"translation":[0,3,1],"scale":[.34,.34,.34]},
        "ground":{"translation":[0,2,0],"scale":[.34,.34,.34]},"fixed":{"scale":[1,1,1]}}}
    (dest/"models/item/deemo_cookie.json").write_text(json.dumps(model,indent=2),encoding="utf-8")
    # 바닐라 먹기 회전 Ry(90f) Rx(10f) Rz(30f)을 모델에서 역보정.
    # 행렬을 XYZ 각도로 변환하고 0.5틱 간격으로 시작 회전도 완화.
    def matmul(a,b):
        return [[sum(a[i][k]*b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    def rotate(axis,degrees):
        a=math.radians(degrees);c=math.cos(a);v=math.sin(a)
        return ([[1,0,0],[0,c,-v],[0,v,c]] if axis=="x" else
                [[c,0,v],[0,1,0],[-v,0,c]] if axis=="y" else
                [[c,-v,0],[v,c,0],[0,0,1]])
    entries=[]
    for step in range(17):
        ticks=step*.5
        f=1-((32-ticks)/32)**27
        inverse=matmul(matmul(rotate("z",-30*f),rotate("x",-10*f)),rotate("y",-90*f))
        rotation=matmul(inverse,matmul(rotate("y",-8),rotate("z",-5)))
        y=math.asin(max(-1,min(1,rotation[0][2])))
        x=math.atan2(-rotation[1][2],rotation[2][2])
        z=math.atan2(-rotation[0][1],rotation[0][0])
        angles=[round(math.degrees(v),6) for v in [x,y,z]]
        name=f"deemo_cookie_eating_{step}"
        transform={"rotation":angles,"translation":[0,4,-1],"scale":[.38,.38,.38]}
        eating={"parent":"servercore:item/deemo_cookie", "display":{
            "firstperson_righthand":transform,"firstperson_lefthand":transform}}
        (dest/f"models/item/{name}.json").write_text(json.dumps(eating,indent=2),encoding="utf-8")
        entries.append({"threshold":ticks,"model":{"type":"minecraft:model","model":f"servercore:item/{name}"}})
    normal={"type":"minecraft:model","model":"servercore:item/deemo_cookie"}
    item={"model":{"type":"minecraft:condition","property":"minecraft:using_item","on_false":normal,
        "on_true":{"type":"minecraft:range_dispatch","property":"minecraft:use_duration","entries":entries,"fallback":normal}}}
    (dest/"items/deemo_cookie.json").write_text(json.dumps(item,indent=2),encoding="utf-8")
    return im

if __name__ == "__main__":
    import sys
    install_cookie_assets(sys.argv[1])
