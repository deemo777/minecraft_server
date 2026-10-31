"""Native pixel atelier screens; coordinates match SkinMerchantFeature inventory slots."""
import json
from pathlib import Path
from PIL import Image,ImageDraw
from make_blacksmith_art import fitted_label

HERE=Path(__file__).resolve().parent
SCREENS={'skin_shop':(4,0xE0B0),'skin_result':(3,0xE0B4),'skin_collection':(5,0xE0B8)}


def background(rows):
    height=188+18*(rows-3);offset=18*(rows-3)
    image=Image.new('RGBA',(176,height),'#181622');d=ImageDraw.Draw(image)
    def rect(box,fill,outline=None):
        x0,y0,x1,y1=box;d.rectangle((x0,y0+20,x1,y1+20),fill=fill,outline=outline)
    rect((0,-20,175,height-21),'#201e2d','#b69a66')
    rect((3,-17,172,height-24),'#30293f','#665670')
    rect((5,-15,170,0),'#45354f','#a68c60')
    rect((4,7,171,71+offset),'#211e30','#7a6485')
    for x in (8,167):
        for y in (-12,4,72+offset,160+offset):
            rect((x-1,y-1,x+1,y+1),'#dfc18b')
    # Quiet, empty caption strip for Minecraft's native dark inventory label.
    rect((6,73+offset,169,82+offset),'#c5bdcd')
    for row in range(3):
        for col in range(9):
            x,y=8+18*col,85+18*row+offset
            rect((x-1,y-1,x+16,y+16),'#100f1b','#6b5d7b')
            rect((x,y,x+15,y+15),'#332d45')
    for col in range(9):
        x=8+18*col
        rect((x-1,142+offset,x+16,159+offset),'#100f1b','#b69a66')
        rect((x,143+offset,x+15,158+offset),'#3b3248')
    return image,rect


def generate(name,rows):
    image,rect=background(rows)
    def slot(index):
        x,y=8+18*(index%9),18+18*(index//9)
        rect((x-1,y-1,x+16,y+16),'#d4ba83','#6b537a')
        rect((x,y,x+15,y+15),'#171725')
    def button(box):
        rect(box,'#3f304e','#b89a6b')
    labels=[]
    if name=='skin_shop':
        labels.append(((10,-14,165,-1),'스킨 아틀리에','#f2dfb7',18))
        labels.append(((15,8,160,19),'원하는 무기를 선택하세요','#bcb0ce',13))
        for index,(cx,text) in enumerate(((52,'단검'),(88,'태도'),(124,'대검'))):
            rect((cx-15,22,cx+14,54),'#30283e','#665174')
            slot((11,13,15)[index])
            labels.append(((cx-13,22,cx+12,33),text,'#ead6ad',14))
            button((cx-9,74,cx+8,87))
            labels.append(((cx-9,74,cx+8,87),'도감','#d7c5ec',12))
        labels.append(((20,57,155,69),'1회 100,000G · 중복 없음','#f2cc82',14))
        button((152,74,168,87));labels.append(((152,74,168,87),'닫기','#bcaecb',10))
    elif name=='skin_result':
        labels.append(((10,-14,165,-1),'새로운 스킨 획득','#f5dfaa',18))
        labels.append(((12,10,163,23),'계정 도감에 영구 등록','#c2b3d6',14))
        rect((69,28,106,55),'#44324f','#c6a775');slot(13)
        for cx,text in ((52,'도감 열기'),(124,'상점으로')):
            button((cx-22,56,cx+21,69))
            labels.append(((cx-22,56,cx+21,69),text,'#e3ccab',13))
    else:
        labels.append(((10,-14,165,-1),'스킨 도감','#f2dfb7',18))
        labels.append(((12,8,163,23),'보유 스킨을 주손 무기에 무료 적용','#c8b9dc',13))
        for index in list(range(10,17))+list(range(19,26)):slot(index)
        labels.append(((12,76,163,87),'미보유 스킨은 상점에서 획득','#a99bbd',12))
        button((65,92,110,105));labels.append(((65,92,110,105),'상점으로','#e3ccab',13))
    image=image.resize((image.width*2,image.height*2),Image.Resampling.NEAREST)
    for box,text,color,size in labels:fitted_label(image,box,text,color,size)
    image.save(HERE/f'art/{name}_bg.png')
    return image


def install_skin_shop_art(pack):
    sc=Path(pack)/'assets/servercore';font=sc/'font/forge.json'
    data=json.loads(font.read_text('utf8'))
    for name,(rows,first) in SCREENS.items():
        image=generate(name,rows);half=image.height//2
        assert half<=256 and half%2==0
        for index in range(4):
            x,y=(index%2)*176,(index//2)*half
            image.crop((x,y,x+176,y+half)).save(sc/f'textures/font/{name}_bg_{index}.png')
            data['providers'].append({'type':'bitmap','file':f'servercore:font/{name}_bg_{index}.png',
                'height':half//2,'ascent':33 if index<2 else 33-half//2,'chars':[chr(first+index)]})
    font.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf8')


if __name__=='__main__':
    for name,(rows,_) in SCREENS.items():generate(name,rows)
