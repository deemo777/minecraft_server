"""Validate the skin draw catalog, new font panels, and the dedicated NPC in the ZIP."""
import io
import json
import re
import zipfile
from pathlib import Path
from PIL import Image
from make_skin_shop_art import SCREENS

HERE=Path(__file__).resolve().parent


def catalog():
    source=(HERE.parent/'src/main/java/com/htk/core/skins/WeaponSkinCatalog.java').read_text('utf8')
    pools={}
    for kind,body in re.findall(r'"(dagger|tachi|greatsword)",skins\("\1",(.*?)(?=\),|\)\);)',source,re.S):
        pools[kind]=re.findall(r'"([a-z_]+)","([^"]+)"',body)
    assert len(pools)==3 and all(len(values)==13 for values in pools.values())
    return pools


def verify():
    pools=catalog()
    with zipfile.ZipFile(HERE/'build/servercore_pack.zip') as pack:
        for kind,skins in pools.items():
            default={'dagger':'iron_dagger','tachi':'uchigatana','greatsword':'black_iron_greatsword'}[kind]
            assert default not in {skin[0] for skin in skins}
            for name,_ in skins:
                item=json.loads(pack.read(f'assets/servercore/items/weapons/{kind}/{name}.json'))
                assert item['model']=={'type':'minecraft:model','model':f'servercore:item/weapons/{kind}/{name}'}
        font=json.loads(pack.read('assets/servercore/font/forge.json'))
        glyphs={}
        for provider in font['providers']:
            for row in provider.get('chars',[]):
                for char in row:
                    assert char not in glyphs, f'Duplicate forge glyph {ord(char):04X}'
                    glyphs[char]=provider
        for name,(rows,first) in SCREENS.items():
            height=188+18*(rows-3)
            assembled=Image.new('RGBA',(352,height*2))
            for index in range(4):
                provider=glyphs[chr(first+index)]
                assert provider['file']==f'servercore:font/{name}_bg_{index}.png'
                assert provider['height']==height//2
                assert provider['ascent']==(33 if index<2 else 33-height//2)
                tile=Image.open(io.BytesIO(pack.read('assets/servercore/textures/'+provider['file'].split(':')[1])))
                assert tile.size==(176,height) and max(tile.size)<=256
                assembled.paste(tile,(index%2*176,index//2*height))
            assert assembled.tobytes()==Image.open(HERE/f'art/{name}_bg.png').convert('RGBA').tobytes()
            offset=18*(rows-3)
            # Native player inventory title must stay empty and readable.
            assert all(assembled.getpixel((x*2,(y+20)*2))[:3]==(197,189,205)
                       for x in range(9,166) for y in range(74+offset,82+offset)),name
        npc=json.loads(pack.read('assets/servercore/models/item/npc_skinmerchant.json'))
        assert npc['textures']['skin']=='servercore:item/skin_npc_skinmerchant'
        arms=json.loads(pack.read('assets/servercore/models/item/arms_merchant.json'))
        assert npc['elements']!=arms['elements'] and len(npc['elements'])>20
        assert pack.read('assets/servercore/textures/item/skin_npc_skinmerchant.png')!=pack.read('assets/servercore/textures/item/skin_arms_merchant.png')
        for element in npc['elements']:
            assert all(-16<=v<=32 for key in ('from','to') for v in element[key])
            for face in element['faces'].values():assert all(0<=v<=16 for v in face['uv'])
        assert json.loads(pack.read('assets/servercore/items/npc_skinmerchant.json'))['model']['model']=='servercore:item/npc_skinmerchant'
    print('PASS: 39 purchasable skins, 3 categories, 12 unique aligned font tiles, '
          '3 clear inventory captions, original artwork equality and dedicated skin merchant model/UVs.')


if __name__=='__main__':verify()
