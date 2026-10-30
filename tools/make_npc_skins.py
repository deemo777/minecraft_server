"""표준 64x64 UV에 부위별로 직접 그리는 깔끔한 NPC 스킨."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageColor
import json
HERE=Path(__file__).resolve().parent
UV={
'head':[(8,8,16,16),(0,8,8,16),(16,8,24,16),(24,8,32,16),(8,0,16,8),(16,0,24,8)],
'body':[(20,20,28,32),(16,20,20,32),(28,20,32,32),(32,20,40,32),(20,16,28,20),(28,16,36,20)],
'ra':[(44,20,48,32),(40,20,44,32),(48,20,52,32),(52,20,56,32),(44,16,48,20),(48,16,52,20)],
'la':[(36,52,40,64),(32,52,36,64),(40,52,44,64),(44,52,48,64),(36,48,40,52),(40,48,44,52)],
'rl':[(4,20,8,32),(0,20,4,32),(8,20,12,32),(12,20,16,32),(4,16,8,20),(8,16,12,20)],
'll':[(20,52,24,64),(16,52,20,64),(24,52,28,64),(28,52,32,64),(20,48,24,52),(24,48,28,52)]}
FACES=['north','east','west','south','up','down']
BOUNDS={'head':([4,24,4],[12,32,12]),'body':([4,12,6],[12,24,10]),'ra':([0,12,6],[4,24,10]),'la':([12,12,6],[16,24,10]),'rl':([4,0,6],[8,12,10]),'ll':([8,0,6],[12,12,10])}
# 머리·코트·안감·앞치마·바지·장식·참고 스킨.
ROLES={
'weapon_merchant':('#493a32','#315d60','#dbc9a4','#94694e','#353d40','#c9a166','purple_hair.png'),
'arms_merchant':('#d3ae6d','#293e59','#a44b50',None,'#303644','#bccbd4','gray_hair.png'),
'npc_mayor':('#c9c3b8','#535943','#8a4048',None,'#48454b','#d7bb7c','gray_hair.png'),
'npc_blacksmith':('#302c2c','#4c4a49','#b7a696','#975b48','#434348','#bf9b70','purple_hair.png'),
'npc_repairer':('#9a6143','#3b7375','#e7d5b5','#866449','#34444e','#c8ad6b','gray_hair.png'),
'npc_fish':('#685044','#405e76','#e0d7c3',None,'#3f4b57','#c9af77','gray_hair.png'),
'npc_crops':('#a47850','#63815b','#ede0b6','#c4a459','#4b5a47','#dfc58b','purple_hair.png'),
'npc_ores':('#bbb5ad','#49556d','#9c594d',None,'#424653','#c0946d','gray_hair.png'),
'deemo_chef':('#655f5d','#f0ede7','#dfd8d1','#c98e91','#4b4747','#c6a261',None)}
def rgb(c):return ImageColor.getrgb(c) if isinstance(c,str) else c[:3]
def tone(c,n):return tuple(max(0,min(255,v+n)) for v in rgb(c))
def outer(part):
 dx,dy={'head':(32,0),'body':(0,16),'ra':(0,16),'rl':(0,16),'la':(16,0),'ll':(-16,0)}[part]
 return [(a+dx,b+dy,c+dx,d+dy) for a,b,c,d in UV[part]]
def skin(role,colors):
 hair,coat,inner,apron,pants,accent,ref=colors;im=Image.new('RGBA',(64,64))
 original=Image.open(HERE/'art/deemo777_skin.png' if ref is None else HERE/'art/npc_references'/ref).convert('RGBA')
 for box in UV['head']+outer('head'):
  part=original.crop(box)
  if ref:
   for y in range(part.height):
    for x in range(part.width):
     rr,gg,bb,aa=part.getpixel((x,y))
     if aa and rr<200 and gg<200 and bb<210:part.putpixel((x,y),(*tone(hair,round(((rr+gg+bb)/3-105)*.52)),aa))
  im.paste(part,box[:2])
 # 참고 스킨의 긴 앞머리는 유지하되 눈 자리는 가리지 않게 직접 정리.
 if ref:
  front=im.crop((8,8,16,16));fd=ImageDraw.Draw(front)
  face_skin='#f4dbd1';shadow='#e6c5c0'
  fd.rectangle((1,3,6,7),fill=face_skin)
  fd.point((1,7),fill=shadow);fd.point((6,7),fill=shadow)
  fd.point((2,3),fill=tone(hair,-5));fd.point((5,3),fill=tone(hair,7))
  iris={'weapon_merchant':'#68816c','arms_merchant':'#557e9a','npc_mayor':'#7d98a9','npc_blacksmith':'#a77c58','npc_repairer':'#559a9b','npc_fish':'#7196ad','npc_crops':'#74986f','npc_ores':'#8d8ca5'}[role]
  for x in [1,5]:
   fd.rectangle((x,4,x+1,5),fill='#fff7ee')
   fd.point((x+(1 if x==1 else 0),5),fill=iris)
   fd.point((x,6),fill='#e8bdc0')
  im.paste(front,(8,8))
  # 투명 외피에서도 눈과 볼 영역을 비워 얼굴이 묻히지 않게 한다.
  hd=ImageDraw.Draw(im);hd.rectangle((41,12,46,15),fill=(0,0,0,0))
 for key,boxes in UV.items():
  if key=='head':continue
  for face,box in zip(FACES,boxes):
   w,h=box[2]-box[0],box[3]-box[1];base=pants if key in ['rl','ll'] else coat
   tile=Image.new('RGBA',(w,h),base);d=ImageDraw.Draw(tile)
   if face in ['east','west']:d.rectangle((0,0,w-1,h-1),fill=tone(base,-8))
   if face=='down':d.rectangle((0,0,w-1,h-1),fill=tone(base,-15))
   if face in ['north','south','east','west']:
    d.line((0,1,0,h-2),fill=tone(base,-13));d.line((w-1,1,w-1,h-2),fill=tone(base,-7))
    if key in ['rl','ll']:
     d.line((1,2,1,6),fill=tone(base,7));d.point((2,7),fill=tone(base,-10));d.rectangle((0,9,w-1,11),fill='#4d3e38');d.line((0,11,w-1,11),fill='#2e2d32');d.line((1,9,w-2,9),fill=accent)
    elif key in ['ra','la']:
     d.line((1,1,1,4),fill=tone(base,8));d.point((w-2,5),fill=tone(base,-12));d.line((0,7,w-1,7),fill=tone(inner,-8))
     hand='#655f5d' if role=='deemo_chef' else '#f1d5c5';d.rectangle((0,8,w-1,11),fill=hand);d.line((0,11,w-1,11),fill=tone(hand,-11))
     if role in ['weapon_merchant','arms_merchant','npc_blacksmith','npc_repairer','npc_ores']:d.rectangle((0,9,w-1,11),fill='#474044');d.line((0,9,w-1,9),fill=accent)
    elif key=='body':
     if face=='north':
      d.rectangle((2,0,5,10),fill=inner);d.polygon([(1,0),(3,0),(2,3)],fill=tone(coat,14));d.polygon([(6,0),(4,0),(5,3)],fill=tone(coat,14))
      if apron:
       d.rectangle((1,3,6,10),fill=apron);d.line((1,0,1,3),fill=apron);d.line((6,0,6,3),fill=apron);d.line((1,4,1,9),fill=tone(apron,10));d.line((6,4,6,10),fill=tone(apron,-12));d.rectangle((2,6,5,8),fill=tone(apron,-9));d.line((2,6,5,6),fill=tone(apron,17))
       if role=='deemo_chef':d.rectangle((2,6,5,8),fill='#f0e8dc');d.point((3,7),fill='#c98e91');d.point((4,8),fill='#73826b')
      else:
       for yy in [3,5,7,9]:d.point((4,yy),fill=accent)
      if role=='npc_mayor':d.line((0,1,0,9),fill=accent);d.line((7,1,7,9),fill=accent);d.rectangle((3,0,4,1),fill='#ede4d8')
      if role=='arms_merchant':
       # Crimson neck scarf, diagonal leather baldric, silver uniform buttons.
       d.rectangle((1,0,6,1),fill=inner);d.line((5,2,5,4),fill=tone(inner,-14))
       d.line((0,2,7,9),fill='#795338',width=2)
       d.point((1,5),fill=accent);d.point((1,7),fill=accent)
      if role in ['npc_fish','npc_ores']:
       scarf='#6f9c9c' if role=='npc_fish' else inner;d.rectangle((1,0,6,1),fill=scarf);d.line((4,2,4,4),fill=tone(scarf,-12))
     elif face=='south':
      d.line((w//2,1,w//2,8),fill=tone(base,-8))
      if apron:d.line((0,8,w-1,8),fill=apron)
     d.line((0,h-2,w-1,h-2),fill='#705144')
     if face=='north':d.rectangle((3,10,4,10),fill=accent)
   im.paste(tile,box[:2])
  for face,box in zip(FACES,outer(key)):
   w,h=box[2]-box[0],box[3]-box[1];layer=Image.new('RGBA',(w,h));d=ImageDraw.Draw(layer)
   if key=='body' and face=='north':d.line((0,2,0,8),fill=(*tone(coat,10),255));d.line((7,2,7,8),fill=(*tone(coat,-8),255))
   if key in ['ra','la'] and face in ['north','east','west','south']:d.line((0,7,w-1,7),fill=(*tone(inner,5),255))
   im.paste(layer,box[:2])
 if role=='arms_merchant':
  # Short swept blond hair under a cap; no long bangs or master's goggles.
  d=ImageDraw.Draw(im)
  for box in outer('head'):d.rectangle((box[0],box[1],box[2]-1,box[3]-1),fill=(0,0,0,0))
  d.rectangle((8,8,15,10),fill=hair);d.line((8,11,10,11),fill=tone(hair,-15))
  d.point((8,12),fill=hair);d.point((15,11),fill=hair)
 return im
COLORS=['#e9e4d9','#d0c9be','#c9a368','#87634b','#504744','#b6c3cb','#6a9ea7','#c28d91','#7c8f64','#c79f50','#3b4e64','#b16f52','#744b3e','#d8d3cb','#76906a','#b39bbd']
def props():
 im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
 for i,c in enumerate(COLORS):
  x=i%4*16;y=i//4*16;d.rectangle((x,y,x+15,y+15),fill=c);d.line((x,y,x+15,y),fill=tone(c,12));d.line((x,y,x,y+15),fill=tone(c,8));d.line((x+15,y,x+15,y+15),fill=tone(c,-10));d.line((x,y+15,x+15,y+15),fill=tone(c,-14))
 return im

def model(role):
 es=[]
 def cube(a,b,boxes,tex='skin'):
  es.append({'from':a,'to':b,'faces':{f:{'texture':'#'+tex,'uv':[v/4 for v in box]} for f,box in zip(FACES,boxes)}})
 for part,(a,b) in BOUNDS.items():
  cube(a,b,UV[part]);m=.23 if part=='head' else .12;cube([v-m for v in a],[v+m for v in b],outer(part))
 def prop(a,b,i):
  u=i%4*16;v=i//4*16;cube(a,b,[(u,v,u+16,v+16)]*6,'props')
 if role=='weapon_merchant':
  for x in [4.1,8.5]:prop([x,30.3,3.5],[x+3.4,32.3,3.9],2);prop([x+.5,30.7,3.4],[x+2.9,31.9,3.5],6)
  prop([9.4,10,10.2],[11.1,28,11.7],4);prop([8.4,25,10],[12.2,25.7,11.9],5);prop([10,25.7,10.6],[10.6,29,11.4],3);prop([-.1,22.6,5.8],[4.1,24.2,10.2],5);prop([12,10,6],[13.3,13,8.8],3)
 elif role=='arms_merchant':
  # Navy peaked cap, silver badge, one steel pauldron and a hip scabbard.
  prop([3.5,31.3,3.5],[12.5,34.5,12.5],10)
  prop([3.3,31.1,2.3],[12.7,31.8,5.3],10)
  prop([3.4,31.8,3.3],[12.6,32.5,3.6],5)
  prop([7,32.5,3.1],[9,33.8,3.5],2)
  prop([-.5,22.3,5.5],[4.5,24.5,10.5],5)
  prop([-.6,21.3,5.4],[4.6,22.1,10.6],10)
  prop([12.2,2.3,7.8],[13.4,15,9.2],10)
  prop([11.4,14.3,7.3],[14.3,15,9.7],5)
  prop([12.5,15,8.1],[13.1,18.3,8.9],3)
  prop([12.3,18.3,7.9],[13.3,19,9.1],2)
  prop([-.5,10,5.3],[2,13,7],3)
 elif role=='npc_mayor':
  prop([11.7,9,5.7],[12.9,14,6.8],3)
  for x in [10.3,11.2,12.1]:prop([x,9,5.4],[x+.35,11.7,5.7],2)
  prop([-.1,23,5.8],[4.1,24.1,10.2],2);prop([11.9,23,5.8],[16.1,24.1,10.2],2)
 elif role=='npc_blacksmith':
  prop([12.3,6.5,7],[12.9,15.7,7.7],3);prop([11,14.1,6.5],[14.2,16.1,8.3],5)
 elif role=='npc_repairer':
  # Copper headband, two raised goggles, a forked steel wrench and a belt pouch.
  prop([3.7,29.7,3.6],[12.3,30.6,4.1],3)
  for x in [4.5,8.5]:
   prop([x,30.2,3.1],[x+3,32.5,3.8],2);prop([x+.5,30.6,2.95],[x+2.5,32.1,3.1],6)
  prop([12.5,7.5,6.6],[13.3,15.5,7.4],5)
  prop([11.6,14.7,6.3],[14.2,15.7,7.7],5)
  prop([11.3,15.2,6.3],[12.1,17.6,7.7],5);prop([13.7,15.2,6.3],[14.5,17.6,7.7],5)
  prop([-.7,10,6],[1.8,13.7,9.5],3);prop([-.8,12.9,5.8],[1.9,13.7,9.7],2)
  for x in [5.5,9.5]:prop([x,17,5.7],[x+.5,20.1,6],5)
 elif role=='npc_fish':
  prop([3.5,31.6,3.5],[12.5,33.6,12.5],10);prop([3.2,31.4,2.7],[12.8,32,5],2);prop([-1,8,7],[1.6,14,10.5],3)
  for yy in [9,11,13]:prop([-1.1,yy,6.9],[1.7,yy+.3,7.1],2)
  prop([-.3,14,7.6],[.3,16,8.5],5);prop([.6,14,9],[1.1,15.7,9.7],6);prop([13.4,8,10.3],[13.8,29,10.7],3)
 elif role=='npc_crops':
  prop([1.9,31.5,1.9],[14.1,32.1,14.1],9);prop([4,32.1,4],[12,34.5,12],9);prop([3.9,32,3.9],[12.1,32.8,12.1],8);prop([12,11,6.2],[13,14.5,7.3],11);prop([12.2,14.5,6.5],[12.8,16.7,7],14)
 elif role=='npc_ores':
  prop([3.4,31.6,3.4],[12.6,34,12.6],3);prop([3,31.4,2.6],[13,32,5],2);prop([6.5,32,2.2],[9.5,34.2,3.5],5);prop([7,32.4,2.05],[9,33.8,2.2],6);prop([-.6,10,6.7],[2.3,13.5,9.5],3)
  for x,i in [(0,6),(1,15)]:prop([x,13.5,6.6],[x+.7,15,7.2],i)
  prop([12.2,10,10.3],[12.8,28,11],3);prop([10.7,26.7,10],[15,28.2,11.5],5)
 elif role=='deemo_chef':
  prop([3.6,31.8,3.6],[12.4,33.2,12.4],1);prop([3.2,33,3.2],[12.8,36.4,12.8],0);prop([4.2,36.3,4.2],[11.8,38.2,11.8],0);prop([2.7,33.5,4.5],[4,36,11.5],0);prop([12,33.5,4.5],[13.3,36,11.5],0);prop([12.3,9.5,7],[12.9,17.7,7.6],3);prop([12.1,11.2,6.8],[13.1,16,7.8],0)
 for e in es:
  for key in ['from','to']:
   x,y,z=e[key];e[key]=[8+(x-8)*.8,y*.8,8+(z-8)*.8]
 return {'textures':{'skin':'servercore:item/skin_'+role,'props':'servercore:item/npc_clean_props','particle':'servercore:item/skin_'+role},'elements':es,'gui_light':'front'}

def install_clean_npc_skins(pack):
 sc=Path(pack)/'assets/servercore';props().save(sc/'textures/item/npc_clean_props.png')
 for role,c in ROLES.items():
  skin(role,c).save(sc/f'textures/item/skin_{role}.png');(sc/f'models/item/{role}.json').write_text(json.dumps(model(role),indent=2),encoding='utf-8');(sc/f'items/{role}.json').write_text(json.dumps({'model':{'type':'minecraft:model','model':'servercore:item/'+role}}),encoding='utf-8')
 (sc/'models/item/npc_merchant.json').write_text(json.dumps({'parent':'servercore:item/weapon_merchant'}),encoding='utf-8');(sc/'items/npc_merchant.json').write_text(json.dumps({'model':{'type':'minecraft:model','model':'servercore:item/npc_merchant'}}),encoding='utf-8')
