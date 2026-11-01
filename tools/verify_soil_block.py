"""Validate the shipped native soil planter and render its actual meshes/UVs for review."""
import argparse
import io
import json
from pathlib import Path
from zipfile import ZipFile
from PIL import Image, ImageDraw
import preview_weapon_animations as renderer


def verify(path):
    with ZipFile(path) as pack:
        models=[]
        for name in ("soil_block","soil_block_wet","farm_plot"):
            item=json.loads(pack.read(f"assets/servercore/items/{name}.json"))
            assert item["model"]["model"]==f"servercore:item/{name}"
            model=json.loads(pack.read(f"assets/servercore/models/item/{name}.json"))
            assert "parent" not in model, "soil must use its own mesh, not a vanilla item model"
            assert len(model["elements"])>=10
            for element in model["elements"]:
                assert all(0<=a<=b<=16 for a,b in zip(element["from"],element["to"]))
                assert all(0<=v<=16 for face in element["faces"].values() for v in face["uv"])
            soil=[e for e in model["elements"] if all(f["texture"]=="#soil" for f in e["faces"].values())]
            assert soil and min(e["to"][1] for e in soil)>15, "custom soil surface must conceal native farmland's 15/16 top"
            for ref in model["textures"].values():
                with Image.open(io.BytesIO(pack.read("assets/servercore/textures/"+ref.split(":")[1]+".png"))) as image:
                    assert image.size==(32,32);image.verify()
            models.append(model)
        assert models[0]==models[2], "legacy inventory model must match the new soil block"
        assert models[0]["elements"]==models[1]["elements"], "dry/wet states must keep identical geometry"
        original=renderer.rotation
        renderer.rotation=lambda axis,angle: original(axis,28 if axis=="x" and angle==-8 else angle)
        try:
            images=[renderer.prepare(pack,model_path=f"assets/servercore/models/item/{name}.json",angle=-35)(0)
                    for name in ("soil_block","soil_block_wet")]
        finally: renderer.rotation=original
        output=Image.new("RGB",(renderer.SIZE*2,renderer.SIZE+35),(34,31,26))
        draw=ImageDraw.Draw(output)
        for i,image in enumerate(images):
            output.paste(image,(i*renderer.SIZE,0),image)
            draw.text((i*renderer.SIZE+18,renderer.SIZE+8),"Dry soil" if i==0 else "Watered soil",fill=(234,218,190))
        target=Path(path).parent/"soil_block_preview.png";output.save(target)
        print("Soil block: native mesh, dry/wet states, legacy alias, 32px PNG/UV references and farmland surface coverage passed")
        print(target)


if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("--pack",default="tools/build/servercore_pack.zip")
    verify(parser.parse_args().pack)
