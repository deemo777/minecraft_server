"""Pixel forge GUI + five dedicated enhancement materials. No external artwork.
Slots match EnhancementGui: tool=11, material=15, upgrade=22 (three rows).
"""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,shutil

HERE=Path(__file__).resolve().parent
TYPES=('pickaxe','axe','hoe','fishing_rod','watering_can')
COLORS=('#7acbec','#70b88b','#c0dc83','#a0b5ef','#89e1db')

def generate_art(folder=None):
    folder=Path(folder or HERE/'art');folder.mkdir(parents=True,exist_ok=True)
    im=Image.new('RGBA',(176,188),'#141c2b');d=ImageDraw.Draw(im)
    def rect(box,fill,outline=None,width=1):
        a,b,c,e=box;d.rectangle((a,b+20,c,e+20),fill=fill,outline=outline,width=width)
    rect((0,-20,175,167),'#171e2b','#a77950',2)
    rect((3,-17,172,164),'#242d3c','#3e4759')
    rect((5,-15,170,0),'#303747','#ad8657')
    # Small forge rivets and warm glowing upper rails.
    for x in (8,167):
        for y in (-12,6,72,160):
            rect((x-1,y-1,x+1,y+1),'#d5b27e')
    rect((8,7,167,72),'#1c2635','#536078')
    for cx in (52,124):
        # Slot center is (cx-.5, 43.5): outer frames share that exact center.
        rect((cx-18,27,cx+17,60),'#101823','#886b53',2)
        rect((cx-15,30,cx+14,57),'#293546','#486075')
        rect((cx-9,35,cx+8,52),'#101823','#e2be88')
        rect((cx-8,36,cx+7,51),'#18222f')
        for x in (cx-15,cx+13): rect((x,31,x+1,32),'#a98762')
    # Inward arrows, center forging spark and the button beneath it.
    for sign in (-1,1):
        x=88+sign*9
        rect((min(x,88),40,max(x,88),41),'#bd9970')
    d.polygon([(88,54),(91,61),(88,64),(85,61)],fill='#f0c48b')
    rect((72,53,104,71),'#100f18','#d8ad73',2)
    rect((75,56,101,68),'#8f4f3e','#e3b77c')
    # Inventory: draw the same coordinates as vanilla, without moving click targets.
    rect((6,80,169,138),'#1a2331','#566070')
    for row in range(3):
        for col in range(9):
            x,y=8+18*col,85+18*row
            rect((x-1,y-1,x+16,y+16),'#0e1620','#536072')
            rect((x,y,x+15,y+15),'#263345')
    for col in range(9):
        x=8+18*col
        rect((x-1,142,x+16,159),'#101721','#ac865a')
        rect((x,143,x+15,158),'#2e3849')
    im=im.resize((352,376),Image.Resampling.NEAREST)
    dd=ImageDraw.Draw(im)
    font_path=next((p for p in (HERE/'fonts/Galmuri11-Bold.ttf',Path('C:/Windows/Fonts/malgunbd.ttf')) if p.exists()),None)
    if font_path is None: raise RuntimeError('Korean font required: tools/fonts/Galmuri11-Bold.ttf')
    def label(box,text,color,size=16):
        x0,y0,x1,y1=box
        for pixels in range(size,7,-1):
            font=ImageFont.truetype(str(font_path),pixels)
            bounds=dd.textbbox((0,0),text,font=font)
            if bounds[2]-bounds[0] <= (x1-x0)*2-4 and bounds[3]-bounds[1] <= (y1-y0)*2-2: break
        assert bounds[2]-bounds[0] <= (x1-x0)*2-4
        assert bounds[3]-bounds[1] <= (y1-y0)*2-2
        # Center ink bounds, including font bearing/ascent, rather than the text origin.
        x=(x0+x1)-(bounds[0]+bounds[2])/2
        y=y0+y1+40-(bounds[1]+bounds[3])/2
        dd.text((round(x),round(y)),text,font=font,fill=color)
    label((12,-14,163,-1),'대장간','#f3d7a4',18)
    label((32,12,71,24),'장비','#c0d7e3')
    label((104,12,143,24),'전용 재료','#c0d7e3')
    label((76,56,100,69),'강화','#ffebc3',14)
    label((9,72,37,80),'가방','#b5c5d8',14)
    im.save(folder/'blacksmith_bg.png')
    for tool,color in zip(TYPES,COLORS):
        icon=Image.new('RGBA',(32,32));p=ImageDraw.Draw(icon)
        if tool=='pickaxe': points=[(7,24),(5,13),(11,6),(14,12),(20,3),(26,17),(21,27),(13,29)]
        elif tool=='axe': points=[(8,26),(5,16),(12,5),(18,10),(27,6),(24,20),(17,28)]
        elif tool=='hoe': points=[(16,29),(6,22),(5,12),(14,4),(25,8),(27,19),(21,25)]
        elif tool=='fishing_rod': points=[(5,24),(8,9),(19,4),(26,9),(23,19),(17,29),(10,27)]
        else: points=[(16,3),(25,15),(27,22),(23,28),(15,30),(7,27),(4,21),(7,14)]
        p.polygon(points,fill=color,outline='#16263a',width=2)
        p.line([(10,22),(12,13),(17,8)],fill='#edfff5',width=2)
        p.polygon([(15,13),(23,17),(18,26),(12,24)],fill='#3f687e')
        p.line([(15,13),(18,26)],fill=color,width=2)
        icon.save(folder/f'enhancement_{tool}.png')
    return folder

