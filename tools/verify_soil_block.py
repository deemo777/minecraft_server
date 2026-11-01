"""Validate vanilla-size soil block texels and render the shipped native mesh/UVs."""
import argparse
import io
import json
import os
from pathlib import Path
from zipfile import ZipFile
from PIL import Image, ImageDraw
import preview_weapon_animations as renderer


def verify(path,client_path,reference=None):
    with ZipFile(path) as pack,ZipFile(client_path) as client:
        names=set(pack.namelist())
        class Resources:
            def read(self,name): return pack.read(name) if name in names else client.read(name)
            def namelist(self): return pack.namelist()+client.namelist()
        resources=Resources()
        vanilla=client.read("assets/minecraft/textures/block/dirt_path_top.png")
        if reference:
            assert Path(reference).read_bytes()==vanilla,"example top differs from the original vanilla PNG"
        models=[]
        for name in ("soil_block","soil_block_wet","farm_plot"):
            item=json.loads(pack.read(f"assets/servercore/items/{name}.json"))
            assert item["model"]["model"]==f"servercore:item/{name}"
            model=json.loads(pack.read(f"assets/servercore/models/item/{name}.json"))
            assert "parent" not in model
            assert model["textures"]["soil"]=="minecraft:block/dirt_path_top","use original vanilla texture directly, without recoloring or resizing"
            body=model["elements"][0]
            assert body["from"]==[0,0,0] and body["to"]==[16,16,16]
            assert body["faces"]["up"]=={"uv":[0,0,16,16],"texture":"#soil"},"full 16px UV coverage, no stretched top"
            for element in model["elements"]:
                assert all(float(v).is_integer() for v in element["from"]+element["to"]),"subpixel geometry is forbidden"
                assert all(0<=a<b<=limit for a,b,limit in zip(element["from"],element["to"],[16,18,16]))
                x,y,z=[b-a for a,b in zip(element["from"],element["to"])]
                sizes={"north":(x,y),"south":(x,y),"east":(z,y),"west":(z,y),"up":(x,z),"down":(x,z)}
                for face,spec in element["faces"].items():
                    u0,v0,u1,v1=spec["uv"];w,h=sizes[face]
                    assert 0<u1-u0<=w and 0<v1-v0<=h,"a texture pixel must not be smaller than one native block pixel"
            for ref in model["textures"].values():
                namespace,location=ref.split(":")
                with Image.open(io.BytesIO(resources.read(f"assets/{namespace}/textures/{location}.png"))) as image:
                    assert image.size==(16,16),"all soil textures must follow the vanilla 16x16 block grid"
                    image.verify()
            models.append(model)
        assert models[0]==models[1]==models[2],"legacy and wet IDs must keep the unchanged vanilla top"
        original=renderer.rotation
        renderer.rotation=lambda axis,angle: original(axis,28 if axis=="x" and angle==-8 else angle)
        try:
            image=renderer.prepare(resources,model_path="assets/servercore/models/item/soil_block.json",angle=-35)(0)
        finally: renderer.rotation=original
        output=Image.new("RGB",(480,275),(34,31,26));draw=ImageDraw.Draw(output)
        output.paste(image,(0,0),image)
        side=Image.open(io.BytesIO(pack.read("assets/servercore/textures/block/soil_block_wood.png"))).convert("RGBA")
        output.paste(side.resize((128,128),Image.Resampling.NEAREST),(300,24))
        top=Image.open(io.BytesIO(vanilla)).convert("RGBA")
        output.paste(top.resize((64,64),Image.Resampling.NEAREST),(332,184))
        draw.text((20,250),"Shipped 3D soil block",fill=(234,218,190))
        draw.text((289,160),"Side: native 16 x 16",fill=(234,218,190))
        draw.text((269,252),"Top: original vanilla dirt path",fill=(234,218,190))
        target=Path(path).parent/"soil_block_preview.png";output.save(target)
        print("Soil block: 16px textures, >=1 native pixel per texel, integer geometry, original dirt-path full UVs and legacy/wet IDs passed")
        print(target)


if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("--pack",default="tools/build/servercore_pack.zip")
    parser.add_argument("--jar",default=str(Path(os.environ.get("APPDATA",""))/".minecraft/versions/26.3/26.3.jar"))
    parser.add_argument("--reference-top")
    args=parser.parse_args();verify(args.pack,args.jar,args.reference_top)
