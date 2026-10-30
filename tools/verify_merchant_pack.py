"""Validate full-pack JSON/PNG references plus the forge-style merchant font layout."""
import io
import json
from pathlib import Path
import zipfile
from PIL import Image


def verify(path):
    with zipfile.ZipFile(path) as pack:
        assert pack.testzip() is None, 'ZIP CRC failure'
        names=set(pack.namelist()); documents={}; images=0; references=0
        for name in names:
            if name.endswith(('.json','.mcmeta')):
                documents[name]=json.loads(pack.read(name))
            elif name.endswith('.png'):
                with Image.open(io.BytesIO(pack.read(name))) as image: image.verify()
                images+=1
        def reference(value, folder, extension):
            nonlocal references
            if not isinstance(value,str) or not value.startswith('servercore:'): return
            target='assets/servercore/'+folder+'/'+value.split(':',1)[1]+extension
            assert target in names, (value,target)
            references+=1
        def walk(value):
            if isinstance(value,list):
                for item in value: walk(item)
            elif isinstance(value,dict):
                for key,item in value.items():
                    if key in ('model','parent'): reference(item,'models','.json')
                    if key=='file': reference(item,'textures','')
                    if key=='textures' and isinstance(item,dict):
                        for texture in item.values(): reference(texture,'textures','.png')
                    walk(item)
        for document in documents.values(): walk(document)
        # Every runtime equipment type must resolve to its own transparent gem,
        # including weapons (the weapon installer used to overwrite these IDs).
        import re
        source=(Path(__file__).resolve().parents[1]/'src/main/java/com/htk/core/tools/ToolType.java').read_text(encoding='utf-8')
        kinds=re.findall(r'^    [A-Z_]+\("([a-z_]+)",',source,re.MULTILINE)
        assert len(kinds)==8, kinds
        gems=set(); silhouettes=set()
        for kind in kinds:
            stem='enhancement_'+kind
            assert documents[f'assets/servercore/items/{stem}.json']['model']=={'type':'minecraft:model','model':'servercore:item/'+stem}
            model=documents[f'assets/servercore/models/item/{stem}.json']
            assert model['parent']=='minecraft:item/generated'
            assert model['textures']=={'layer0':'servercore:item/'+stem}
            raw=pack.read(f'assets/servercore/textures/item/{stem}.png')
            assert raw==(Path(__file__).resolve().parent/'art'/f'{stem}.png').read_bytes()
            with Image.open(io.BytesIO(raw)) as gem:
                assert gem.size==(32,32) and gem.mode=='RGBA'
                alpha=gem.getchannel('A')
                assert set(alpha.getdata())=={0,255}, 'gem must have crisp transparent edges'
                box=alpha.getbbox()
                assert box and 0<box[0]<box[2]<32 and 0<box[1]<box[3]<32
                assert len(gem.getcolors())>=7, 'missing gemstone facets'
                silhouettes.add(alpha.tobytes())
            gems.add(raw)
        assert len(gems)==len(silhouettes)==len(kinds), 'gem designs and cuts must be distinct'
        font=documents['assets/servercore/font/forge.json']
        glyphs={provider['chars'][0]:provider for provider in font['providers'] if provider['type']=='bitmap'}
        for index in range(4):
            provider=glyphs[chr(0xE074+index)]
            assert provider['file']==f'servercore:font/weapon_shop_bg_{index}.png'
            assert provider['height']==94 and provider['ascent']==(33 if index<2 else -61)
            with Image.open(io.BytesIO(pack.read('assets/servercore/textures/font/weapon_shop_bg_'+str(index)+'.png'))) as tile:
                assert tile.size==(176,188)
        assert all(chr(code) in glyphs for code in range(0xE070,0xE074)), 'existing forge glyphs lost'
        assert font['providers'][0]['advances']['\uf808']==-128
        for index in range(4):
            provider=glyphs[chr(0xE07C+index)]
            assert provider['file']==f'servercore:font/repair_bg_{index}.png'
            assert provider['height']==94 and provider['ascent']==(33 if index<2 else -61)
            with Image.open(io.BytesIO(pack.read(f'assets/servercore/textures/font/repair_bg_{index}.png'))) as tile:
                assert tile.size==(176,188)
        for button in ('forge_button','forge_button_disabled','repair_button_disabled'):
            assert documents[f'assets/servercore/items/{button}.json']['model']=={'type':'minecraft:empty'}, 'button items must not paint over the background'
            assert f'assets/servercore/models/item/{button}.json' not in names, 'obsolete button overlay model'
            assert f'assets/servercore/textures/item/{button}.png' not in names, 'obsolete button overlay texture'
        # Inspect the pixels actually shipped, rather than just generator constants.
        for background in ('blacksmith','repair','weapon_shop','arms_shop'):
            full=Image.new('RGBA',(352,376))
            for index in range(4):
                with Image.open(io.BytesIO(pack.read(f'assets/servercore/textures/font/{background}_bg_{index}.png'))) as tile:
                    full.paste(tile,(176*(index%2),188*(index//2)))
            def pixel(x,y):return full.getpixel((x*2,(y+20)*2))[:3]
            # Bottom panel border stays intact, with two empty rows under button.
            assert all(pixel(x,71)==(83,96,120) for x in range(4,172)), background
            assert all(pixel(x,y)==(28,38,53) for x in range(6,170) for y in (69,70)), background
            # No baked 'bag' label anywhere in vanilla's inventory caption strip.
            assert all(pixel(x,y)==(181,193,204) for x in range(6,170) for y in range(73,83)), background
        arms=documents['assets/servercore/items/arms_merchant.json']['model']
        assert arms=={'type':'minecraft:model','model':'servercore:item/arms_merchant'}
        dealer=documents['assets/servercore/models/item/arms_merchant.json']
        master=documents['assets/servercore/models/item/weapon_merchant.json']
        assert dealer['textures']['skin']=='servercore:item/skin_arms_merchant'
        assert dealer['elements']!=master['elements'], 'arms dealer must have its own silhouette'
        assert pack.read('assets/servercore/textures/item/skin_arms_merchant.png')!=pack.read('assets/servercore/textures/item/skin_weapon_merchant.png')
        for element in dealer['elements']:
            assert all(-16<=v<=32 for key in ('from','to') for v in element[key])
            for face in element['faces'].values():
                assert all(0<=v<=16 for v in face['uv'])
        npc=documents['assets/servercore/models/item/npc_repairer.json']
        assert len(npc['elements'])>12, 'repairer must include its own goggles and wrench'
        assert npc['textures']['skin']=='servercore:item/skin_npc_repairer'
        for element in npc['elements']:
            assert all(-16<=v<=32 for key in ('from','to') for v in element[key]), 'NPC model outside supported bounds'
        gui=documents['assets/servercore/font/gui.json']
        honor={}
        seen=set()
        for provider in gui['providers']:
            if provider['type'] != 'bitmap': continue
            for row in provider['chars']:
                for char in row:
                    assert char not in seen, f'duplicate GUI glyph: {ord(char):04X}'
                    seen.add(char)
                    honor[char]=provider
        for index in range(4):
            provider=honor[chr(0xE078+index)]
            assert provider['file']==f'servercore:font/honor_bg_{index}.png'
            assert provider['height']==121 and provider['ascent']==(33 if index<2 else -88)
            with Image.open(io.BytesIO(pack.read('assets/servercore/textures/font/honor_bg_'+str(index)+'.png'))) as tile:
                assert tile.size==(176,242)
        print(f'PASS: {len(documents)} JSON documents, {images} PNGs, {references} local references; {len(kinds)} unique equipment gems; 4 clear forge layouts; distinct arms NPC; forge, merchant and honor glyphs verified.')


if __name__=='__main__':
    verify(Path(__file__).resolve().parent/'build/servercore_pack.zip')
