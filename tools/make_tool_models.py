"""Fine sculpted Java item models: curved bevelled surfaces and XYZ relief.
Run with --preview to generate the model comparison and side views.
Requires Pillow and numpy (only the offline preview renderer uses numpy).
"""
import argparse
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import numpy as np

HERE=Path(__file__).resolve().parent
TOOLS=('village_pickaxe','village_axe','village_hoe','village_watering_can')
TIERS=(0,5,10,15,20)
COLORS={'wood':'#624336','leather':'#342c3f','iron':'#7e8a9e','steel':'#c5dbdf',
        'dark':'#292840','gold':'#dcb367','lightgold':'#fff0b6','copper':'#ad7855',
        'teal':'#38c4b2','ice':'#9cf5ff','blue':'#3871cb','violet':'#8a55d6',
        'pink':'#f287de','ruby':'#d72e59','fire':'#ff8c36','white':'#edffff',
        'green':'#57bc70','leaf':'#a4e175','black':'#171b30','water':'#679de2'}
KEYS=list(COLORS)


def sample_element_colors(elements, texture_path):
    """Sample each imported cuboid's UV patches for a useful shape preview."""
    with Image.open(texture_path) as source:
        texture=source.convert('RGBA')
    sx,sy=texture.width/16,texture.height/16
    result=[]
    for element in elements:
        pixels=[]
        for face in element.get('faces',{}).values():
            uv=face.get('uv')
            if not uv: continue
            x0=max(0,min(texture.width-1,round(min(uv[0],uv[2])*sx)))
            x1=max(x0+1,min(texture.width,round(max(uv[0],uv[2])*sx)))
            y0=max(0,min(texture.height-1,round(min(uv[1],uv[3])*sy)))
            y1=max(y0+1,min(texture.height,round(max(uv[1],uv[3])*sy)))
            patch=texture.crop((x0,y0,x1,y1))
            pixels.extend(patch.getdata())
        opaque=[pixel[:3] for pixel in pixels if len(pixel)>3 and pixel[3]>0]
        if opaque:
            rgb=np.asarray(opaque,dtype=np.uint8)
            color=np.median(rgb,axis=0).astype(int)
            result.append('#'+''.join(f'{component:02x}' for component in color))
        else:
            result.append('#777777')
    return result


