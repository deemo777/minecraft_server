"""Install a native 3D wooden soil planter, matching the supplied crate-and-soil reference.

All geometry and textures are generated deterministically; no vanilla item/block mesh is reused.
The old farm_plot item model remains an alias for already-issued legacy items.
"""
import json
import random
from pathlib import Path
from PIL import Image, ImageDraw


def texture(kind):
    rng=random.Random(9271)
    image=Image.new("RGBA",(32,32))
    for y in range(32):
        for x in range(32):
            if kind.startswith("soil"):
                base=(119,83,54) if kind=="soil" else (73,49,32)
                noise=rng.choice([-12,-7,-3,0,3,7,11])
                color=tuple(max(0,min(255,c+noise)) for c in base)
            else:
                base=(164,119,70) if kind=="wood" else (191,150,96)
                grain=[-18,5,10,-5,2,-8,13,-4][x%8]
                noise=rng.choice([-4,-2,0,2,4])
                color=tuple(max(0,min(255,c+grain+noise)) for c in base)
            image.putpixel((x,y),(*color,255))
    d=ImageDraw.Draw(image)
    if kind=="wood":
        for x in (0,8,16,24):
            d.line((x,0,x,31),fill=(98,67,36,255))
            d.line((x+1,0,x+1,31),fill=(207,162,101,255))
        for x,y in ((5,10),(13,22),(21,6),(28,18)):
            d.line((x,y,x,y+5),fill=(118,77,38,255))
            d.point((x+1,y+2),fill=(214,167,100,255))
    elif kind=="rim":
        d.line((0,0,31,0),fill=(231,190,127,255));d.line((0,31,31,31),fill=(121,82,43,255))
        for x,y in ((4,8),(21,18),(12,26)):
            d.line((x,y,min(31,x+7),y),fill=(147,103,57,255))
    else:
        shift=0 if kind=="soil" else -25
        for x,y in ((3,5),(17,11),(25,25),(8,23),(23,4)):
            d.rectangle((x,y,x+1,y+1),fill=(164+shift,117+shift,77+shift,255))
    return image


def box(start,end,slot):
    x,y,z=[b-a for a,b in zip(start,end)]
    sizes={"north":(x,y),"south":(x,y),"east":(z,y),"west":(z,y),"up":(x,z),"down":(x,z)}
    return {"from":start,"to":end,"faces":{face:{"uv":[0,0,w,h],"texture":"#"+slot}
            for face,(w,h) in sizes.items()}}


def soil_model(wet=False):
    elements=[box([0,0,0],[16,2,16],"wood"),box([2,2,2],[14,15.02,14],"soil")]
    # Full-width panels conceal the native farmland side while its collision/crop support stay intact.
    elements += [box([0,2,0],[2,15.02,16],"wood"),box([14,2,0],[16,15.02,16],"wood"),
                 box([2,2,0],[14,15.02,2],"wood"),box([2,2,14],[14,15.02,16],"wood")]
    elements += [box([0,15.02,0],[16,16,2],"rim"),box([0,15.02,14],[16,16,16],"rim"),
                 box([0,15.02,2],[2,16,14],"rim"),box([14,15.02,2],[16,16,14],"rim")]
    for x in (0,14):
        for z in (0,14):elements.append(box([x,0,z],[x+2,16,z+2],"rim"))
    # Slightly heaped soil under the raised wooden lip, no sprouts or decorative crops.
    elements.append(box([5,15.02,5],[11,15.05,11],"soil"))
    return {"ambientocclusion":True,"textures":{"particle":"servercore:block/soil_block_wood",
            "wood":"servercore:block/soil_block_wood","rim":"servercore:block/soil_block_rim",
            "soil":"servercore:block/soil_block_soil"+("_wet" if wet else "")},
            "elements":elements,"display":{
                "gui":{"rotation":[30,225,0],"translation":[0,0,0],"scale":[.625,.625,.625]},
                "ground":{"translation":[0,3,0],"scale":[.25,.25,.25]},
                "fixed":{"rotation":[0,0,0],"scale":[.5,.5,.5]},
                "firstperson_righthand":{"rotation":[0,45,0],"scale":[.4,.4,.4]},
                "firstperson_lefthand":{"rotation":[0,225,0],"scale":[.4,.4,.4]},
                "thirdperson_righthand":{"rotation":[75,45,0],"translation":[0,2.5,0],"scale":[.375,.375,.375]},
                "thirdperson_lefthand":{"rotation":[75,225,0],"translation":[0,2.5,0],"scale":[.375,.375,.375]}}}


def install_farm_plot_assets(pack_dir):
    root=Path(pack_dir)/"assets/servercore"
    for directory in (root/"textures/block",root/"models/item",root/"items"):
        directory.mkdir(parents=True,exist_ok=True)
    for kind,suffix in (("wood","wood"),("rim","rim"),("soil","soil"),("soil_wet","soil_wet")):
        texture(kind).save(root/"textures/block"/f"soil_block_{suffix}.png")
    for name,wet in (("soil_block",False),("soil_block_wet",True),("farm_plot",False)):
        (root/"models/item"/f"{name}.json").write_text(json.dumps(soil_model(wet),indent=2),encoding="utf-8")
        item={"model":{"type":"minecraft:model","model":f"servercore:item/{name}"}}
        (root/"items"/f"{name}.json").write_text(json.dumps(item,indent=2),encoding="utf-8")
