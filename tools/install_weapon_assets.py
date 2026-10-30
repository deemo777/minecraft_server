import json,shutil
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from make_blacksmith_art import HERE, panel, display_slot, action_button, fitted_label, ACTION_BOX

def generate_arms_art(folder):
    """Three named weapon displays with inset slots and a contained return button."""
    folder=Path(folder)
    # Preserve the forge's outer shell, inventory grid and hotbar coordinates.
    image=Image.open(folder/'blacksmith_bg.png').convert('RGBA')
    image=image.resize((176,188),Image.Resampling.NEAREST)
    d=ImageDraw.Draw(image)
    def rect(box,fill,outline=None,width=1):
        x0,y0,x1,y1=box;d.rectangle((x0,y0+20,x1,y1+20),fill=fill,outline=outline,width=width)
    rect((5,-15,170,0),'#303747','#ad8657')
    panel(d)
    # Three symmetric displays, matching purchase slots 11,13,15.
    for col in (2,4,6):
        x=8+18*col
        display_slot(d,x)
    action_button(d)
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
    label((12,-14,163,-1),'무기 상인','#f3d7a4',18)
    for cx,name in ((52,'단검'),(88,'태도'),(124,'대검')):
        fitted_label(image,(cx-15,18,cx+14,30),name,'#c0d7e3',16)
    fitted_label(image,ACTION_BOX,'돌아가기','#ffebc3',14)
    target=folder/'arms_shop_bg.png';image.save(target)
    return target

def install_weapon_assets(pack):
    pack=Path(pack);sc=pack/'assets/servercore';source=Path(__file__).parent/'weapons'
    for category in source.iterdir():
        if not category.is_dir():continue
        for path in category.iterdir():
            if path.suffix=='.json':
                dst=sc/'models/item/weapons'/category.name/path.name
                dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,dst)
                item=sc/'items/weapons'/category.name/path.name
                item.parent.mkdir(parents=True,exist_ok=True)
                item.write_text(json.dumps({'model':{'type':'minecraft:model','model':f'servercore:item/weapons/{category.name}/{path.stem}'}}),encoding='utf8')
            else:
                dst=sc/'textures/item/weapons'/category.name/path.name
                dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,dst)
    # Enhancement gems (including weapons) are installed by install_blacksmith_assets.
    # Separate merchant title glyphs, reusing the established forge visual language.
    font=sc/'font/forge.json';data=json.loads(font.read_text('utf8'))
    image=generate_arms_art(Path(__file__).parent/'art')
    image=Image.open(image).convert('RGBA')
    for i,(x,y) in enumerate(((0,0),(176,0),(0,188),(176,188))):image.crop((x,y,x+176,y+188)).save(sc/'textures/font'/f'arms_shop_bg_{i}.png')
    old=[p for p in data['providers'] if p.get('chars') and ord(p['chars'][0][0]) in range(0xE074,0xE078)]
    for i,p in enumerate(old):
        q=dict(p);q['chars']=[chr(0xE0A0+i)];q['file']=f'servercore:font/arms_shop_bg_{i}.png';data['providers'].append(q)
    font.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf8')
    # install_clean_npc_skins owns arms_merchant; never alias it to the master.
