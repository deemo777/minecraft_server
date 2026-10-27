"""고글·청록 작업복·가죽 앞치마·등의 검을 갖춘 무기 상인."""
from pathlib import Path
from PIL import Image,ImageDraw
import json
HERE=Path(__file__).resolve().parent

def install_weapon_merchant_assets(pack):
    sc=Path(pack)/"assets/servercore"
    for sub in ["textures/item","models/item","items"]:(sc/sub).mkdir(parents=True,exist_ok=True)
    colors={"coat":"#294c53","coat_dark":"#1e343d","leather":"#805a42","leather_light":"#a47a52","skin":"#c99b7c","hair":"#66524b","beard":"#4b3b37","brass":"#c4a16a","steel":"#c0cdd3","dark":"#30333e","lens":"#76b4bd","light":"#e8ded0"}
    for k,c in colors.items():Image.new("RGBA",(2,2),c).save(sc/f"textures/item/merchant_{k}.png")
    face=Image.new("RGBA",(8,8),colors["skin"]);d=ImageDraw.Draw(face)
    d.rectangle((0,0,7,1),fill=colors["hair"]);d.line((0,1,0,4),fill=colors["hair"]);d.line((7,1,7,4),fill=colors["hair"])
    for x in [2,5]:d.point((x,3),fill=colors["dark"]);d.point((x,2),fill=colors["hair"])
    d.rectangle((3,4,4,4),fill="#b7866d");d.rectangle((1,5,6,6),fill=colors["beard"]);d.rectangle((2,7,5,7),fill=colors["beard"])
    d.line((3,5,4,5),fill=colors["skin"]);d.line((3,6,4,6),fill=colors["light"])
    face.save(sc/"textures/item/merchant_face.png")
    elements=[]
    def cube(a,b,tex,front=None):
        faces={f:{"texture":"#"+tex,"uv":[0,0,16,16]} for f in ["north","south","east","west","up","down"]}
        if front:faces["north"]={"texture":"#"+front,"uv":[0,0,16,16]}
        elements.append({"from":a,"to":b,"faces":faces})
    # 머리·머리카락, 눈 위로 올린 고글.
    cube([4,24,4],[12,32,12],"skin","face")
    cube([3.9,30.8,4],[12.1,32.4,12.1],"hair")
    cube([3.8,25,10.8],[12.2,31,12.2],"hair")
    cube([3.7,30,3.7],[12.3,31.1,12.3],"leather")
    for x in [4.3,8.3]:
        cube([x,29.8,3.3],[x+3,32,3.7],"brass")
        cube([x+.5,30.2,3.1],[x+2.5,31.6,3.3],"lens")
        cube([x+.5,31,3],[x+1,31.6,3.1],"light")
    cube([7.3,30.5,3.3],[8.3,31,3.7],"brass")
    # 작업복·장갑·가죽 부츠.
    cube([4,12,6],[12,24,10],"coat")
    for x in [0,12]:
        cube([x,15,6],[x+4,24,10],"coat")
        cube([x,12,5.9],[x+4,15.1,10.1],"skin")
        cube([x,12,5.8],[x+4,13.5,10.2],"dark")
        cube([x,15,5.8],[x+4,16,10.2],"brass")
    cube([4,0,6],[7.9,12,10],"coat_dark");cube([8.1,0,6],[12,12,10],"coat_dark")
    for x in [3.9,8.1]:
        cube([x,0,5.5],[x+4,4,10.2],"leather")
        cube([x,3,5.3],[x+4,3.6,5.5],"brass")
    # 앞치마와 멜빵·허리띠·큰 주머니.
    cube([5,9,5.3],[11,21,5.8],"leather")
    for x in [5,10]:cube([x,20,5.6],[x+1,24.2,6.2],"leather_light")
    cube([3.8,12,5.1],[12.2,13.2,10.4],"leather_light")
    cube([7.1,12,4.9],[8.9,13.5,5.2],"brass")
    cube([5.6,14,4.8],[10.4,17.5,5.3],"leather_light")
    cube([5.6,17,4.7],[10.4,17.5,4.8],"brass")
    for x in [6,9]:cube([x,17.5,5],[x+.5,19,5.5],"steel")
    # 등에 멘 검집과 드러난 손잡이.
    cube([9.6,8,10.1],[11.6,28,12],"dark")
    cube([9.4,9,10],[11.8,10,12.1],"brass")
    cube([8.4,25,10.2],[12.8,26,12.2],"steel")
    cube([10,26,10.6],[11.2,30,11.8],"leather")
    cube([9.8,29.5,10.4],[11.4,30.4,12],"brass")
    for e in elements:
        for key in ["from","to"]:
            x,y,z=e[key];e[key]=[8+(x-8)*.8,y*.8,8+(z-8)*.8]
    textures={k:"servercore:item/merchant_"+k for k in colors};textures["face"]="servercore:item/merchant_face";textures["particle"]=textures["coat"]
    model={"textures":textures,"elements":elements,"gui_light":"front"}
    (sc/"models/item/weapon_merchant.json").write_text(json.dumps(model,indent=2),encoding="utf-8")
    (sc/"items/weapon_merchant.json").write_text(json.dumps({"model":{"type":"minecraft:model","model":"servercore:item/weapon_merchant"}},indent=2),encoding="utf-8")
    return model

if __name__=="__main__":
    import sys
    install_weapon_merchant_assets(sys.argv[1])
