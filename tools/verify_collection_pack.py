"""Validate full pack JSON/PNG/references and every inactive collection icon."""
import argparse
import io
import json
import zipfile
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageFont, ImageEnhance
from build_pack import Jar, load_materials

def verify(pack_path, client_path):
    client=Jar(client_path)
    with zipfile.ZipFile(pack_path) as pack:
        names=set(pack.namelist());json_count=png_count=references=0
        def reference(value, kind):
            nonlocal references
            if not isinstance(value,str) or value.startswith("#") or ":" not in value:return
            namespace,path=value.split(":",1)
            if path.startswith("builtin/"):return
            suffix=".json" if kind in ("models","items","font") else ""
            if kind=="textures" and not path.endswith(".png"):suffix=".png"
            target=f"assets/{namespace}/{kind}/{path}{suffix}"
            assert target in names or target in client.names, ("Missing reference",target)
            references+=1
        def walk(value, context=""):
            if isinstance(value,list):
                for item in value:walk(item,context)
            if not isinstance(value,dict):return
            if context=="model":
                reference(value.get("parent"),"models")
                for texture in value.get("textures",{}).values():reference(texture,"textures")
            if value.get("type")=="minecraft:model":reference(value.get("model"),"models")
            if value.get("type")=="minecraft:special":
                reference(value.get("base"),"models")
                special=value["model"]
                if special.get("type")=="minecraft:head" and "texture" in special:
                    ns,path=special["texture"].split(":",1);reference(f"{ns}:entity/{path}","textures")
            if value.get("type")=="bitmap":reference(value.get("file"),"textures")
            if value.get("type")=="reference":reference(value.get("id"),"font")
            for item in value.values():walk(item,"")
        for name in names:
            if name.endswith((".json",".mcmeta")):
                value=json.loads(pack.read(name));json_count+=1
                walk(value,"model" if "/models/" in name else "")
            if name.endswith(".png"):
                with Image.open(io.BytesIO(pack.read(name))) as image:image.verify()
                png_count+=1
        materials=load_materials("src/main/resources/dogam.yml")
        for material in materials:
            disabled=json.loads(pack.read(f"assets/servercore/items/disabled/{material}.json"))
            checked=json.loads(pack.read(f"assets/servercore/items/checked/{material}.json"))
            assert "disabled" in json.dumps(disabled),material
            assert checked["model"]["type"]=="minecraft:composite",material
            assert checked["model"]["models"][-1]["model"]=="servercore:item/check",material
        model_count=0
        for name in names:
            prefix="assets/servercore/models/disabled/minecraft/"
            if not name.startswith(prefix) or not name.endswith(".json"):continue
            original=json.loads(client.z.read("assets/minecraft/models/"+name[len(prefix):]))
            actual=json.loads(pack.read(name))
            # Only texture references and inherited model references may change.
            assert set(actual.get("textures",{}))==set(original.get("textures",{})),name
            for value in (original,actual):
                value.pop("textures",None);value.pop("parent",None)
            assert actual==original,("Geometry/display changed",name)
            model_count+=1
        # File existence alone is insufficient: each disabled sprite must be stitched.
        atlas_sources={}
        for atlas in ("blocks","items"):
            path=f"assets/minecraft/atlases/{atlas}.json"
            atlas_sources[atlas]=json.loads(client.z.read(path))["sources"]
            if path in names:
                atlas_sources[atlas]+=json.loads(pack.read(path))["sources"]
        def stitched(texture,atlas):
            namespace,sprite=texture.split(":",1)
            for source in atlas_sources[atlas]:
                if source["type"]=="minecraft:directory":
                    prefix=source.get("prefix","")
                    if sprite.startswith(prefix):
                        file=f"assets/{namespace}/textures/{source['source']}/{sprite[len(prefix):]}.png"
                        if file in names or file in client.names:return True
                elif source["type"]=="minecraft:single":
                    if source.get("sprite",source["resource"])==texture:return True
            return False
        atlas_checks=0
        texture_count=0
        for name in names:
            prefix="assets/servercore/textures/disabled/minecraft/"
            if not name.startswith(prefix) or not name.endswith(".png"):continue
            original=Image.open(io.BytesIO(client.z.read("assets/minecraft/textures/"+name[len(prefix):]))).convert("RGBA")
            dark=Image.open(io.BytesIO(pack.read(name))).convert("RGBA")
            assert dark.size==original.size
            assert ImageChops.difference(dark.getchannel("A"),original.getchannel("A")).getbbox() is None
            expected=ImageEnhance.Brightness(ImageEnhance.Color(original).enhance(.15)).enhance(.30)
            expected.putalpha(original.getchannel("A"))
            assert dark.tobytes()==expected.tobytes(),name
            sprite="servercore:disabled/minecraft/"+name[len(prefix):-4]
            for atlas in ("blocks","items"):
                assert stitched(sprite,atlas),("Texture exists but is absent from atlas",sprite,atlas)
                atlas_checks+=1
            texture_count+=1
        # Preview inventory-size icons at integer scaling, using their actual pack textures.
        image=Image.new("RGB",(600,268),(43,38,35));draw=ImageDraw.Draw(image)
        try:font=ImageFont.truetype("C:/Windows/Fonts/arial.ttf",16)
        except OSError:font=ImageFont.load_default()
        labels=["Missing: 10 remaining","Partial: 7 remaining","Complete: check only"]
        for col,label in enumerate(labels):draw.text((10+200*col,10),label,font=font,fill=(235,222,202))
        check=Image.open(io.BytesIO(pack.read("assets/servercore/textures/item/check.png"))).convert("RGBA")
        for row,material in enumerate(["diamond","amethyst_shard","apple"]):
            for col in range(3):
                sprite=Image.open(io.BytesIO((client.z.read(f"assets/minecraft/textures/item/{material}.png") if col==2 else pack.read(f"assets/servercore/textures/disabled/minecraft/item/{material}.png")))).convert("RGBA")
                sprite=sprite.resize((64,64),Image.Resampling.NEAREST)
                x,y=60+200*col,44+72*row
                image.paste(sprite,(x,y),sprite)
                if col==2:
                    overlay=check.resize((64,64),Image.Resampling.NEAREST);image.paste(overlay,(x,y),overlay)
                else:draw.text((x+42,y+43),"10" if col==0 else "7",font=font,fill="white",stroke_width=1,stroke_fill="black")
        image.save(Path(pack_path).parent/"collection_preview.png")
        print(f"Full pack: {json_count} JSON/metadata, {png_count} PNG, {references} references; {len(materials)} inactive/completed pairs, {texture_count} dark texture/alpha checks, {model_count} preserved geometries, {atlas_checks} atlas checks")

if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("--jar",required=True);args=parser.parse_args()
    verify("tools/build/servercore_pack.zip",args.jar)
