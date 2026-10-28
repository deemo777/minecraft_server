"""기존 도구와 같은 팔레트·실제 XYZ 모델로 제작하는 단계별 낚싯대."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import math,json,argparse
import make_tool_models as b
HERE=Path(__file__).resolve().parent
TIERS=(0,5,10,15,20)
original_palette=b.palette_for
PALETTE=dict(b.COLORS)
PALETTE.update({'wood':'#745944','bark':'#72563e','barkhi':'#b89b70','barklo':'#493c33','leather':'#424049',
 'navy':'#304b63','navyhi':'#638398','navylo':'#263742','silver':'#a8c3ca','silverhi':'#e6eee6','silverlo':'#67858f',
 'gold':'#b89c64','goldhi':'#e5d6aa','goldlo':'#796849','azure':'#468aa0','azurehi':'#a8e0df','azurelo':'#365d75',
 'jade':'#387e77','jadehi':'#8ac5b1','jadelo':'#2e5558','violet':'#817aaf','pink':'#b4a6ce','ice':'#c1efeb','white':'#f5f0df'})
b.palette_for=lambda tool:PALETTE if tool=='village_fishing_rod' else original_palette(tool)

def line(m,points,color,width=.13,depth=.18,z=8):
 for a,c in zip(points,points[1:]):
  steps=max(1,math.ceil(math.dist(a,c)/.25))
  for i in range(steps):
   t=(i+.5)/steps;x=a[0]+(c[0]-a[0])*t;y=a[1]+(c[1]-a[1])*t
   m.box((x,y,z),(width,.32,depth),color,label='fine fishing line')
def ring(m,x,y,z,r,color,depth=.35):
 for i in range(16):
  t=2*math.pi*i/16
  m.box((x+r*math.cos(t),y+r*math.sin(t),z),(.28,.28,depth),color,label='open line guide / reel rim')
def rod(tier,cast=False):
 m=b.Model('village_fishing_rod',tier);rank=tier//5
 metal='iron' if rank==0 else 'gold' if rank==1 else 'silver'
 accent='jade' if rank<2 else 'azure' if rank==2 else 'violet' if rank==3 else 'ice'
 body='bark' if rank<2 else 'navy' if rank<4 else 'ivory'
 def x(y):return 5.7+.12*y+(max(0,y-23)**2*.022 if cast else 0)
 # 손잡이부터 끝까지 가늘어지는 입체 낚싯대.
 for i in range(54):
  y=1+i*.54;size=.9-(y/31)*.6
  color=body if i%6 else ('barkhi' if rank<2 else 'navyhi' if rank<4 else 'white')
  m.box((x(y),y,8),(size,.64,size),color,label='tapered rod blank')
 m.box((x(4.5),4.5,8),(1.18,6.6,1.22),'leather',label='leather grip')
 for y in [1.4,2.5,3.6,4.7,5.8,7.2]:
  m.box((x(y),y,8),(1.25,.22,1.28),metal if y in [1.4,7.2] else body,label='grip binding')
 # 릴 받침·입체 스풀·회전 손잡이.
 cx=x(9)+.65;cy=8.8;r=1.05+rank*.12
 m.box((x(8.7),8.7,8.8),(.55,2.5,.9),metal,label='reel seat')
 m.box((cx,cy,9.35),(r*1.45,r*1.45,1.4),'navylo' if rank>0 else 'barklo',label='reel drum')
 ring(m,cx,cy,10.1,r,metal,.5);ring(m,cx,cy,8.7,r,metal,.35)
 m.box((cx,cy,10.15),(.45,2*r,.3),metal,label='spool brace');m.box((cx,cy,10.15),(2*r,.4,.3),metal,label='spool brace')
 m.box((cx,cy,10.5),(.65,.65,.45),accent,label='reel hub')
 m.box((cx+r+.4,cy,9.7),(.4,1.9,.35),metal,label='crank');m.box((cx+r+.4,cy-1,10.1),(.65,.6,.9),'leather',label='crank handle')
 # 낚싯줄이 지나는 열린 가이드.
 for y in [12,17.5,23,29.6]:
  gx=x(y)
  m.box((gx,y,8.55),(.2,.28,.9),metal,label='guide foot');ring(m,gx,y,9.1,.35 if y<25 else .25,metal,.25)
 line(m,[(x(y),y) for y in [9.8,12,17.5,23,29.6]],'silverhi',.08,.08,9.1)
 if not cast:
  # 던진 상태에서는 매달린 찌를 숨긴다.
  bx=x(18)-3.1;by=18
  line(m,[(x(29.6),29.6),(bx,by+1.2)],'silverhi',.09,.09,9.15)
  m.box((bx,by,9.15),(.65,1.4,.65),accent,label='hanging float')
  m.box((bx,by-.6,9.15),(.65,.35,.65),'white',label='float stripe')
  m.box((bx,by+1,9.15),(.13,.8,.13),metal,label='float antenna')
  line(m,[(bx,by-.9),(bx-.2,by-1.5),(bx+.2,by-1.8),(bx+.55,by-1.45)],metal,.12,.12,9.15)
 if rank>=1:
  for y in [11,15.5,20.5,26]:m.box((x(y),y,8),(.78,.35,.78),'gold' if rank==1 else 'silver',label='segmented shaft collar')
 if rank>=2:
  # 물결 모양 장식은 가늘게 두어 낚싯대 윤곽을 유지.
  for sign in [-1,1]:
   pts=[(x(11),10.8),(x(13)+sign*1.7,12.2),(x(15)+sign*.55,15),(x(11),10.8)]
   line(m,pts,'silverhi',.23,.4,8)
  m.gem(x(11),11,8,accent,.65)
  line(m,[(x(13)+.3,13),(x(18)+.3,18),(x(23)+.2,23)],accent,.18,.3,8.55)
 if rank>=3:
  for y in [16,21.5]:
   for sign in [-1,1]:
    line(m,[(x(y),y),(x(y)+sign*1.4,y+1),(x(y)+sign*.3,y+2)],'silver',.2,.45,8)
  m.box((x(5),5,8.75),(.35,3.8,.2),'violet',label='inlaid handle')
 if rank==4:
  # 20강은 진주색 골격과 금빛 조개형 릴 케이지로 실루엣을 바꿈.
  for sign in [-1,1]:
   pts=b.cubic((cx,6.5),(cx+sign*3.6,7),(cx+sign*3.2,11.3),(cx,12.4),24)
   line(m,pts,'gold',.24,.6,9.6)
   line(m,[(x(19),19),(x(20)+sign*2,20),(x(23)+sign*.6,23)],'gold',.2,.45,8)
  m.gem(cx,cy,10.3,'ice',.6)
  m.box((x(4.7),4.7,8.7),(.55,3.6,.25),'gold',label='engraved pearl grip')
 return m

def data(m,cast):
 d=m.data();display=d['display']
 display['firstperson_righthand']={'rotation':[-8,-15,-22],'translation':[.6,1,-1.5],'scale':[.45]*3}
 display['firstperson_lefthand']={'rotation':[-8,-15,-22],'translation':[.6,1,-1.5],'scale':[.45]*3}
 display['thirdperson_righthand']={'rotation':[0,-90,0],'translation':[0,1.5,1],'scale':[.6]*3}
 display['thirdperson_lefthand']={'rotation':[0,90,0],'translation':[0,1.5,1],'scale':[.6]*3}
 d['credit']='ServerCore tide fishing rods / real reel, guides and cast variants'
 return d

def generate(preview=False):
 out=HERE/'models';tex=out/'textures';tex.mkdir(parents=True,exist_ok=True)
 palette=Image.new('RGBA',(64,64));draw=ImageDraw.Draw(palette)
 for i,key in enumerate(b.KEYS):
  x,y=i%8*8,i//8*8;draw.rectangle((x,y,x+7,y+7),fill=PALETTE[key])
 board=Image.new('RGB',(2000,670),'#eee9df');font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',22)
 for col,tier in enumerate(TIERS):
  name=f'village_fishing_rod_{tier}';palette.save(tex/(name+'.png'))
  for cast in [False,True]:
   m=rod(tier,cast);d=data(m,cast)
   assert all(-16<=a<b<=32 for e in d['elements'] for a,b in zip(e['from'],e['to']))
   (out/(name+('_cast' if cast else '')+'.json')).write_text(json.dumps(d,separators=(',',':')),encoding='utf-8')
   if preview:
    im=b.render(m,(8,-25,-25),(380,540),12)
    if not cast:board.paste(im,(col*400+10,55),im)
  if preview:ImageDraw.Draw(board).text((col*400+20,610),f'+{tier} / '+['TIDAL RELIC','SILVER NEEDLE','DEEP WHISPER','LEVIATHAN','NEPTURA'][col],font=font,fill='#3c4b57')
 if preview:
  path=HERE/'build/fishing_preview';path.mkdir(parents=True,exist_ok=True);board.save(path/'fishing_collection.png')
 print('Generated 5 fishing tiers and 5 cast variants; bounds verified.')
if __name__=='__main__':
 ap=argparse.ArgumentParser();ap.add_argument('--preview',action='store_true');generate(ap.parse_args().preview)
