"""Offline composition of the shipped panels, models and UVs (not a game capture)."""
import zipfile
from PIL import Image,ImageDraw,ImageFont
from make_skin_shop_art import HERE
from preview_weapon_animations import prepare
from verify_skin_shop_pack import catalog


def main():
    with zipfile.ZipFile(HERE/'build/servercore_pack.zip') as pack:
        def panel(name,rows):
            height=188+18*(rows-3);out=Image.new('RGBA',(352,height*2))
            for index in range(4):
                import io
                tile=Image.open(io.BytesIO(pack.read(f'assets/servercore/textures/font/{name}_bg_{index}.png')))
                out.paste(tile,(index%2*176,index//2*height))
            return out
        def item(image,slot,kind,name):
            icon=prepare(pack,kind,name)(8)
            bounds=icon.getbbox();icon=icon.crop(bounds)
            icon.thumbnail((30,30),Image.Resampling.NEAREST)
            x,y=(8+18*(slot%9))*2,(18+18*(slot//9)+20)*2
            image.alpha_composite(icon,(x+(32-icon.width)//2,y+(32-icon.height)//2))
        shop=panel('skin_shop',4)
        for slot,kind,name in [(11,'dagger','iron_dagger'),(13,'tachi','uchigatana'),(15,'greatsword','black_iron_greatsword')]:item(shop,slot,kind,name)
        result=panel('skin_result',3);item(result,13,'dagger','amethyst_greatblade_dagger')
        collection=panel('skin_collection',5)
        names=['iron_dagger']+[name for name,_ in catalog()['dagger']]
        for index,name in enumerate(names):item(collection,10+index if index<7 else 19+index-7,'dagger',name)
        font=ImageFont.truetype(str(HERE/'fonts/Galmuri11-Bold.ttf'),16)
        for image,rows in [(shop,4),(result,3),(collection,5)]:
            ImageDraw.Draw(image).text((16,(74+18*(rows-3)+20)*2),'보관함',font=font,fill='#41384c')
        panels=Image.new('RGB',(1096,500),'#171522');draw=ImageDraw.Draw(panels)
        for index,(image,label) in enumerate([(shop,'무기 종류 선택'),(result,'획득 결과'),(collection,'영구 도감 · 단검 전체 보유 예시')]):
            panels.paste(image,(12+index*364,36),image)
            draw.text((12+index*364,10),label,font=font,fill='#e3c995')
        panels.save(HERE/'build/skin_shop_dialogs_preview.png')
        npc_views=Image.new('RGB',(720,290),'#171522')
        for index,angle in enumerate((180,145,0)):
            npc=prepare(pack,model_path='assets/servercore/models/item/npc_skinmerchant.json',angle=angle)(0)
            npc_views.paste(npc,(index*240,30),npc)
        npc_views.save(HERE/'build/skin_merchant_preview.png')
        featured=Image.new('RGB',(624,460),'#171522')
        featured.paste(shop,(12,36),shop)
        npc=prepare(pack,model_path='assets/servercore/models/item/npc_skinmerchant.json',angle=145)(0)
        featured.paste(npc,(372,90),npc)
        d=ImageDraw.Draw(featured);d.text((12,10),'스킨 아틀리에 · 상점과 전용 NPC',font=font,fill='#e3c995')
        d.text((391,348),'스킨 상인',font=font,fill='#d0b9e4')
        d.text((391,376),'대사 없이 바로 입장',font=font,fill='#c0b4d0')
        featured.save(HERE/'build/skin_shop_preview.png')
    print('Saved skin shop, three dialogs and NPC front/side/back previews.')


if __name__=='__main__':main()
