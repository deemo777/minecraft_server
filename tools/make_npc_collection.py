"""7종 NPC의 직물·가죽·머리카락 도트 명암과 직업 소품을 생성한다."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageColor
import json,random,math

STYLES={
 "mayor":dict(coat="#484d36",apron="#813e40",pants="#3d3b3c",hair="#ded6c5",skin="#d6ad8b",accent="#d2ae65"),
 "blacksmith":dict(coat="#383b3b",apron="#97553c",pants="#3e3c39",hair="#342e2b",skin="#b98d70",accent="#ba9364"),
 "fish":dict(coat="#345b70",apron="#dfd2b7",pants="#3c454e",hair="#58483d",skin="#d5ab87",accent="#bd9b65"),
 "crops":dict(coat="#5a794a",apron="#b99246",pants="#4d5940",hair="#895c3e",skin="#dfb496",accent="#ddc889"),
 "ores":dict(coat="#3b485d",apron="#704c3f",pants="#41434d",hair="#b5a99a",skin="#c79d80",accent="#ba865b"),
}

def shade(rgb,f):return tuple(max(0,min(255,round(c*f))) for c in rgb)
def texture(path,color,kind):
    rgb=ImageColor.getrgb(color);im=Image.new("RGBA",(16,16));rng=random.Random(path.stem)
    for y in range(16):
        for x in range(16):
            f=.93+.07*math.cos((x-5)*.5)+.035*math.sin(y*.8)
            f+=rng.choice([-.055,0,0,0,.04])
            if kind=="skin":f=.99+rng.choice([-.02,0,.015])
            if x in [0,15] or y in [0,15]:f-=.18
            if kind in ["cloth","apron"] and x in [3,11]:f-=.14
            if kind=="hair":f+=.12*math.cos(x*1.9)
            if kind=="metal":f+=.15*(1-y/15)+(.14 if x in [2,3] else 0)
            im.putpixel((x,y),(*shade(rgb,f),255))
    d=ImageDraw.Draw(im)
    if kind in ["leather","apron","boots"]:
        for i in range(2,14,3):
            for pt in [(i,1),(i,14),(1,i),(14,i)]:d.point(pt,fill=(*shade(rgb,1.28),255))
        for x,y in [(5,5),(6,6),(10,11),(11,11)]:d.point((x,y),fill=(*shade(rgb,1.22),255))
    if kind=="cloth":
        d.line((2,1,2,14),fill=(*shade(rgb,.72),255));d.line((3,2,3,13),fill=(*shade(rgb,1.12),255))
        d.line((4,13,13,13),fill=(*shade(rgb,.83),255))
    if kind=="metal":
        d.rectangle((1,1,14,14),outline=(*shade(rgb,1.18),255));d.point((2,2),fill=(*shade(rgb,1.45),255))
    im.save(path)

def enhance_existing(sc):
    # 기존 캐릭터의 실제 얼굴 텍스처에는 손대지 않고 단색 재질만 세밀화.
    for path in sorted((sc/"textures/item").glob("*.png")):
        if not path.name.startswith(("chef_","merchant_")):continue
        im=Image.open(path).convert("RGBA")
        if im.size!=(2,2):continue
        rgb=im.getpixel((0,0))[:3];color="#%02x%02x%02x"%rgb
        kind="hair" if "hair" in path.stem or "beard" in path.stem else "metal" if any(k in path.stem for k in ["gold","brass","steel","lens"]) else "skin" if "skin" in path.stem else "leather" if "leather" in path.stem else "cloth"
        texture(path,color,kind)
    # 요리사 앞치마 포켓 자수·코트 단추·롤링핀.
    p=sc/"models/item/deemo_chef.json";m=json.loads(p.read_text());m["textures"]["particle"]=m["textures"]["cream"]
    def add(a,b,tex):
        a=[8+(a[0]-8)*.8,a[1]*.8,8+(a[2]-8)*.8];b=[8+(b[0]-8)*.8,b[1]*.8,8+(b[2]-8)*.8]
        m["elements"].append({"from":a,"to":b,"faces":{f:{"texture":"#"+tex,"uv":[0,0,16,16]} for f in ["north","south","east","west","up","down"]}})
    for x,y in [(7.7,12.3),(7.2,12.8),(8.2,12.8),(7.7,13.3)]:add([x,y,4.96],[x+.5,y+.5,5.03],"pink")
    for x in [5.2,10.5]:add([x,9,5.15],[x+.3,17,5.3],"cream")
    for y in [20.5,22]:add([5.3,y,5.7],[5.8,y+.5,6],"gold")
    add([12.1,9,7],[13,18,8],"gold");add([12.2,11,6.7],[13.4,16,8.3],"cream")
    p.write_text(json.dumps(m,indent=2),encoding="utf-8")
    # 무기 상인의 어깨 덧댐·앞치마 솔기·철제 리벳.
    p=sc/"models/item/weapon_merchant.json";m=json.loads(p.read_text())
    def addm(a,b,tex):
        m["elements"].append({"from":[8+(a[0]-8)*.8,a[1]*.8,8+(a[2]-8)*.8],"to":[8+(b[0]-8)*.8,b[1]*.8,8+(b[2]-8)*.8],"faces":{f:{"texture":"#"+tex,"uv":[0,0,16,16]} for f in ["north","south","east","west","up","down"]}})
    addm([-.3,22,5.6],[4.3,24.3,10.4],"steel")
    for x in [.2,3.3]:addm([x,22.5,5.4],[x+.5,23,5.65],"brass")
    for y in [10,12,14,16,18,20]:addm([5.2,y,5.1],[5.45,y+.35,5.3],"brass")
    addm([11.8,9,5],[13.2,12,6],"leather");addm([12.2,11.5,4.8],[12.8,12,5.1],"brass")
    p.write_text(json.dumps(m,indent=2),encoding="utf-8")

def make_character(sc,role,c):
    palette=dict(c);palette.update(boots="#544337",metal="#b8c2c5",dark="#2d3036",cream="#ece2cf",pink="#c8897a",blue="#62c5cf",green="#679849",orange="#db9545",purple="#8c67ac")
    textures={}
    for k,color in palette.items():
        name=f"npc_{role}_{k}";textures[k]="servercore:item/"+name
        kind="hair" if k=="hair" else "metal" if k in ["metal","accent","blue","purple"] else "skin" if k=="skin" else "apron" if k=="apron" else "boots" if k=="boots" else "cloth"
        texture(sc/f"textures/item/{name}.png",color,kind)
    # 얼굴은 표정·눈썹·피부 명암이 있는 16px 텍스처.
    face=Image.new("RGBA",(16,16),c["skin"]);d=ImageDraw.Draw(face);skin=ImageColor.getrgb(c["skin"])
    d.rectangle((0,0,15,3),fill=c["hair"]);d.line((0,3,0,11),fill=shade(skin,.8),width=2);d.line((15,3,15,11),fill=shade(skin,.8),width=2)
    for x in [4,10]:
        d.line((x,6,x+2,6),fill=c["hair"]);d.rectangle((x,8,x+2,9),fill="#eee6d5");d.point((x+1,8),fill="#334346")
    d.line((7,10,8,10),fill=shade(skin,.83));d.line((6,13,9,13),fill=shade(skin,.65))
    d.point((3,11),fill="#c28672");d.point((12,11),fill="#c28672")
    if role in ["mayor","blacksmith","ores"]:
        d.rectangle((2,12,13,13),fill=c["hair"]);d.rectangle((4,14,11,15),fill=c["hair"]);d.line((6,13,9,13),fill="#e5d8c2")
    if role=="blacksmith":
        for pt in [(2,7),(3,7),(12,10),(11,11)]:d.point(pt,fill="#796b60")
    face.save(sc/f"textures/item/npc_{role}_face.png");textures["face"]=f"servercore:item/npc_{role}_face";textures["particle"]=textures["coat"]
    es=[]
    def cube(a,b,t,front=None):
        faces={f:{"texture":"#"+t,"uv":[0,0,16,16]} for f in ["north","south","east","west","up","down"]}
        if front:faces["north"]={"texture":"#"+front,"uv":[0,0,16,16]}
        es.append({"from":a,"to":b,"faces":faces})
    cube([4,24,4],[12,32,12],"skin","face");cube([3.9,30.8,4.3],[12.1,32.2,12.1],"hair");cube([4,25,11],[12,31,12.2],"hair")
    # 옆머리·귀·앞머리 층으로 민머리처럼 보이는 단면을 없앤다.
    cube([3.8,27,5.5],[4.1,31.5,12.2],"hair")
    cube([11.9,27,5.5],[12.2,31.5,12.2],"hair")
    cube([3.5,26,7],[4.1,28,8.7],"skin")
    cube([11.9,26,7],[12.5,28,8.7],"skin")
    for x,h in [(4.1,29.8),(6.2,30.2),(9.7,29.9)]:cube([x,h,3.8],[x+1.6,31.2,4.1],"hair")
    if role=="crops":
        cube([11.8,23,6],[12.9,28,7.4],"hair");cube([11.7,22.5,5.9],[13,23.3,7.5],"green")
    cube([4,12,6],[12,24,10],"coat")
    for x in [0,12]:
        cube([x,15,6],[x+4,24,10],"coat");cube([x,12,5.9],[x+4,15,10.1],"skin");cube([x,14.5,5.8],[x+4,16,10.2],"cream")
    for x in [4,8.1]:
        cube([x,0,6],[x+3.9,12,10],"pants");cube([x-.1,0,5.5],[x+4,3.5,10.3],"boots");cube([x,2.5,5.3],[x+3.8,3.2,5.5],"accent")
    cube([3.8,12,5.3],[12.2,13.3,10.4],"boots");cube([7.1,12,5],[8.9,13.7,5.3],"accent")
    cube([12,9,6],[14,13,9],"boots");cube([12.4,11.8,5.8],[13.6,12.4,6],"accent")
    for y in [15,18,21]:cube([10.4,y,5.75],[10.9,y+.5,6],"accent")
    if role=="mayor":
        cube([5.2,13,5.7],[10.8,23.6,6],"apron")
        for x in [4.5,10.5]:cube([x,12,5.4],[x+1,24,5.9],"accent")
        cube([6.5,21,5.1],[9.5,24.3,5.7],"cream")
        for y in [15,18,20]:cube([7.7,y,5.4],[8.2,y+.5,5.7],"accent")
        cube([2.8,9,10.1],[5.5,17,11.5],"boots")
        for x in [10.5,11.5,12.5]:cube([x,9,5.5],[x+.4,12,5.9],"accent")
        cube([-.2,22,5.7],[4.2,24.2,10.3],"accent");cube([11.8,22,5.7],[16.2,24.2,10.3],"accent")
    elif role=="blacksmith":
        cube([5,8,5.2],[11,21.5,5.8],"apron")
        for x in [5,10]:cube([x,20,5.5],[x+1,24.2,6.2],"boots")
        for x in [0,12]:cube([x,12,5.7],[x+4,14.4,10.2],"boots")
        for y in [10,13,16,19]:
            for x in [5.2,10.3]:cube([x,y,5],[x+.5,y+.4,5.25],"accent")
        cube([12.5,6,6.5],[13.3,15,7.3],"boots");cube([11.2,13.5,5.8],[14.7,16,8],"metal")
        cube([6,14,4.8],[10,17.5,5.2],"boots")
    elif role=="fish":
        cube([3.5,31.5,3.5],[12.5,34,12.5],"pants");cube([3.2,31,2.4],[12.8,32,5],"accent")
        cube([5,21,5],[11,24.2,6.5],"blue");cube([8,17,5],[10,22,5.7],"blue")
        for y in [16,18]:
            for x in [0,12]:cube([x,y,5.8],[x+4,y+.5,6],"pants")
        cube([-2,7,7],[1.5,15,11],"apron")
        for y in [8,10,12,14]:cube([-2.2,y,6.8],[1.7,y+.4,7.1],"boots")
        cube([-1,15,7.5],[0,18,8.5],"metal");cube([0,15,9],[1,17,10],"blue")
        cube([13.5,6,10],[14,29,10.5],"boots")
    elif role=="crops":
        cube([1.5,31,1.5],[14.5,32,14.5],"apron");cube([4,32,4],[12,35,12],"apron");cube([3.8,32,3.8],[12.2,33,12.2],"green")
        cube([5,8,5.2],[11,22,5.8],"apron")
        for x in [5,10]:cube([x,21,5.7],[x+1,24,6.1],"boots")
        cube([5.5,12,4.7],[10.5,16,5.2],"cream")
        for x in [6,8.5]:cube([x,12.7,4.5],[x+.8,14.8,4.7],"green")
        cube([12.5,11,5],[14.5,15,7],"orange");cube([13,15,5.5],[14,18,6.5],"green")
        for y in [18,21]:
            for x in [0,12]:cube([x,y,5.8],[x+4,y+.4,6],"green")
    elif role=="ores":
        cube([3.3,31,3.3],[12.7,34,12.7],"apron");cube([3,31,2.5],[13,32,5],"accent")
        cube([6.5,32,2.1],[9.5,34.5,3.4],"metal");cube([7,32.5,1.9],[9,34,2.1],"blue")
        cube([5,21,5],[11,24.2,6.4],"apron");cube([6,18,5],[8,22,5.6],"apron")
        cube([4,6,10],[12,12,10.7],"coat")
        cube([-1,9,6],[2.5,14,9.5],"boots")
        for x,t in [(0,"blue"),(1,"purple")]:cube([x,13,5.7],[x+.8,15.5,6.3],t)
        cube([12.2,8,10.1],[13,29,11],"boots");cube([10,27,10],[15,29,12],"metal")
    for e in es:
        for key in ["from","to"]:
            x,y,z=e[key];e[key]=[8+(x-8)*.8,y*.8,8+(z-8)*.8]
    model={"textures":textures,"elements":es,"gui_light":"front"}
    name="npc_"+role
    (sc/f"models/item/{name}.json").write_text(json.dumps(model,indent=2),encoding="utf-8")
    (sc/f"items/{name}.json").write_text(json.dumps({"model":{"type":"minecraft:model","model":"servercore:item/"+name}},indent=2),encoding="utf-8")

def install_npc_collection(pack):
    sc=Path(pack)/"assets/servercore"
    enhance_existing(sc)
    for role,palette in STYLES.items():make_character(sc,role,palette)
    # 운영자가 추가한 상점 ID는 무기 상인 외형을 기본으로 사용.
    (sc/"models/item/npc_merchant.json").write_text(json.dumps({"parent":"servercore:item/weapon_merchant"}),encoding="utf-8")
    (sc/"items/npc_merchant.json").write_text(json.dumps({"model":{"type":"minecraft:model","model":"servercore:item/npc_merchant"}}),encoding="utf-8")
