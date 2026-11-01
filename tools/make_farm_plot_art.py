"""Native 16px crate-style soil block; use the original vanilla dry/wet farmland tops.

A block face has at most 16 texels across 16 model units. No random noise, fine grain,
resized vanilla textures or subpixel decorative geometry. The supplied example pack
uses cube_bottom_top and the byte-identical vanilla farmland/farmland_moist textures.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw


def texture(kind):
    # Hand-placed, broad pixel clusters: three vertical boards inside substantial rails.
    image=Image.new("RGBA",(16,16),(159,115,65,255));d=ImageDraw.Draw(image)
    if kind=="wood":
        for x0,x1,color in ((2,5,(171,126,72)),(6,9,(164,119,66)),(10,13,(176,130,76))):
            d.rectangle((x0,2,x1,13),fill=(*color,255))
        # Two dark joints between the three boards; no thin repeated hatching.
        d.line((5,2,5,13),fill=(113,76,39,255));d.line((9,2,9,13),fill=(113,76,39,255))
        for x,y0,y1 in ((3,5,8),(7,9,12),(12,3,6)):
            d.line((x,y0,x,y1),fill=(151,104,53,255))
        for x,y in ((2,3),(6,6),(11,10)):
            d.rectangle((x,y,x+1,y+1),fill=(184,139,83,255))
        # Outer corner posts and horizontal top/bottom rails from the reference crate.
        d.rectangle((0,0,1,15),fill=(184,139,82,255));d.rectangle((14,0,15,15),fill=(176,129,73,255))
        d.rectangle((0,0,15,1),fill=(194,150,91,255));d.rectangle((0,14,15,15),fill=(142,98,50,255))
        d.line((0,0,15,0),fill=(216,175,110,255));d.line((0,0,0,14),fill=(204,160,96,255))
        d.line((1,2,1,13),fill=(154,108,56,255));d.line((14,2,14,13),fill=(122,81,40,255))
        d.line((2,13,13,13),fill=(123,82,41,255))
    else:
        image=Image.new("RGBA",(16,16),(190,147,90,255));d=ImageDraw.Draw(image)
        d.line((0,0,15,0),fill=(221,181,117,255));d.line((0,15,15,15),fill=(132,90,45,255))
        for x0,x1,y,color in ((2,5,4,(177,130,72,255)),(9,12,9,(204,160,98,255)),(4,7,12,(170,121,65,255))):
            d.line((x0,y,x1,y),fill=color)
    return image


def box(start,end,slot):
    x,y,z=[b-a for a,b in zip(start,end)]
    sizes={"north":(x,y),"south":(x,y),"east":(z,y),"west":(z,y),"up":(x,z),"down":(x,z)}
    return {"from":start,"to":end,"faces":{face:{"uv":[0,0,min(w,16),min(h,16)],"texture":"#"+slot}
            for face,(w,h) in sizes.items()}}


def soil_model(wet=False):
    # Full 16-unit face, as in the supplied full-block farmland/path example.
    body=box([0,0,0],[16,16,16],"wood")
    body["faces"]["up"]={"uv":[0,0,16,16],"texture":"#soil"}
    elements=[body]
    # One-block-pixel wooden lip and corner posts. Integer geometry only.
    elements += [box([0,16,0],[16,17,1],"rim"),box([0,16,15],[16,17,16],"rim"),
                 box([0,16,1],[1,17,15],"rim"),box([15,16,1],[16,17,15],"rim")]
    for x in (0,15):
        for z in (0,15):elements.append(box([x,16,z],[x+1,18,z+1],"rim"))
    # Native farmland textures follow the actual moisture state; the wooden sides stay identical.
    return {"ambientocclusion":True,"textures":{"particle":"servercore:block/soil_block_wood",
            "wood":"servercore:block/soil_block_wood","rim":"servercore:block/soil_block_rim",
            "soil":"minecraft:block/"+("farmland_moist" if wet else "farmland")},
            "elements":elements,"display":{
                "gui":{"rotation":[30,225,0],"translation":[0,-.5,0],"scale":[.625,.625,.625]},
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
    for kind in ("wood","rim"):
        texture(kind).save(root/"textures/block"/f"soil_block_{kind}.png")
    for name,wet in (("soil_block",False),("soil_block_wet",True),("farm_plot",False)):
        (root/"models/item"/f"{name}.json").write_text(json.dumps(soil_model(wet),indent=2),encoding="utf-8")
        item={"model":{"type":"minecraft:model","model":f"servercore:item/{name}"}}
        (root/"items"/f"{name}.json").write_text(json.dumps(item,indent=2),encoding="utf-8")