class Model:
    def __init__(self,tool,tier):
        self.tool,self.tier=tool,tier
        self.elements=[]
        self.colors=[]

    def box(self,c,size,color,angle=0,axis='z',glow=False,label=''):
        f=[round(c[i]-size[i]/2,4) for i in range(3)]
        t=[round(c[i]+size[i]/2,4) for i in range(3)]
        idx=KEYS.index(color);u,v=(idx%8)*2,(idx//8)*2
        face={'uv':[u+.5,v+.5,u+1.5,v+1.5],'texture':'#0'}
        e={'name':label or color,'from':f,'to':t,'faces':{k:dict(face) for k in ('north','south','east','west','up','down')}}
        if angle: e['rotation']={'origin':list(c),'axis':axis,'angle':angle,'rescale':False}
        if glow: e.update(light_emission=15,shade=False)
        self.elements.append(e);self.colors.append(color)

    def gem(self,x,y,z,color,size=1.5):
        self.box((x,y,z),(size*1.6,size*1.6,2.4),'gold',45,label='gem setting')
        for sign in (-1,1):
            self.box((x,y,z+sign*1.45),(size,size,1.05),color,45,glow=True,label='gem crown')
            self.box((x,y,z+sign*2.05),(size*.48,size*.48,.4),'white',45,glow=True,label='gem point')

    def rib(self,y,width,color):
        self.box((8,y,8),(width,.55,width),color,label='four sided shaft collar')

    def data(self):
        if self.tool=='village_axe':
            source=HERE/'models'/'references'/'iron_greataxe.json'
            result=json.loads(source.read_text(encoding='utf-8'))
            texture=f'servercore:block/village_axe_{self.tier}'
            result['credit']='Iron Greataxe model from the user-provided Nongko Fantasy Weapons pack'
            result['textures']={key:texture for key in result.get('textures',{})}
            self.elements=result.get('elements',[])
            self.colors=sample_element_colors(self.elements, HERE/'models'/'textures'/'village_axe_0.png')
            return result
        can=self.tool=='village_watering_can'
        gui_scale=.42 if self.tool=='village_hoe' and self.tier==20 else .49
        result={'credit':'ServerCore fine sculpted volumetric tools',
            'textures':{'0':f'servercore:block/{self.tool}_{self.tier}','particle':f'servercore:block/{self.tool}_{self.tier}'},
            'gui_light':'side','elements':self.elements,
            'display':{
                'gui':{'rotation':[15,-25,0 if can else -35],'translation':[0,-1,0],'scale':[gui_scale]*3},
                'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.3]*3},
                'fixed':{'rotation':[0,180,0],'translation':[0,-1,0],'scale':[.55]*3},
                'thirdperson_righthand':{'rotation':[0,-90,0],'translation':[0,1.5,1],'scale':[.69]*3},
                'thirdperson_lefthand':{'rotation':[0,90,0],'translation':[0,1.5,1],'scale':[.69]*3},
                'firstperson_righthand':{'rotation':[-5,-20,0] if can else [-12,-12,-20],'translation':[1,-.3,-1] if can else [.6,.5,-2],'scale':[.75 if can else .58]*3},
                'firstperson_lefthand':{'rotation':[-5,20,0] if can else [-12,12,20],'translation':[1,-.3,-1] if can else [.6,.5,-2],'scale':[.75 if can else .58]*3},
                'head':{'rotation':[0,180,0],'translation':[0,9,0],'scale':[.6]*3}}}
        points=[]
        for e in self.elements:
            f,t=e['from'],e['to']
            p=np.array([[x,y,z] for x in (f[0],t[0]) for y in (f[1],t[1]) for z in (f[2],t[2])])
            if 'rotation' in e:
                r=e['rotation'];o=np.array(r['origin']);p=(p-o)@matrix(r['axis'],r['angle']).T+o
            points.extend(p)
        g=result['display']['gui'];r=g['rotation']
        view=matrix('z',r[2])@matrix('x',r[0])@matrix('y',r[1])
        pts=(np.array(points)-8)@view.T
        lo,hi=pts.min(axis=0),pts.max(axis=0)
        scale=min(.7,14.2/max(hi[0]-lo[0],hi[1]-lo[1]))
        g['scale']=[round(scale,5)]*3
        g['translation']=[round(-(lo[0]+hi[0])/2*scale,5),round(-(lo[1]+hi[1])/2*scale,5),0]
        return result



# Fine relief geometry. Curved blade surfaces are sculpted in XYZ with bevels.
COLORS.update({
    'ivory':'#f4e7ce','silver':'#b4cad9','silverhi':'#e7f3f8','silverlo':'#7896af',
    'navy':'#334268','navyhi':'#536b91','navylo':'#27314f',
    'goldhi':'#f4dc9b','goldlo':'#aa7d45','wine':'#5a294a','winehi':'#9a5270',
    'jade':'#2a7c76','jadehi':'#65c9ad','jadelo':'#225751',
    'amethyst':'#7560ad','amethysthi':'#b5a3ed','amethystlo':'#4e437a',
    'azure':'#4b9ac3','azurehi':'#a1deed','azurelo':'#316485',
    'rose':'#d88cba','rosehi':'#f4c2df','roselo':'#945f98',
    'cinder':'#8f4650','cinderhi':'#d67968','cinderlo':'#513449',
    'bark':'#563d42','barkhi':'#8c6256','barklo':'#362b37',
    'pearl':'#fff8e6','periwinkle':'#c5c6fa','aqua':'#6ee6da',
})
# Restricted cool pixel-art palette: ink silhouette, cold metal, one jewel accent.
COLORS.update({
    'ink':'#182039','leather':'#242a45','bark':'#34384b','barkhi':'#586079','barklo':'#252a3e',
    'navy':'#304569','navyhi':'#507da3','navylo':'#202d4c',
    'silver':'#8bb6d0','silverhi':'#d5f2f3','silverlo':'#537893',
    'ivory':'#b9d7e7','pearl':'#e9fcf7','white':'#e9fcf7',
    'gold':'#788cbb','goldhi':'#bdcce9','goldlo':'#485a84',
    'copper':'#656c86','lightgold':'#c7d7ef',
    'azure':'#32b8d2','azurehi':'#9df2ee','azurelo':'#29628e',
    'amethyst':'#9864c8','amethysthi':'#e6b3e6','amethystlo':'#5d448d',
    'jade':'#268f91','jadehi':'#8ae2b7','jadelo':'#254f6a',
    'rose':'#c45d9e','rosehi':'#f3abd8','roselo':'#70457e',
    'cinder':'#944d70','cinderhi':'#e98f95','cinderlo':'#493756',
    'fire':'#f0aa8c','water':'#487fb4','green':'#53b8a4','leaf':'#8adeb2',
    'ice':'#9fece9','teal':'#3bbac2',
})
KEYS=list(COLORS)


def cubic(a,b,c,d,n=64):
    return [tuple((1-t)**3*a[i]+3*(1-t)**2*t*b[i]+3*(1-t)*t*t*c[i]+t**3*d[i] for i in (0,1)) for t in np.linspace(0,1,n)]


def path(start,segments):
    out=[start];p=start
    for b,c,d in segments:
        out.extend(cubic(p,b,c,d,40)[1:]);p=d
    return out


def ribbon(a,b,c,d,width):
    points=cubic(a,b,c,d)
    left=[];right=[]
    for i,p in enumerate(points):
        q=points[min(i+1,len(points)-1)];r=points[max(i-1,0)]
        dx,dy=q[0]-r[0],q[1]-r[1];length=math.hypot(dx,dy) or 1
        w=width(i/(len(points)-1))
        left.append((p[0]-dy/length*w,p[1]+dx/length*w))
        right.append((p[0]+dy/length*w,p[1]-dx/length*w))
    return left+right[::-1]


def sculpt(m,outline,material='silver',rim='silverhi',thick=2.8,z=8,step=.24,label='curved blade'):
    """A continuous bevelled solid, narrower toward every boundary in all views.

    Depth is computed from the distance to the silhouette. Equal runs are merged,
    preserving genuine XYZ volume while keeping the vanilla element count bounded.
    """
    x0=math.floor(min(p[0] for p in outline)/step)*step-step
    y0=math.floor(min(p[1] for p in outline)/step)*step-step
    w=math.ceil((max(p[0] for p in outline)-x0)/step)+2
    h=math.ceil((max(p[1] for p in outline)-y0)/step)+2
    maskim=Image.new('1',(w,h));d=ImageDraw.Draw(maskim)
    d.polygon([((x-x0)/step,(y-y0)/step) for x,y in outline],fill=1)
    mask=np.array(maskim,dtype=bool);inside=mask.copy();dist=np.zeros_like(mask,dtype=np.int16)
    for _ in range(7):
        dist+=inside
        a=np.pad(inside,1)
        inside=a[1:-1,1:-1]&a[:-2,1:-1]&a[2:,1:-1]&a[1:-1,:-2]&a[1:-1,2:]
    hi=material+'hi' if material+'hi' in COLORS else material
    lo=material+'lo' if material+'lo' in COLORS else material
    active={};rects=[]
    for iy in range(h):
        current={}
        ix=0
        while ix<w:
            if not mask[iy,ix]: ix+=1;continue
            def spec(x):
                depth=.22+(thick-.22)*min(1,dist[iy,x]/6)
                shade=hi if iy<h-1 and dist[iy+1,x]<dist[iy,x] else lo if iy>0 and dist[iy-1,x]<dist[iy,x] else material
                color='ink' if dist[iy,x]<=1 else rim if dist[iy,x]<=2 else shade
                return round(depth,2),color
            dep,col=spec(ix);end=ix
            while end+1<w and mask[iy,end+1] and spec(end+1)==(dep,col): end+=1
            key=(ix,end,dep,col)
            current[key]=(active[key][0],iy) if key in active else (iy,iy)
            ix=end+1
        for key,extent in active.items():
            if key not in current: rects.append((key,extent))
        active=current
    rects.extend(active.items())
    for (ix,end,dep,col),(iy,ey) in rects:
        m.box((x0+(ix+end+1)*step/2,y0+(iy+ey+1)*step/2,z),
              ((end-ix+1)*step+.002,(ey-iy+1)*step+.002,dep),col,label=label)


def tube(m,points,z,color,r=.12,depth=.24):
    # Fine raised wire/engraving, present on the front and back of the tool.
    step=.14
    x0=min(x for x,y in points)-r-step;y0=min(y for x,y in points)-r-step
    w=math.ceil((max(x for x,y in points)-x0+r)/step)+2
    h=math.ceil((max(y for x,y in points)-y0+r)/step)+2
    im=Image.new('1',(w,h));draw=ImageDraw.Draw(im)
    draw.line([((x-x0)/step,(y-y0)/step) for x,y in points],fill=1,width=max(1,round(r*2/step)))
    mask=np.array(im);active={};rects=[]
    for iy in range(h):
        current={};ix=0
        while ix<w:
            if not mask[iy,ix]: ix+=1;continue
            end=ix
            while end+1<w and mask[iy,end+1]: end+=1
            key=(ix,end);current[key]=(active[key][0],iy) if key in active else (iy,iy);ix=end+1
        rects.extend((key,ext) for key,ext in active.items() if key not in current);active=current
    rects.extend(active.items())
    for (ix,end),(iy,ey) in rects:
        for sign in (-1,1):
            m.box((x0+(ix+end+1)*step/2,y0+(iy+ey+1)*step/2,8+sign*z),
                  ((end-ix+1)*step,(ey-iy+1)*step,depth),color,label='raised filigree')


def jewel(m,x,y,r,color='azure',z=8):
    # A pointed marquise rather than a square block. Layered, bevelled front/back.
    outline=path((x,y+r*1.5),[
        ((x+r*.5,y+r*.7),(x+r,y+r*.2),(x+r*.73,y-r*.35)),
        ((x+r*.5,y-r*.8),(x,y-r*1.5),(x,y-r*1.5)),
        ((x-r*.5,y-r*.8),(x-r,y-r*.1),(x-r*.73,y+r*.35)),
        ((x-r*.5,y+r*.8),(x,y+r*1.5),(x,y+r*1.5))])
    sculpt(m,outline,'gold','goldhi',2.8,z,step=.15,label='marquise bezel')
    inner=[(x+(a-x)*.73,y+(b-y)*.77) for a,b in outline]
    for sign in (-1,1):
        sculpt(m,inner,color,color+'hi' if color+'hi' in COLORS else 'pearl',1.2,z+sign*1.8,.14,label='faceted marquise')
        m.box((x-.16,y+r*.35,z+sign*2.45),(.2,.7,.18),'pearl',-22.5,glow=True,label='gem glint')


def shaft(m,k,accent):
    # Octagonal shaft, fine diagonal leather winding and sculpted end fittings.
    wood='bark' if k<2 else 'navy'
    m.box((8,5.5,8),(1.03,26.5,1.03),wood)
    m.box((8,5.5,8),(.87,26.5,.87),wood+'hi',45,axis='y')
    if m.tool!='village_pickaxe':
        for y in np.arange(-5.8,5.4,.85):
            for sign in (-1,1):
                m.box((8,y,8+sign*.65),(1.35,.2,.2),'leather',-22.5,label='leather winding')
    metal='copper' if k<2 else 'gold'
    for y in (-6.6,5.8,11.9,14):
        m.box((8,y,8),(1.55,.25,1.55),metal)
        m.box((8,y+.27,8),(1.24,.18,1.24),'ivory' if k>=2 else 'iron')
    pom=path((8,-9),[((9,-8.1),(9.5,-7.3),(8,-6.3)),((6.5,-7.3),(7,-8.1),(8,-9))])
    sculpt(m,pom,'gold' if k>=2 else 'copper','goldhi' if k>=2 else 'silver',1.6,step=.16,label='pommel')
    if k>=1: jewel(m,8,-7.5,.5,accent)
    for sign in (-1,1):
        m.box((8,9,8+sign*.7),(.3,4.5,.17),accent,label='handle inlay')
    if k>=2 and m.tool=='village_pickaxe':
        tube(m,cubic((7.4,6),(5.2,8),(10.7,10),(8.6,12)),.95,'goldhi',.11)
        for y in (7,9.8,12.6):
            m.box((8,y,6.75),(.3,.5,.2),'pearl',45)
def socket(m,k,accent,cy=20):
    # Elegant layered teardrop collar, narrow at the handle and broad at the head.
    shape=path((8,cy+3),[((10.7,cy+1),(10,cy-1),(8,cy-3)),((6,cy-1),(5.3,cy+1),(8,cy+3))])
    if m.tool=='village_pickaxe':
        shape=[(8,cy+2.7),(10,cy+1),(10,cy-1),(8,cy-2.7),(6,cy-1),(6,cy+1)]
    sculpt(m,shape,'gold' if k>=2 else 'copper','goldhi' if k>=2 else 'silverhi',3.5,step=.18,label='head socket')
    jewel(m,8,cy,1 if k<2 else 1.35,accent)


def leaf3d(m,x,y,z,scale=1,color='jade'):
    outline=path((x,y-1.6*scale),[((x-1.5*scale,y),(x-.8*scale,y+1.8*scale),(x,y+2.2*scale)),((x+1.1*scale,y+.6*scale),(x+scale,y),(x,y-1.6*scale))])
    sculpt(m,outline,color,color+'hi',.7,z,step=.17,label='carved leaf')
    m.box((x,y+.3*scale,z+.48),(.12,2.6*scale,.12),'goldhi',-22.5)


def fine_make(tool,tier):
    m=Model(tool,tier);k=tier//5
    if tool=='village_axe': return m
    accent={'village_pickaxe':('rose','rose','rose','rose','amethyst'),
            'village_hoe':('jade','jade','jade','jade','azure'),
            'village_watering_can':('azure','azure','azure','amethyst','azure')}[tool][k]
    metal=('amethyst','amethyst','amethyst','amethyst','amethyst')[k] if tool=='village_pickaxe' else ('silverlo','silver','navy','navy','silver')[k]
    if tool!='village_watering_can': shaft(m,k,accent)
    if tool=='village_pickaxe':
        # Broad, swept cutting wings with a clean bevel and restrained magenta inlay.
        scale=(.68,.74,.95,1.02,1.12)[k]
        blade_scale=scale*1.225
        blade_width=1.0 if k<2 else 1.35
        m.box((8,19.9,8),(1.45,6.6,1.35),'navy',label='reinforced pick neck')
        for sign in (-1,1):
            pts=ribbon((8,22.9),(8+sign*4.2,24.0),(8+sign*9.2,23.1),(8+sign*11.5,20.3),lambda t: blade_width*(1-t)**.72+.08)
            pts=[(8+(x-8)*blade_scale,21+(y-21)*blade_scale) for x,y in pts]
            sculpt(m,pts,metal,'silverhi',3.1 if k>=2 else 2.5,label='broad swept cutting blade')
            if k>=1:
                line=cubic((8+sign*1.6*blade_scale,21+2.15*blade_scale),(8+sign*5*blade_scale,21+2.6*blade_scale),(8+sign*8.5*blade_scale,21+1.7*blade_scale),(8+sign*10.4*blade_scale,21-.15*blade_scale))
                tube(m,line,1.6,'rosehi',.12)
            if k>=2:
                for z in (6.7,9.3):
                    m.box((8+sign*7.2*blade_scale,21+1.35*blade_scale,z),(1.15,.22,.16),'rosehi',-22.5,glow=True,label='precision blade inlay')
        if k==3:
            for sign in (-1,1): jewel(m,8+sign*4.7*blade_scale,23.0,.36,'rose')
        if k==4:
            sculpt(m,[(7.2,24.5),(8,27.3),(8.8,24.5)],'rose','rosehi',1.5,label='crystal spine')
            for sign in (-1,1):
                m.box((8+sign*9.3*scale,21.9,6.5),(1.1,.2,.18),'amethysthi',-22.5,glow=True,label='tier crest inlay')
                m.box((8+sign*9.3*scale,21.9,9.5),(1.1,.2,.18),'amethysthi',-22.5,glow=True,label='tier crest inlay')
        socket(m,k,accent,20.5+2.3*blade_scale)
    elif tool=='village_hoe':
        if k<2:
            pts=ribbon((9,21),(5,23),(1,23),(-1,20),lambda t:1.05*(1-t)+.06)
            sculpt(m,pts,'silverlo' if k==0 else 'silver','silverhi',2.2,label='young sickle')
        else:
            scale=(.83,.94,1.04)[k-2]
            pts=ribbon((9,22),(3,27),(-6,27),(-11,22),lambda t:2.05*(1-t)**.7+.03)
            pts=[(8+(x-8)*scale,22+(y-22)*scale) for x,y in pts]
            sculpt(m,pts,'navy' if k in (2,3) else 'silver',accent+'hi',3.1,label='crescent scythe')
            line=cubic((8,23),(2,26),(-5,26),(-9,23))
            line=[(8+(x-8)*scale,22+(y-22)*scale) for x,y in line]
            tube(m,line,1.75,'goldhi',.12)
            tube(m,cubic((6,23),(2,25),(0,22),(3,23)),1.8,'gold',.1)
            if k==4:
                wing=ribbon((8,23),(4,26),(-2,28),(-6,26.8),lambda t:.65*(1-t)+.04)
                sculpt(m,wing,'azure','azurehi',1.3,label='secondary petal blade')
                fine_halo(m,8,22,8,2.7)
                for x,y,z in ((-5,23,5),(-1,26,11),(11,21,8)):
                    leaf3d(m,x,y,z,.48,'jade')
        if k:
            tube(m,cubic((8,7),(3,11),(12,15),(8,19)),1.05,'goldlo' if k==1 else 'goldhi',.13)
            for x,y,z in ((6.6,10,7.3),(9.8,14,8),(6.6,17,8)):
                leaf3d(m,x,y,z,.55,'jade')
        if k>=3:
                for z in (5.8,10.2): flower(m,10,22,z,.8)
        socket(m,k,accent,21)
    else:
        fine_can(m,k,accent)
    return m


def fine_halo(m,x,y,z,r):
    # A fine continuous torus-like octagonal-depth lattice, not eight thick bars.
    for i in range(72):
        a=2*math.pi*i/72
        m.box((x,y+math.cos(a)*r,z+math.sin(a)*r),(.17,.24,.24),'goldhi',label='fine depth halo')
    for sign in (-1,1):
        for zoff in (-1,1):
            m.box((x,y+sign*r*.7,z+zoff*r*.7),(.45,.45,.45),'pearl',45)


def flower(m,x,y,z,r):
    for i in range(5):
        a=2*math.pi*i/5
        cx=x+math.cos(a)*r*.6;cy=y+math.sin(a)*r*.6
        circle=[(cx+math.cos(t)*r*.55,cy+math.sin(t)*r*.55) for t in np.linspace(0,2*math.pi,25)]
        sculpt(m,circle,'rose','rosehi',.5,z,step=.18,label='flower petal')
    jewel(m,x,y,r*.3,'azure',z)


def fine_can(m,k,accent):
    # Gardening watering can: low tank, open carrying handle, straight long spout,
    # perforated shower rose. No teapot lid, wings or suspended halo.
    b=m.box
    width=6.4+k*.25
    for y in np.arange(5.1,11.7,.24):
        t=(y-5.1)/6.6
        bevel=min(1,t*6,(1-t)*6)
        b((8,y,8),(width-.8+bevel*.8,.25,4.5+bevel*.6),'azure' if k<3 else 'navy',label='water tank')
    for y in (5.1,11.7):
        b((8,y,8),(width+.15,.3,5.3),'gold',label='tank rim')
    # Open fill opening on the top, clear of the carrying arch.
    b((8,12,8),(2.3,.3,2.3),'goldhi')
    b((8,12.17,8),(1.65,.12,1.65),'ink',label='fill opening')
    handle=path((4.6,11.5),[
        ((4,15.4),(4.8,17),(7.7,17)),
        ((10.7,17),(11.5,15.3),(11.2,11.5))])
    for x,y in handle[::2]:
        b((x,y,8),(.48,.48,.85),'gold' if k<2 else 'goldhi',label='carrying arch')
    b((7.7,16.9,8),(2.3,.65,1.05),'leather',label='handle grip')
    # Rear grip joins the left side, unlike the ornamental teapot handle.
    for a in np.linspace(-math.pi*.65,math.pi*.65,36):
        b((4.2-1.65*math.cos(a),8.3+2.35*math.sin(a),8),(.4,.45,.7),'gold',label='rear grip')
    # A long, gently rising tube rather than a tall swan-neck spout.
    for i in range(36):
        t=i/35;x=11+t*8.3;y=7.1+t*5.2;r=1.05-.48*t
        b((x,y,8),(.42,r*1.5,r*1.6),'azure' if i<10 else 'gold',label='straight spout')
    b((19.6,12.8,8),(.7,3.35,3.9),'goldhi',-45,label='shower rose')
    # Dark holes on the outer slanted face of the shower rose.
    for side in (-1,0,1):
        for zoff in (-1.1,0,1.1):
            b((19.92+side*.63,13.12-side*.63,8+zoff),(.18,.34,.34),'ink',-45,label='rose hole')
    for sign in (-1,1):
        z=8+sign*2.65
        # A little water drop emblem, not a giant jewellery panel.
        drop=path((8,10.4),[((8.3,9.8),(9.1,9),(8.8,8.2)),((8.3,7.3),(7,7.8),(7.2,8.6)),((7.3,9.2),(7.8,9.9),(8,10.4))])
        sculpt(m,drop,'azurehi','pearl',.35,z,step=.17,label='water drop emblem')
        if k>=1:
            for x in (5.2,10.8): b((x,8.4,z),(.25,4.8,.3),'goldhi',label='tank reinforcement')
        if k>=2:
            for y in (6.3,10.5): b((8,y,z),(width-.5,.23,.26),'goldhi',label='enamel stripe')
        if k>=3:
            for x in (6.2,9.8):
                b((x,8.5,z+sign*.2),(.45,.45,.2),'jadehi',45,label='small enamel flower')
    if k==4:
        for z in (5.2,10.8):
            for x in (5.4,10.6):
                leaf3d(m,x,8.5,z,.32,'jade')


def palette_for(tool):
    colors=dict(COLORS)
    if tool=='village_pickaxe':
        colors.update({'ink':'#21142f','bark':'#382653','barkhi':'#624889','barklo':'#251936',
            'navy':'#38245f','navyhi':'#604486','navylo':'#24163f','leather':'#29193f',
            'silver':'#ded8ed','silverhi':'#fff8ff','silverlo':'#9285ad','ivory':'#d2c5ea',
            'pearl':'#fff4ff','gold':'#84316e','goldhi':'#c34f9f','goldlo':'#501b48',
            'copper':'#70235d','lightgold':'#e0a8d2',
            'azure':'#8e257d','azurehi':'#d45cba','azurelo':'#501744',
            'teal':'#9c2d82','jade':'#8b276e','jadehi':'#c74b9a','jadelo':'#501b44',
            'rose':'#a8328f','rosehi':'#dc73c1','roselo':'#642354',
            'amethyst':'#4d267e','amethysthi':'#7049a3','amethystlo':'#2e164f'})
    elif tool=='village_hoe':
        colors.update({'gold':'#688d58','goldhi':'#c5dc9a','goldlo':'#46623e',
            'navy':'#304937','navyhi':'#567c4e','navylo':'#233832',
            'jade':'#6aaa3e','jadehi':'#cbf489','jadelo':'#3c6841',
            'azure':'#83c34b','azurehi':'#e3ffb2','azurelo':'#4e7c43',
            'silver':'#94bf7a','silverhi':'#e4f4ca','silverlo':'#507652',
            'rose':'#b77cc2','rosehi':'#ebc4ee','roselo':'#72567e',
            'leather':'#493746'})
    elif tool=='village_watering_can':
        colors.update({'azure':'#4097ad','azurehi':'#a3e0df','azurelo':'#326374',
            'navy':'#315974','navyhi':'#5389a1','navylo':'#243c54',
            'gold':'#b4b9b2','goldhi':'#eee5c8','goldlo':'#7b9095',
            'leather':'#665344','jade':'#4ba88b','jadehi':'#afd9ab'})
    return colors


make=fine_make



def matrix(axis,degrees):
    a=math.radians(degrees);c,s=math.cos(a),math.sin(a)
    if axis=='x': return np.array([[1,0,0],[0,c,-s],[0,s,c]])
    if axis=='y': return np.array([[c,0,s],[0,1,0],[-s,0,c]])
    return np.array([[c,-s,0],[s,c,0],[0,0,1]])


def render(m,angles=(18,-35,-25),size=(280,300),zoom=7.3):
    """Z-buffer renderer of the actual JSON cuboids including element rotations."""
    w,h=size;buf=np.full((h,w),-1e9);canvas=np.zeros((h,w,4),dtype=np.uint8)
    view=matrix('z',angles[2])@matrix('x',angles[0])@matrix('y',angles[1])
    bounds=[]
    for e in m.elements:
        f,t=e['from'],e['to']
        p=np.array([[x,y,z] for x in (f[0],t[0]) for y in (f[1],t[1]) for z in (f[2],t[2])])
        if 'rotation' in e:
            r=e['rotation'];o=np.array(r['origin']);p=(p-o)@matrix(r['axis'],r['angle']).T+o
        bounds.extend((p-np.array([8,10,8]))@view.T)
    bounds=np.array(bounds);lo,hi=bounds.min(axis=0),bounds.max(axis=0)
    center=(lo+hi)/2
    zoom=min(zoom,(w-24)/(hi[0]-lo[0]),(h-28)/(hi[1]-lo[1]))
    faces=[([0,1,3,2],.65),([4,6,7,5],.9),([0,4,5,1],.7),([2,3,7,6],1),([0,2,6,4],.78),([1,5,7,3],.88)]
    for e,key in zip(m.elements,m.colors):
        f,t=e['from'],e['to']
        pts=np.array([[x,y,z] for x in (f[0],t[0]) for y in (f[1],t[1]) for z in (f[2],t[2])])
        if 'rotation' in e:
            r=e['rotation'];o=np.array(r['origin']);pts=(pts-o)@matrix(r['axis'],r['angle']).T+o
        pts=(pts-np.array([8,10,8]))@view.T
        pts[:,0]=w/2+(pts[:,0]-center[0])*zoom;pts[:,1]=h/2-(pts[:,1]-center[1])*zoom
        color=key if key.startswith('#') else palette_for(m.tool)[key]
        rgb=np.array(tuple(bytes.fromhex(color[1:])))
        for face,shade in faces:
            q=pts[face];light=1 if e.get('light_emission') else shade
            color=np.array([*np.minimum(255,rgb*light).astype(int),255],dtype=np.uint8)
            for ids in ((0,1,2),(0,2,3)):
                a,b,c=q[list(ids)]
                x0=max(0,int(min(a[0],b[0],c[0])));x1=min(w-1,int(max(a[0],b[0],c[0]))+1)
                y0=max(0,int(min(a[1],b[1],c[1])));y1=min(h-1,int(max(a[1],b[1],c[1]))+1)
                if x1<x0 or y1<y0: continue
                yy,xx=np.mgrid[y0:y1+1,x0:x1+1];xx=xx+.5;yy=yy+.5
                den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
                if abs(den)<1e-8: continue
                u=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
                v=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den
                z=u*a[2]+v*b[2]+(1-u-v)*c[2];region=buf[y0:y1+1,x0:x1+1]
                mask=(u>=0)&(v>=0)&(u+v<=1)&(z>region)
                region[mask]=z[mask];canvas[y0:y1+1,x0:x1+1][mask]=color
    return Image.fromarray(canvas)


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--preview',action='store_true')
    ap.add_argument('--preview-dir',type=Path,default=HERE/'build/model_preview');args=ap.parse_args()
    out=HERE/'models';tex=out/'textures';tex.mkdir(parents=True,exist_ok=True)
    palette=Image.new('RGBA',(64,64));draw=ImageDraw.Draw(palette)
    for i,key in enumerate(KEYS):
        x,y=(i%8)*8,(i//8)*8;draw.rectangle((x,y,x+7,y+7),fill=COLORS[key])
    font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',19)
    small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',14)
    board=Image.new('RGB',(2100,2000),'#e8e1d5');d=ImageDraw.Draw(board)
    d.text((28,20),'VILLAGE RELICS / SCULPTED FANTASY COLLECTION',font=font,fill='#423e50')
    labels=('ROUGH RELIC','REINFORCED','AWAKENED','ASCENDED','LEGENDARY')
    names=('PICKAXE','AXE','HOE / SCYTHE','WATERING CAN')
    for row,tool in enumerate(TOOLS):
        for col,tier in enumerate(TIERS):
            model=make(tool,tier);name=f'{tool}_{tier}';data=model.data()
            for e in data.get('elements',[]):
                assert all(-16<=a<b<=32 for a,b in zip(e['from'],e['to']))
                if 'rotation' in e: assert e['rotation']['angle'] in (-45,-22.5,0,22.5,45)
            (out/(name+'.json')).write_text(json.dumps(data,separators=(',',':')),encoding='utf-8')
            palette=Image.new('RGBA',(64,64));palette_draw=ImageDraw.Draw(palette)
            for idx,key in enumerate(KEYS):
                x,y=(idx%8)*8,(idx//8)*8
                palette_draw.rectangle((x,y,x+7,y+7),fill=palette_for(tool)[key])
            if tool=='village_axe':
                art=tex/(name+'.png')
                if not art.is_file(): raise FileNotFoundError(f'Missing axe sprite texture: {art}')
                with Image.open(art) as sprite:
                    if sprite.mode!='RGBA' or sprite.getchannel('A').getbbox() is None:
                        raise ValueError(f'Axe sprite must be transparent RGBA: {art}')
                print(name,'static alpha sprite')
            else:
                palette.save(tex/(name+'.png'))
                print(name,len(data['elements']),'solid parts')
            if args.preview:
                args.preview_dir.mkdir(parents=True,exist_ok=True)
                tile=Image.new('RGB',(420,480),'#f1ece3')
                if tool=='village_axe':
                    front=render(model,(18,-35,-25),(280,310),7.3)
                    side=render(model,(12,-65,0),(110,310),6.2)
                    tile.paste(front,(0,65),front);tile.paste(side,(310,65),side)
                else:
                    front=render(model,(8,-18,-27),(310,385),8.2)
                    side=render(model,(12,-65,0),(110,385),7.4)
                    tile.paste(front,(0,53),front);tile.paste(side,(310,53),side)
                td=ImageDraw.Draw(tile);td.text((15,10),f'{names[row]} +{tier}',font=font,fill='#423e50')
                td.text((15,35),labels[col],font=small,fill='#8b7c70')
                td.line((15,60,400,60),fill='#dcd3c5')
                td.text((15,451),'IRON GREATAXE' if tool=='village_axe' else 'SCULPTED FACE',font=small,fill='#8b7c70');td.text((320,451),'SIDE',font=small,fill='#8b7c70')
                tile.save(args.preview_dir/(name+'.png'));board.paste(tile,(col*420,65+row*485))
    if args.preview:
        board.save(args.preview_dir/'collection.png');board.save(HERE.parent/'collection.png')
        board.save(args.preview_dir/'all_tools.png')
        depth=Image.new('RGB',(1500,1440),'#e9e4da')
        for row,tool in enumerate(TOOLS):
            for col,tier in enumerate((0,10,20)):
                if tool=='village_axe':
                    im=render(make(tool,tier),(15,-45,-20),(500,360),7.5)
                else:
                    im=render(make(tool,tier),(15,-45,-20),(500,360),7.5)
                depth.paste(im,(col*500,row*360),im)
                ImageDraw.Draw(depth).text((col*500+10,row*360+6),f'{names[row]} +{tier}',font=small,fill='#423e50')
        depth.save(args.preview_dir/'depth_comparison.png')
    print('Validated bounds and Java element rotation angles.')


if __name__=='__main__': main()
