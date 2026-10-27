"""실제 스킨을 유지한 요리사 모델. 얼굴을 재해석하거나 바꾸지 않는다."""
from pathlib import Path
from PIL import Image, ImageDraw
import json
HERE=Path(__file__).resolve().parent

def install_chef_assets(pack):
    sc=Path(pack)/"assets/servercore"
    for sub in ["textures/item","models/item","items"]:(sc/sub).mkdir(parents=True,exist_ok=True)
    skin=Image.open(HERE/"art/deemo777_skin.png").convert("RGBA")
    # 머리는 스킨의 여섯 면과 겉 레이어를 그대로 합성.
    head=Image.new("RGBA",(64,64))
    head.paste(skin,(0,0))
    for box,overlay in [((8,8,16,16),(40,8,48,16)),((0,8,8,16),(32,8,40,16)),((16,8,24,16),(48,8,56,16)),((24,8,32,16),(56,8,64,16)),((8,0,16,8),(40,0,48,8)),((16,0,24,8),(48,0,56,8))]:
        part=skin.crop(box);part.alpha_composite(skin.crop(overlay));head.paste(part,box[:2])
    head.save(sc/"textures/item/chef_skin.png")
    colors={"cream":"#faf8f3","shadow":"#d8d4d4","white":"#ffffff","pink":"#d8978a","charcoal":"#4a4442","gray":"#655f5d","gold":"#d3a028"}
    for k,c in colors.items():Image.new("RGBA",(2,2),c).save(sc/f"textures/item/chef_{k}.png")
    elements=[]
    def cube(a,b,tex,uvs=None):
        faces={f:{"texture":"#"+tex,"uv":[0,0,16,16]} for f in ["north","south","east","west","up","down"]}
        if uvs:
            for f,uv in uvs.items():faces[f]={"texture":"#skin","uv":[v/4 for v in uv]}
        elements.append({"from":a,"to":b,"faces":faces})
    # 캐릭터 머리: 얼굴은 정면 north.
    cube([4,24,4],[12,32,12],"gray",{"north":[8,8,16,16],"south":[24,8,32,16],"east":[0,8,8,16],"west":[16,8,24,16],"up":[8,0,16,8],"down":[16,0,24,8]})
    # 흰 조리복, 회색 바지와 신발. 스킨의 손 색과 팔레트 사용.
    cube([4,12,6],[12,24,10],"cream")
    cube([0,12,6],[4,24,10],"cream")
    cube([12,12,6],[16,24,10],"cream")
    cube([0,12,5.9],[4,15,10.1],"gray")
    cube([12,12,5.9],[16,15,10.1],"gray")
    cube([0,15,5.8],[4,16,10.2],"shadow")
    cube([12,15,5.8],[16,16,10.2],"shadow")
    cube([4,0,6],[7.9,12,10],"gray")
    cube([8.1,0,6],[12,12,10],"gray")
    cube([3.9,0,5.7],[7.9,2,10.2],"charcoal")
    cube([8.1,0,5.7],[12.1,2,10.2],"charcoal")
    # 딸기색 앞치마: 목끈, 허리띠, 앞판, 입체 주머니와 등쪽 리본.
    cube([6.5,20,5.75],[9.5,24.2,6],"pink")
    cube([5,8,5.4],[11,20.5,5.8],"pink")
    cube([3.8,13,5.3],[12.2,14,10.3],"pink")
    cube([6,11,5.1],[10,14,5.4],"cream")
    cube([6,13.5,5.05],[10,14,5.1],"shadow")
    cube([6.5,13,10.3],[9.5,14.5,11],"pink")
    cube([7.5,12.5,10.8],[8.5,15,11.2],"cream")
    # 작은 금색 단추 두 개는 실제 캐릭터의 장식색.
    for y in [21,22.5]:cube([10,y,5.75],[10.5,y+.5,6],"gold")
    # 얼굴 위로만 올라가는 제빵모자: 띠와 세 갈래의 둥근 볼륨.
    cube([3.5,31.8,3.5],[12.5,34,12.5],"shadow")
    cube([3.4,32.4,3.4],[12.6,34,12.6],"cream")
    cube([3,34,3],[13,37,13],"cream")
    cube([4,37,4],[12,39,12],"white")
    cube([2,34,4],[5,37,12],"white")
    cube([11,34,4],[14,37,12],"white")
    cube([5,34,2],[11,37,5],"white")
    cube([5,34,11],[11,37,14],"cream")
    # Java 아이템 모델의 좌표 범위 -16..32 안으로 정규화.
    for e in elements:
        for key in ["from","to"]:
            x,y,z=e[key];e[key]=[8+(x-8)*.8,y*.8,8+(z-8)*.8]
    textures={k:"servercore:item/chef_"+k for k in colors};textures["skin"]="servercore:item/chef_skin"
    model={"textures":textures,"elements":elements,"texture_size":[64,64],"gui_light":"front"}
    (sc/"models/item/deemo_chef.json").write_text(json.dumps(model,indent=2),encoding="utf-8")
    (sc/"items/deemo_chef.json").write_text(json.dumps({"model":{"type":"minecraft:model","model":"servercore:item/deemo_chef"}},indent=2),encoding="utf-8")
    return model

if __name__=="__main__":
    import sys
    install_chef_assets(sys.argv[1])
