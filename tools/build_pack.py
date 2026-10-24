"""
ServerCore 리소스팩 만들기

  python build_pack.py --jar "C:\\Users\\내이름\\AppData\\Roaming\\.minecraft\\versions\\26.3\\26.3.jar" \
                       --dogam "..\\src\\main\\resources\\dogam.yml"

하는 일
  1. dogam.yml 의 항목을 순서대로 읽어서 (0번, 1번, ...)
  2. 마인크래프트 jar 안의 텍스처로 항목마다 32x32 아이콘을 만들고
     - 일반 아이템: 아이템 그림 그대로
     - 꽉 찬 블록(돌, 원목, 호박...): 인벤토리처럼 입체로 그림
     - 그 밖의 특수한 것: 찾을 수 있는 그림 중 하나로 (평면)
  3. pack_template 의 고정 그림(체크, 숫자, 화살표, 막대, 버튼)과 합치고
  4. 폰트 정의(font/gui.json)와 pack.mcmeta 를 쓰고
  5. zip 으로 묶고 SHA-1 값을 알려줘요

⚠ 글자 번호 규칙은 플러그인의 Glyphs.java 와 같아야 해요.
필요한 것: Python 3.10+, pip install pillow pyyaml
"""
import argparse
import hashlib
import io
import json
import os
import shutil
import sys
import zipfile

try:
    import yaml
    from PIL import Image
except ImportError:
    sys.exit("먼저 설치하세요:  pip install pillow pyyaml")

HERE = os.path.dirname(os.path.abspath(__file__))
TEMPLATE = os.path.join(HERE, "pack_template")

ICON_BASE = 0xE100
ICON_ASCENT, ICON_HEIGHT = 11, 16

# 회색으로 저장돼 있어서 게임이 색을 입히는 텍스처들 (대략적인 기본 색)
TINT = {
    "grass_block": "#7cbd6b", "short_grass": "#7cbd6b", "tall_grass": "#7cbd6b", "fern": "#7cbd6b",
    "large_fern": "#7cbd6b", "vine": "#59ae30", "lily_pad": "#208030", "sugar_cane": "#7cbd6b",
    "oak_leaves": "#59ae30", "jungle_leaves": "#59ae30", "acacia_leaves": "#59ae30",
    "dark_oak_leaves": "#59ae30", "mangrove_leaves": "#59ae30", "spruce_leaves": "#619961",
    "birch_leaves": "#80a755",
}


# ======================================================================
#  jar 읽기
# ======================================================================
class Jar:
    def __init__(self, path):
        self.z = zipfile.ZipFile(path)
        self.names = set(self.z.namelist())

    def has(self, p):
        return p in self.names

    def json(self, p):
        return json.loads(self.z.read(p).decode("utf-8")) if self.has(p) else None

    def image(self, p):
        if not self.has(p):
            return None
        im = Image.open(io.BytesIO(self.z.read(p))).convert("RGBA")
        if im.height > im.width:  # 움직이는 텍스처 → 첫 장면만
            im = im.crop((0, 0, im.width, im.width))
        return im

    def version_pack_format(self):
        v = self.json("version.json") or {}
        pv = v.get("pack_version")
        if isinstance(pv, int):
            return pv
        if isinstance(pv, dict):
            for k in ("resource_major", "resource"):
                if isinstance(pv.get(k), int):
                    return pv[k]
        return None


def strip_ns(ref):
    return ref.split(":", 1)[1] if ":" in ref else ref


def tex_path(ref):
    return f"assets/minecraft/textures/{strip_ns(ref)}.png"


def model_chain(jar, model_ref):
    """모델과 그 부모들을 따라가며 (이름 목록, 텍스처 변수 합친 것)을 돌려줘요"""
    chain, textures = [], {}
    ref = model_ref
    for _ in range(20):
        name = strip_ns(ref)
        chain.append(name)
        data = jar.json(f"assets/minecraft/models/{name}.json")
        if data is None:
            break
        for k, v in (data.get("textures") or {}).items():
            textures.setdefault(k, v)  # 자식 값이 우선
        parent = data.get("parent")
        if not parent:
            break
        ref = parent
    return chain, textures


def resolve(textures, key):
    v = textures.get(key)
    for _ in range(10):
        if v is None:
            return None
        if v.startswith("#"):
            v = textures.get(v[1:])
        else:
            return v
    return None


def first_model_ref(node):
    """items/*.json 의 model 정의 안에서 처음 나오는 모델 이름 찾기"""
    if isinstance(node, dict):
        if node.get("type", "").endswith("model") and isinstance(node.get("model"), str):
            return node["model"]
        for k in ("model", "fallback", "on_true", "on_false", "cases", "entries", "models"):
            if k in node:
                r = first_model_ref(node[k])
                if r:
                    return r
        for v in node.values():
            r = first_model_ref(v)
            if r:
                return r
    elif isinstance(node, list):
        for v in node:
            r = first_model_ref(v)
            if r:
                return r
    return None


