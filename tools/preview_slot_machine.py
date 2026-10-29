"""Render the actual generated model/UVs for design review; no concept-image substitutions."""
from pathlib import Path
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from make_slot_machine_art import slot_model, textures


def render(camera, width=480, height=680):
    camera = np.array(camera, dtype=float); camera /= np.linalg.norm(camera)
    right = np.array([camera[2], 0, -camera[0]]); right /= np.linalg.norm(right)
    up = np.cross(camera, right)
    color = np.zeros((height, width, 4), dtype=np.uint8); color[:] = (22, 28, 33, 255)
    depth = np.full((height, width), -np.inf)
    tex = {name: np.array(image) for name, image in textures().items()}
    scale = 17
    for element in slot_model()["elements"]:
        x, y, z = element["from"]; X, Y, Z = element["to"]
        faces = {
            "south": ([(x,Y,Z),(X,Y,Z),(X,y,Z),(x,y,Z)], [0,0,1]),
            "north": ([(X,Y,z),(x,Y,z),(x,y,z),(X,y,z)], [0,0,-1]),
            "east": ([(X,Y,Z),(X,Y,z),(X,y,z),(X,y,Z)], [1,0,0]),
            "west": ([(x,Y,z),(x,Y,Z),(x,y,Z),(x,y,z)], [-1,0,0]),
            "up": ([(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)], [0,1,0]),
            "down": ([(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)], [0,-1,0]),
        }
        for face, (vertices, normal) in faces.items():
            if np.dot(normal, camera) <= 0: continue
            points = np.array(vertices, dtype=float) - [8, 16, 8]
            screen = np.column_stack((width/2 + points @ right * scale,
                                      height/2 - points @ up * scale, points @ camera))
            texture = tex[element["faces"][face]["texture"][1:]]
            shade = {"south": 1.0, "east": .78, "up": 1.08}.get(face, .85)
            uv = np.array([[0,0],[1,0],[1,1],[0,1]], dtype=float)
            for indices in ((0,1,2),(0,2,3)):
                v = screen[list(indices)]; coords = uv[list(indices)]
                left = max(0, math.floor(v[:,0].min())); top = max(0, math.floor(v[:,1].min()))
                right_edge = min(width-1, math.ceil(v[:,0].max())); bottom = min(height-1, math.ceil(v[:,1].max()))
                if left > right_edge or top > bottom: continue
                px, py = np.meshgrid(np.arange(left,right_edge+1)+.5, np.arange(top,bottom+1)+.5)
                den = (v[1,1]-v[2,1])*(v[0,0]-v[2,0])+(v[2,0]-v[1,0])*(v[0,1]-v[2,1])
                if abs(den) < 1e-8: continue
                a = ((v[1,1]-v[2,1])*(px-v[2,0])+(v[2,0]-v[1,0])*(py-v[2,1]))/den
                b = ((v[2,1]-v[0,1])*(px-v[2,0])+(v[0,0]-v[2,0])*(py-v[2,1]))/den
                c = 1-a-b; zz = a*v[0,2]+b*v[1,2]+c*v[2,2]
                old = depth[top:bottom+1,left:right_edge+1]
                mask = (a >= -1e-6) & (b >= -1e-6) & (c >= -1e-6) & (zz > old)
                u = a*coords[0,0]+b*coords[1,0]+c*coords[2,0]
                vv = a*coords[0,1]+b*coords[1,1]+c*coords[2,1]
                sample = texture[np.clip((vv*texture.shape[0]).astype(int),0,texture.shape[0]-1),
                                 np.clip((u*texture.shape[1]).astype(int),0,texture.shape[1]-1)].copy()
                sample[:,:,:3] = np.clip(sample[:,:,:3].astype(float)*shade,0,255).astype(np.uint8)
                old[mask] = zz[mask]
                color[top:bottom+1,left:right_edge+1][mask] = sample[mask]
    return Image.fromarray(color)


if __name__ == "__main__":
    output = Path(__file__).resolve().parent / "build/slot_machine_preview.png"
    output.parent.mkdir(parents=True, exist_ok=True)
    canvas = Image.new("RGB", (1060, 840), (22, 28, 33)); d = ImageDraw.Draw(canvas)
    font_path = "C:/Windows/Fonts/segoeui.ttf"
    font = ImageFont.truetype(font_path, 27); small = ImageFont.truetype(font_path, 17)
    d.text((40, 24), "GOLDEN SLOT / 1 x 2 BLOCK CABINET", font=font, fill=(239,199,115))
    d.text((40, 65), "Actual model geometry and textures - front and three-quarter views", font=small, fill=(159,177,186))
    canvas.paste(render([0,0,1]), (20,100)); canvas.paste(render([.65,.35,1]), (550,100))
    d.text((40, 797), "Brass trim / wine-red panels / ivory reels / coin slot / payout tray / red lever", font=small, fill=(176,190,197))
    canvas.save(output); print(output)
