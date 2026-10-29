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
        print(f'PASS: {len(documents)} JSON documents, {images} PNGs, {references} local references; forge, merchant and honor glyphs verified.')


if __name__=='__main__':
    verify(Path(__file__).resolve().parent/'build/servercore_pack.zip')