def install_blacksmith_assets(pack):
    pack=Path(pack);art=generate_art();sc=pack/'assets/servercore'
    texture=sc/'textures/font';texture.mkdir(parents=True,exist_ok=True)
    image=Image.open(art/'blacksmith_bg.png').convert('RGBA')
    for i,(x,y) in enumerate(((0,0),(176,0),(0,188),(176,188))):
        image.crop((x,y,x+176,y+188)).save(texture/f'blacksmith_bg_{i}.png')
    font=sc/'font/forge.json';font.parent.mkdir(parents=True,exist_ok=True)
    data={'providers':[{'type':'space','advances':{chr(0xF801+i):-(1<<i) for i in range(8)}}]}
    for i in range(4):
        top=-20+(94 if i>=2 else 0)
        data['providers'].append({'type':'bitmap','file':f'servercore:font/blacksmith_bg_{i}.png',
            'ascent':13-top,'height':94,'chars':[chr(0xE070+i)]})
    merchant=generate_merchant_art(art)
    image=Image.open(merchant).convert('RGBA')
    for i,(x,y) in enumerate(((0,0),(176,0),(0,188),(176,188))):
        image.crop((x,y,x+176,y+188)).save(texture/f'weapon_shop_bg_{i}.png')
        top=-20+(94 if i>=2 else 0)
        data['providers'].append({'type':'bitmap','file':f'servercore:font/weapon_shop_bg_{i}.png',
            'ascent':13-top,'height':94,'chars':[chr(0xE074+i)]})
    font.write_text(json.dumps(data,ensure_ascii=True,indent=2),encoding='utf-8')
    for tool in TYPES:
        name='enhancement_'+tool
        for folder in ('textures/item','models/item','items'): (sc/folder).mkdir(parents=True,exist_ok=True)
        shutil.copy2(art/(name+'.png'),sc/'textures/item'/(name+'.png'))
        (sc/'models/item'/(name+'.json')).write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'servercore:item/'+name}}),encoding='utf-8')
        (sc/'items'/(name+'.json')).write_text(json.dumps({'model':{'type':'minecraft:model','model':'servercore:item/'+name}}),encoding='utf-8')
    Image.new('RGBA',(16,16)).save(sc/'textures/item/forge_button.png')
    (sc/'models/item/forge_button.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'servercore:item/forge_button'}}),encoding='utf-8')
    (sc/'items/forge_button.json').write_text(json.dumps({'model':{'type':'minecraft:model','model':'servercore:item/forge_button'}}),encoding='utf-8')

def generate_merchant_art(folder):
    """Matching iron, copper and embers; frames align with the five purchase slots."""
    folder=Path(folder)
    # Preserve the forge's outer shell, inventory grid and hotbar coordinates.
    image=Image.open(folder/'blacksmith_bg.png').convert('RGBA')
    image=image.resize((176,188),Image.Resampling.NEAREST)
    d=ImageDraw.Draw(image)
    def rect(box,fill,outline=None,width=1):
        x0,y0,x1,y1=box;d.rectangle((x0,y0+20,x1,y1+20),fill=fill,outline=outline,width=width)
    rect((5,-15,170,0),'#303747','#ad8657')
    rect((8,7,167,72),'#1c2635','#536078')
    # A small anvil medallion above five individually framed tools.
    rect((75,8,100,33),'#101823','#886b53')
    rect((79,12,96,29),'#293546','#ad8657')
    for col in (1,2,4,6,7):
        x=8+18*col
        rect((x-2,33,x+17,54),'#101823','#a77950')
        rect((x-1,35,x+16,52),'#293546','#e2be88')
        rect((x,36,x+15,51),'#18222f')
        rect((x,55,x+15,56),'#8f4f3e')
    rect((69,57,106,73),'#100f18','#d8ad73')
    rect((72,59,103,71),'#8f4f3e','#e3b77c')
    image=image.resize((352,376),Image.Resampling.NEAREST)
    dd=ImageDraw.Draw(image)
    font_path=next(p for p in (HERE/'fonts/Galmuri11-Bold.ttf',Path('C:/Windows/Fonts/malgunbd.ttf')) if p.exists())
    def label(box,text,color,size):
        x0,y0,x1,y1=box
        for pixels in range(size,7,-1):
            font=ImageFont.truetype(str(font_path),pixels)
            b=dd.textbbox((0,0),text,font=font)
            if b[2]-b[0]<=(x1-x0)*2-4 and b[3]-b[1]<=(y1-y0)*2-2: break
        assert b[2]-b[0]<=(x1-x0)*2-4 and b[3]-b[1]<=(y1-y0)*2-2
        dd.text((round(x0+x1-(b[0]+b[2])/2),round(y0+y1+40-(b[1]+b[3])/2)),text,font=font,fill=color)
    label((12,-14,163,-1),'잊혀진 명장','#f3d7a4',18)
    label((12,12,71,28),'남겨 둔 장비','#c0d7e3',15)
    label((104,12,163,28),'새로운 여정','#c0d7e3',15)
    label((73,60,102,70),'돌아가기','#ffebc3',14)
    target=folder/'weapon_shop_bg.png';image.save(target)
    return target

if __name__=='__main__':
    print('Created forge art:',generate_art())
