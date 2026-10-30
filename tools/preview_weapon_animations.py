"""Render representative shipped meshes/UVs as an offline animated contact sheet.

Requires numpy and Pillow. This is an orthographic review, not a game capture.
"""
import io
import json
import math
from pathlib import Path
import zipfile

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
CASES = [
    ('dagger', 'iron_dagger'), ('dagger', 'amethyst_greatblade_dagger'),
    ('tachi', 'crystal_frostblade'), ('dagger', 'talonbrand_dagger'),
    ('dagger', 'demons_blood_blade_dagger'), ('greatsword', 'runic_piercer'),
    ('dagger', 'nature_sword'), ('tachi', 'moonlight'), ('tachi', 'void_oculus'),
]
SIZE = 240


def rotation(axis, angle):
    angle = math.radians(angle); c, s = math.cos(angle), math.sin(angle)
    if axis == 'x': return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y': return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def prepare(pack, category, name):
    model = json.loads(pack.read(f'assets/servercore/models/item/weapons/{category}/{name}.json'))
    textures = {}; meta = {}
    for slot, ref in model['textures'].items():
        if slot == 'particle': continue
        path = 'assets/servercore/textures/' + ref.split(':')[1] + '.png'
        textures[slot] = np.array(Image.open(io.BytesIO(pack.read(path))).convert('RGBA'))
        meta[slot] = json.loads(pack.read(path + '.mcmeta'))['animation']
    faces = []
    camera = rotation('x', -8) @ rotation('y', -23)
    for element in model['elements']:
        x0, y0, z0 = element['from']; x1, y1, z1 = element['to']
        quads = {
            'north': [(x1,y1,z0),(x0,y1,z0),(x0,y0,z0),(x1,y0,z0)],
            'south': [(x0,y1,z1),(x1,y1,z1),(x1,y0,z1),(x0,y0,z1)],
            'east': [(x1,y1,z1),(x1,y1,z0),(x1,y0,z0),(x1,y0,z1)],
            'west': [(x0,y1,z0),(x0,y1,z1),(x0,y0,z1),(x0,y0,z0)],
            'up': [(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)],
            'down': [(x0,y0,z1),(x1,y0,z1),(x1,y0,z0),(x0,y0,z0)],
        }
        for direction, face in element['faces'].items():
            points = np.array(quads[direction], dtype=float)
            transform = element.get('rotation')
            if transform:
                origin = np.array(transform['origin'])
                points = (points-origin) @ rotation(transform['axis'], transform['angle']).T + origin
            points = points @ camera.T
            u0,v0,u1,v1 = face['uv']
            uv = np.array([(u0,v0),(u1,v0),(u1,v1),(u0,v1)])
            uv = np.roll(uv, -face.get('rotation',0)//90, axis=0)
            faces.append((points, uv, face['texture'].lstrip('#')))
    bounds = np.concatenate([f[0] for f in faces])
    low, high = bounds.min(axis=0), bounds.max(axis=0)
    scale = min((SIZE-40)/(high[0]-low[0]), (SIZE-25)/(high[1]-low[1]))
    center = (high+low)/2
    depth = np.full((SIZE,SIZE), -np.inf)
    mappings = {slot: [np.full((SIZE,SIZE),-1,dtype=int),
                       np.zeros((SIZE,SIZE),dtype=int)] for slot in textures}
    selected = np.full((SIZE,SIZE), '', dtype='U16')
    for points, uv, slot in faces:
        points = points.copy(); points[:,:2] = (points[:,:2]-center[:2])*scale
        points[:,0] += SIZE/2; points[:,1] = SIZE/2-points[:,1]
        for indices in ((0,1,2),(0,2,3)):
            tri = points[list(indices)]; tuv = uv[list(indices)]
            x0,y0 = np.maximum(0,np.floor(tri[:,:2].min(axis=0)).astype(int))
            x1,y1 = np.minimum(SIZE-1,np.ceil(tri[:,:2].max(axis=0)).astype(int))
            if x1<x0 or y1<y0: continue
            yy,xx = np.mgrid[y0:y1+1,x0:x1+1]
            a,b,c = tri[:,:2]
            denominator = (b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
            if abs(denominator)<1e-8: continue
            wa = ((b[1]-c[1])*(xx+.5-c[0])+(c[0]-b[0])*(yy+.5-c[1]))/denominator
            wb = ((c[1]-a[1])*(xx+.5-c[0])+(a[0]-c[0])*(yy+.5-c[1]))/denominator
            wc = 1-wa-wb
            valid = (wa>=-1e-7)&(wb>=-1e-7)&(wc>=-1e-7)
            z = wa*tri[0,2]+wb*tri[1,2]+wc*tri[2,2]
            w,h = meta[slot]['width'],meta[slot]['height']
            u = np.clip(((wa*tuv[0,0]+wb*tuv[1,0]+wc*tuv[2,0])*w/16).astype(int),0,w-1)
            v = np.clip(((wa*tuv[0,1]+wb*tuv[1,1]+wc*tuv[2,1])*h/16).astype(int),0,h-1)
            valid &= (z>depth[yy,xx]) & (textures[slot][v,u,3]>0)
            y,x = yy[valid],xx[valid]
            depth[y,x]=z[valid]; selected[y,x]=slot
            mappings[slot][0][y,x]=u[valid]; mappings[slot][1][y,x]=v[valid]
    def render(index):
        output = np.zeros((SIZE,SIZE,4),dtype=np.uint8)
        for slot, texture in textures.items():
            mask = selected==slot; u,v = mappings[slot]
            output[mask] = texture[v[mask]+index*meta[slot]['height'],u[mask]]
        return Image.fromarray(output)
    return render


def main():
    with zipfile.ZipFile(HERE/'build/servercore_pack.zip') as pack:
        renderers = [prepare(pack,*case) for case in CASES]
        frames = []
        for frame in range(16):
            sheet = Image.new('RGB',(SIZE*3,(SIZE+40)*3),(24,29,40))
            draw = ImageDraw.Draw(sheet)
            for index, (render, case) in enumerate(zip(renderers, CASES)):
                x,y = index%3*SIZE,index//3*(SIZE+40)
                image = render(frame); sheet.paste(image,(x,y),image)
                draw.text((x+10,y+SIZE+5),case[1],fill=(213,221,235))
            draw.text((10,5),'Shipped mesh + UV review / normalized loop phase',fill=(153,172,195))
            frames.append(sheet)
        frames[8].save(HERE/'build/weapon_animations_preview.png')
        frames[0].save(HERE/'build/weapon_animations_preview.gif',save_all=True,
                       append_images=frames[1:],duration=160,loop=0,disposal=2)
        print('Preview saved: tools/build/weapon_animations_preview.gif (normalized theme timing)')


if __name__ == '__main__':
    main()