# ======================================================================
#  아이콘 그리기
# ======================================================================
def tinted(im, hex_color):
    if im is None or not hex_color:
        return im
    r, g, b = (int(hex_color[i:i + 2], 16) for i in (1, 3, 5))
    out = im.copy()
    px = out.load()
    for x in range(out.width):
        for y in range(out.height):
            pr, pg, pb, pa = px[x, y]
            px[x, y] = (pr * r // 255, pg * g // 255, pb * b // 255, pa)
    return out


def flat_icon(tex):
    tex = tex.resize((16, 16), Image.NEAREST) if tex.size != (16, 16) else tex
    return tex.resize((32, 32), Image.NEAREST)


def iso_cube(top, south, east):
    """인벤토리에 보이는 것처럼 입체 블록 (32x32)"""
    S = 32
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    po = out.load()
    faces = []
    for tex, shade in ((top, 1.0), (south, 0.80), (east, 0.62)):
        t = tex.resize((16, 16), Image.NEAREST) if tex.size != (16, 16) else tex
        faces.append((t.load(), shade))

    def sample(face, u, v):
        pt, shade = faces[face]
        tx = min(15, max(0, int(u * 16)))
        ty = min(15, max(0, int(v * 16)))
        r, g, b, a = pt[tx, ty]
        return (int(r * shade), int(g * shade), int(b * shade), a)

    for py in range(S):
        for px in range(S):
            sx, sy = px + 0.5, py + 0.5
            # 윗면 (y=1): sx = 16 + (u - v)*14, sy = 1 + (u + v)*7
            a_ = (sx - 16) / 14
            b_ = (sy - 1) / 7
            u, v = (a_ + b_) / 2, (b_ - a_) / 2
            if 0 <= u < 1 and 0 <= v < 1:
                col = sample(0, u, v)
                if col[3] > 0:
                    po[px, py] = col
                continue
            # 남쪽 면 (z=1, 왼쪽 아래): x = (sx-16)/14 + 1, sy = 1 + (x+1)*7 + (1-y)*16
            x = (sx - 16) / 14 + 1
            if 0 <= x < 1:
                y = 1 - (sy - 1 - (x + 1) * 7) / 16
                if 0 <= y < 1:
                    col = sample(1, x, 1 - y)
                    if col[3] > 0:
                        po[px, py] = col
                    continue
            # 동쪽 면 (x=1, 오른쪽 아래): z = 1 - (sx-16)/14, sy = 1 + (1+z)*7 + (1-y)*16
            z = 1 - (sx - 16) / 14
            if 0 <= z < 1:
                y = 1 - (sy - 1 - (1 + z) * 7) / 16
                if 0 <= y < 1:
                    col = sample(2, 1 - z, 1 - y)
                    if col[3] > 0:
                        po[px, py] = col
    return out


def unknown_icon():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    p = im.load()
    for x in range(8, 24):
        for y in range(8, 24):
            p[x, y] = (90, 80, 70, 255) if x in (8, 23) or y in (8, 23) else (40, 36, 32, 255)
    for (x, y) in [(13, 12), (14, 11), (15, 11), (16, 11), (17, 11), (18, 12), (18, 13), (17, 14), (16, 15), (15, 16), (15, 17), (15, 20)]:
        p[x, y] = (230, 220, 180, 255)
    return im


def render_icon(jar, item_id):
    """돌려주는 값: (32x32 이미지, 방법 설명)"""
    tint = TINT.get(item_id)
    item_def = jar.json(f"assets/minecraft/items/{item_id}.json")
    model_ref = first_model_ref(item_def.get("model")) if item_def else None
    if model_ref is None:
        model_ref = f"minecraft:item/{item_id}"

    chain, textures = model_chain(jar, model_ref)

    # 1) 꽉 찬 블록 → 입체
    if "block/cube" in chain:
        top = resolve(textures, "up") or resolve(textures, "top") or resolve(textures, "end") or resolve(textures, "all")
        side = resolve(textures, "south") or resolve(textures, "side") or resolve(textures, "all") or resolve(textures, "front")
        east = resolve(textures, "east") or resolve(textures, "side") or resolve(textures, "all") or side
        top = top or resolve(textures, "particle")
        side = side or top
        imgs = [jar.image(tex_path(t)) if t else None for t in (top, side, east)]
        if all(imgs):
            imgs = [tinted(i, tint) if item_id.endswith("leaves") else i for i in imgs]
            if item_id == "grass_block":
                imgs[0] = tinted(imgs[0], tint)
            return iso_cube(*imgs), "블록(입체)"

    # 2) 평면 아이템 (item/generated, item/handheld 등)
    layer0 = resolve(textures, "layer0")
    if layer0:
        im = jar.image(tex_path(layer0))
        if im is not None:
            return flat_icon(tinted(im, tint)), "아이템"

    # 3) 기타: 아이템 텍스처 → 블록 텍스처 → particle 순서로 찾기
    for p in (f"assets/minecraft/textures/item/{item_id}.png",
              f"assets/minecraft/textures/block/{item_id}.png",
              tex_path(resolve(textures, "particle")) if resolve(textures, "particle") else None):
        if p and jar.has(p):
            return flat_icon(tinted(jar.image(p), tint)), "평면(대체)"

    return unknown_icon(), "못 찾음(?)"


# ======================================================================
#  dogam.yml
# ======================================================================
def load_entries(path):
    """플러그인 CollectionRegistry 와 같은 순서·같은 번호"""
    with open(path, encoding="utf-8") as f:
        data = yaml.safe_load(f) or {}
    out = []
    for cat_id, cat in (data.get("categories") or {}).items():
        for entry_id, e in ((cat or {}).get("entries") or {}).items():
            mat = str((e or {}).get("material", "")).lower().replace("minecraft:", "")
            out.append((len(out), f"{cat_id}:{entry_id}", mat))
    return out


# ======================================================================
#  팩 만들기
# ======================================================================
def bitmap(file, ascent, height, chars):
    return {"type": "bitmap", "file": f"servercore:font/{file}", "ascent": ascent, "height": height, "chars": chars}


def font_json(entries):
    neg = {chr(0xF801 + i): -(1 << i) for i in range(8)}
    pos = {chr(0xF811 + i): (1 << i) for i in range(8)}
    providers = [
        {"type": "space", "advances": {**neg, **pos}},
        bitmap("check.png", 11, 16, ["\ue001"]),
        bitmap("bar_track.png", 8, 9, ["\ue002"]),
        bitmap("bar_fill.png", 7, 7, ["\ue003"]),
        bitmap("arrow_left.png", 11, 16, ["\ue004"]),
        bitmap("arrow_right.png", 11, 16, ["\ue005"]),
        bitmap("arrow_left_off.png", 11, 16, ["\ue006"]),
        bitmap("arrow_right_off.png", 11, 16, ["\ue007"]),
        bitmap("digits.png", 2, 7, ["".join(chr(0xE010 + d) for d in range(10))]),
    ]
    for index, _id, _mat in entries:
        providers.append(bitmap(f"icons/{index}.png", ICON_ASCENT, ICON_HEIGHT, [chr(ICON_BASE + index)]))
    return {"providers": providers}


def main():
    ap = argparse.ArgumentParser(description="ServerCore 리소스팩 만들기")
    ap.add_argument("--jar", required=True, help="마인크래프트 클라이언트 jar (.minecraft/versions/26.3/26.3.jar)")
    ap.add_argument("--dogam", required=True, help="dogam.yml 경로 (서버에서 쓰는 것과 같은 파일)")
    ap.add_argument("--out", default=os.path.join(HERE, "build"), help="결과 폴더 (기본: tools/build)")
    ap.add_argument("--pack-format", type=int, default=None, help="pack format 번호 (안 주면 jar에서 읽음)")
    args = ap.parse_args()

    jar = Jar(args.jar)
    entries = load_entries(args.dogam)
    pack_dir = os.path.join(args.out, "servercore_pack")
    if os.path.exists(pack_dir):
        shutil.rmtree(pack_dir)
    shutil.copytree(TEMPLATE, pack_dir)

    # 아이콘
    icon_dir = os.path.join(pack_dir, "assets", "servercore", "textures", "font", "icons")
    os.makedirs(icon_dir, exist_ok=True)
    stats = {}
    for index, entry_id, mat in entries:
        im, how = render_icon(jar, mat)
        im.save(os.path.join(icon_dir, f"{index}.png"))
        stats[how] = stats.get(how, 0) + 1
        if how == "못 찾음(?)":
            print(f"  ! {entry_id} ({mat}) 아이콘을 못 찾아서 ? 로 넣었어요")

    # 폰트
    font_dir = os.path.join(pack_dir, "assets", "servercore", "font")
    os.makedirs(font_dir, exist_ok=True)
    with open(os.path.join(font_dir, "gui.json"), "w", encoding="utf-8") as f:
        json.dump(font_json(entries), f, ensure_ascii=True, indent=1)

    # pack.mcmeta
    fmt = args.pack_format or jar.version_pack_format()
    if fmt is None:
        sys.exit("jar에서 pack format 번호를 못 찾았어요. --pack-format 숫자 로 직접 넣어주세요.")
    meta = {"pack": {"description": "ServerCore 리소스팩", "min_format": fmt, "max_format": fmt}}
    with open(os.path.join(pack_dir, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)

    # zip (pack.mcmeta 가 맨 위에 오게)
    zip_path = os.path.join(args.out, "servercore_pack.zip")
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for root, _dirs, files in os.walk(pack_dir):
            for fn in sorted(files):
                full = os.path.join(root, fn)
                z.write(full, os.path.relpath(full, pack_dir).replace(os.sep, "/"))
    sha1 = hashlib.sha1(open(zip_path, "rb").read()).hexdigest()

    print()
    print(f"항목 {len(entries)}개 아이콘: " + ", ".join(f"{k} {v}" for k, v in stats.items()))
    print(f"pack format: {fmt}")
    print(f"완성: {zip_path}")
    print()
    print("server.properties 에 넣을 값:")
    print(f"  resource-pack=<zip을 올린 다운로드 주소>")
    print(f"  resource-pack-sha1={sha1}")


if __name__ == "__main__":
    main()
