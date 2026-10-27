"""
ServerCore 리소스팩 만들기 (3.0 - 상자 GUI + 스타듀밸리 배경)

  python build_pack.py --jar "%APPDATA%\\.minecraft\\versions\\26.3\\26.3.jar" --dogam "서버\\plugins\\ServerCore\\dogam.yml"

하는 일
  1. tools/art 의 그림(배경, 체크, 화살표, 막대, 숫자)을 넣고
  2. 폰트(servercore:gui) — 메뉴·도감 배경, 진행도 막대, 페이지 숫자
  3. 아이템 모델 — 화살표, 투명 버튼, 그리고 도감 항목마다 "원래 아이템 + 체크" 모델
     (마인크래프트 jar 안의 원래 아이템 정의를 그대로 감싸서 만들어요)
  4. pack.mcmeta (버전 번호는 jar에서 읽음), zip, SHA-1

⚠ 글자 번호·모델 이름은 플러그인 Glyphs.java 와 약속이에요.
필요: Python 3.10+, pip install pyyaml pillow
"""
import argparse
import hashlib
import json
import os
import shutil
import sys
import zipfile

try:
    import yaml
    from PIL import Image
except ImportError:
    sys.exit("먼저 설치하세요:  pip install pyyaml pillow")

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.join(HERE, "art")

# GUI 제목은 y=6에 쓰이고 글자 기준선은 y=13 → 그림 윗변을 y=T에 두려면 ascent = 13 - T
def ascent_for_top(top):
    return 13 - top


# 배경 그림 위쪽 여백 (창 위로 튀어나온 간판·꽃) — make_art.py 의 TOP 과 같아야 해요
TOP = 20

# 스킬 창: 줄마다 칸 윗변 y (⚠ SkillGui.java, make_art.py 의 draw_skill 과 약속)
#   글자 윗변 = 칸 윗변 + 2  (servercore:skill1~5),  게이지 안쪽 윗변 = 칸 윗변 + 11
SKILL_ROW_TOP = [17 + 18 * r for r in range(5)]

# 상점 배경 테마 (⚠ Glyphs.java 의 SHOP_THEMES, make_art.py 의 SHOP_THEMES 와 같은 순서)
SHOP_THEMES = ["fish", "crops", "ores"]


# 마인크래프트는 폰트 그림 한 장이 256x256 픽셀을 넘으면 그리지 못해요 (네모 □로 나옴).
# 그래서 2배 해상도 배경을 2x2 조각으로 잘라서 글자 4개로 넣어요: 왼위, 오른위, 왼아래, 오른아래
def split_bg(name, out_dir):
    im = Image.open(os.path.join(ART, name + ".png")).convert("RGBA")
    w, h = im.size
    hw, hh = w // 2, h // 2
    for i, (x, y) in enumerate([(0, 0), (hw, 0), (0, hh), (hw, hh)]):
        tile = im.crop((x, y, x + hw, y + hh))
        if max(tile.size) > 256:
            sys.exit(f"{name} 조각이 256px보다 커요 ({tile.size}). make_art.py 크기를 확인하세요.")
        tile.save(os.path.join(out_dir, f"{name}_{i}.png"))


def bg_tiles(name, gui_h, first):
    """GUI 높이 gui_h 짜리 배경을 위/아래 반씩 (윗변은 y = -TOP)"""
    half = gui_h // 2
    out = []
    for i in range(4):
        top = -TOP + (half if i >= 2 else 0)
        out.append({"type": "bitmap", "file": f"servercore:font/{name}_{i}.png",
                    "ascent": ascent_for_top(top), "height": half, "chars": [chr(first + i)]})
    return out


def font_json():
    neg = {chr(0xF801 + i): -(1 << i) for i in range(8)}
    pos = {chr(0xF811 + i): (1 << i) for i in range(8)}
    bm = lambda f, a, h, chars: {"type": "bitmap", "file": f"servercore:font/{f}", "ascent": a, "height": h, "chars": chars}
    return {"providers": [
        {"type": "space", "advances": {**neg, **pos}},
        *bg_tiles("dogam_bg", 222 + TOP, 0xE020),   # 도감 배경 4조각 \uE020~\uE023
        *bg_tiles("menu_bg", 204 + TOP, 0xE024),    # 메뉴 배경 4조각 \uE024~\uE027
        *bg_tiles("skill_bg", 222 + TOP, 0xE028),   # 스킬 창 배경 4조각 \uE028~\uE02B
        *bg_tiles("land_bg", 222 + TOP, 0xE02C),    # 집 지도 창 배경 4조각 \uE02C~\uE02F
        *bg_tiles("members_bg", 222 + TOP, 0xE03C), # 이웃 허가 창 배경 4조각 \uE03C~\uE03F
        # 스킬 창 게이지 조각: 줄마다 높이가 달라서 5개 \uE060~\uE064
        *[bm("bar_full.png", ascent_for_top(t + 11), 5, [chr(0xE060 + i)]) for i, t in enumerate(SKILL_ROW_TOP)],
        # 상점 배경 4조각씩 \uE030~ (SHOP_THEMES 순서)
        *[t for i, theme in enumerate(SHOP_THEMES) for t in bg_tiles(f"shop_{theme}_bg", 222 + TOP, 0xE030 + 4 * i)],
        bm("bar_full.png", ascent_for_top(114), 5, ["\uE002"]),         # 게이지 안쪽 y 114~118 (맨 아래 줄)
        bm("digits.png", ascent_for_top(113), 7, ["".join(chr(0xE010 + i) for i in range(13))]),  # 0~9 / , G
        *hud_glyphs(bm),
    ]}


# ---------------- HUD (보스바 제목 자리) ⚠ HudFeature.java 와 약속 ----------------
# 보스바 제목 글자의 기준선은 화면 y=10 (제목 윗변 y=3 + 기본 글꼴 ascent 7)
#   → 윗변을 y=T 에 두려면 ascent = 10 - T
HUD_SKILLS = ["farming", "mining", "woodcutting", "fishing", "combat"]   # Skill.java 순서
HUD_LINE_TOP = [3, 13, 22, 31]                                         # 1~4번째 줄 윗변


def hud_ascent(top):
    return 10 - top


def hud_glyphs(bm):
    g = [
        bm("hud_panel.png", hud_ascent(1), 40, ["\uE040"]),            # 반투명 상자 88x40, 윗변 y=1
        bm("hud_coin.png", hud_ascent(HUD_LINE_TOP[1]), 8, ["\uE041"]),  # 2번째 줄 금화
    ]
    for i, k in enumerate(HUD_SKILLS):
        g.append(bm(f"hud_{k}.png", hud_ascent(HUD_LINE_TOP[2]), 8, [chr(0xE042 + i)]))   # 3번째 줄용
        g.append(bm(f"hud_{k}.png", hud_ascent(HUD_LINE_TOP[3]), 8, [chr(0xE047 + i)]))   # 4번째 줄용
        g.append(bm(f"hud_label_{k}.png", hud_ascent(HUD_LINE_TOP[0]), 8, [chr(0xE050 + i)]))  # "농사 레벨"
    return g


def hud_line_fonts(jar):
    """servercore:hud1~4 — 마인크래프트 기본 글자(ascii.png)를 줄마다 아래로 내려 그리는 폰트"""
    base = jar.json("assets/minecraft/font/include/default.json") or {}
    ascii_p = next((p for p in base.get("providers", []) if p.get("file") == "minecraft:font/ascii.png"), None)
    if ascii_p is None:
        print("  ! jar에서 기본 글꼴(ascii) 설정을 못 찾았어요. HUD 글자가 안 보일 수 있어요")
        return {}
    fonts = {}
    for n, top in enumerate(HUD_LINE_TOP, start=1):
        p = dict(ascii_p)
        p["ascent"] = 7 - (top - HUD_LINE_TOP[0])
        fonts[f"hud{n}"] = {"providers": [{"type": "space", "advances": {" ": 4}}, p]}
    return fonts


def skill_row_fonts(jar):
    """servercore:skill1~5 — 상자 제목 자리에서 기본 글자를 스킬 창 줄마다 내려 그리는 폰트"""
    base = jar.json("assets/minecraft/font/include/default.json") or {}
    ascii_p = next((p for p in base.get("providers", []) if p.get("file") == "minecraft:font/ascii.png"), None)
    if ascii_p is None:
        print("  ! jar에서 기본 글꼴(ascii) 설정을 못 찾았어요. 스킬 창 글자가 안 보일 수 있어요")
        return {}
    fonts = {}
    for n, top in enumerate(SKILL_ROW_TOP, start=1):
        p = dict(ascii_p)
        p["ascent"] = ascent_for_top(top + 2)
        fonts[f"skill{n}"] = {"providers": [{"type": "space", "advances": {" ": 4}}, p]}
    return fonts


def house_model():
    """집터 모형: 돌 바닥 + 나무 벽 + 계단식 빨간 지붕 + 굴뚝, 문·창문은 살짝 튀어나온 얇은 판"""
    def box(f, t, tex, faces=("north", "south", "east", "west", "up", "down")):
        return {"from": f, "to": t, "faces": {k: {"texture": "#" + (tex[k] if isinstance(tex, dict) else tex)} for k in faces}}
    els = [
        box([1, 0, 1], [15, 1, 15], "base"),
        box([2, 1, 2], [14, 9, 14], "wall"),
        # 문 (앞 = +z = south)
        box([6, 1, 14], [10, 7, 14.5], "door", ("south", "east", "west", "up")),
        # 창문: 앞 양옆, 양쪽 옆면
        box([3, 4, 14], [5.5, 6.5, 14.25], "window", ("south",)),
        box([10.5, 4, 14], [13, 6.5, 14.25], "window", ("south",)),
        box([14, 4, 6], [14.25, 7, 10], "window", ("east",)),
        box([1.75, 4, 6], [2, 7, 10], "window", ("west",)),
        box([6, 4, 1.75], [10, 7, 2], "window", ("north",)),
    ]
    for i in range(6):                         # 계단식 지붕
        lo, hi = 1 + i, 15 - i
        els.append(box([lo, 9 + i, lo], [hi, 10 + i, hi], "roof"))
    els.append(box([10, 11, 4], [12, 16, 6], "chimney"))
    return {
        "textures": {k: f"servercore:block/house_{k}" for k in ("wall", "roof", "door", "window", "base", "chimney")}
                    | {"particle": "servercore:block/house_wall"},
        "elements": els,
        "display": {   # 손·가방에서는 문이 보이게 (기본 블록 각도에서 180도 돌림)
            "gui": {"rotation": [30, 45, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
            "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        },
    }


# ---------------- 마을 도구 (강화 단계별 3D 모델) ⚠ 플러그인과 약속 ----------------
# 모델 자리: tools/models/<도구>_<단계>.json, 텍스처: tools/models/textures/*.png (자세한 건 tools/models/README.md)
#   플러그인: setItemModel("servercore:<도구>") + custom_model_data floats[0] = 강화 단계
#   없는 단계는 그 아래 단계 모양, +0 도 없으면 바닐라 모양
MODELS = os.path.join(HERE, "models")
VILLAGE_TOOLS = {"village_pickaxe": "diamond_pickaxe", "village_axe": "diamond_axe", "village_hoe": "diamond_hoe",
                 "village_watering_can": "carrot_on_a_stick"}
TOOL_TIERS = [0, 5, 10, 15, 20]


def village_tool_def(jar, tool, vanilla):
    """도구 하나의 아이템 정의 + 모델이 있는 단계 목록"""
    have = [t for t in TOOL_TIERS if os.path.exists(os.path.join(MODELS, f"{tool}_{t}.json"))]
    model = lambda t: {"type": "minecraft:model", "model": f"servercore:item/{tool}_{t}"}
    v = jar.json(f"assets/minecraft/items/{vanilla}.json")
    fallback = v["model"] if v and "model" in v else {"type": "minecraft:model", "model": f"minecraft:item/{vanilla}"}
    if 0 in have:
        fallback = model(0)
    entries = [{"threshold": t, "model": model(t)} for t in have if t > 0]
    if not entries:
        return item_def(fallback), have
    return item_def({"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
                     "fallback": fallback, "entries": entries}), have


def generated(texture, extra=None):
    m = {"parent": "minecraft:item/generated", "textures": {"layer0": texture}}
    if extra:
        m.update(extra)
    return m


def item_def(model):
    return {"model": model}


class Jar:
    def __init__(self, path):
        self.z = zipfile.ZipFile(path)
        self.names = set(self.z.namelist())

    def json(self, p):
        return json.loads(self.z.read(p).decode("utf-8")) if p in self.names else None

    def pack_format(self):
        v = self.json("version.json") or {}
        pv = v.get("pack_version")
        if isinstance(pv, int):
            return pv
        if isinstance(pv, dict):
            for k in ("resource_major", "resource"):
                if isinstance(pv.get(k), int):
                    return pv[k]
        return None


def load_materials(path):
    with open(path, encoding="utf-8") as f:
        data = yaml.safe_load(f) or {}
    mats = []
    for cat in (data.get("categories") or {}).values():
        for e in ((cat or {}).get("entries") or {}).values():
            m = str((e or {}).get("material", "")).lower().replace("minecraft:", "")
            if m and m not in mats:
                mats.append(m)
    return mats


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=True, indent=1)


def main():
    ap = argparse.ArgumentParser(description="ServerCore 리소스팩 만들기")
    ap.add_argument("--jar", required=True, help="마인크래프트 클라이언트 jar (.minecraft/versions/26.3/26.3.jar)")
    ap.add_argument("--dogam", required=True, help="서버에서 쓰는 dogam.yml 경로")
    ap.add_argument("--out", default=os.path.join(HERE, "build"), help="결과 폴더 (기본: tools/build)")
    ap.add_argument("--keep-slot-highlight", action="store_true",
                    help="마우스를 올린 칸이 하얗게 되는 효과를 그대로 둠 (기본: 없앰)")
    ap.add_argument("--pack-format", type=int, default=None, help="pack format 번호 (안 주면 jar에서 읽음)")
    args = ap.parse_args()

    if not os.path.isdir(ART):
        sys.exit("tools/art 폴더가 없어요. 먼저  python make_art.py  를 실행하세요.")

    jar = Jar(args.jar)
    mats = load_materials(args.dogam)
    pack = os.path.join(args.out, "servercore_pack")
    if os.path.exists(pack):
        shutil.rmtree(pack)
    sc = os.path.join(pack, "assets", "servercore")

    # 1) 그림
    font_tex = os.path.join(sc, "textures", "font")
    item_tex = os.path.join(sc, "textures", "item")
    os.makedirs(font_tex, exist_ok=True)
    os.makedirs(item_tex, exist_ok=True)
    hud_art = ["hud_panel.png", "hud_coin.png"] + [f"hud_{k}.png" for k in HUD_SKILLS] \
        + [f"hud_label_{k}.png" for k in HUD_SKILLS]
    for name in ["bar_full.png", "digits.png"] + hud_art:
        shutil.copy(os.path.join(ART, name), font_tex)
    split_bg("dogam_bg", font_tex)
    split_bg("menu_bg", font_tex)
    split_bg("skill_bg", font_tex)
    split_bg("land_bg", font_tex)
    split_bg("members_bg", font_tex)
    for theme in SHOP_THEMES:
        split_bg(f"shop_{theme}_bg", font_tex)
    arrows = ("arrow_left", "arrow_right", "arrow_left_off", "arrow_right_off", "land_plus", "land_plus_off")
    for name in ("check", "gold_coin") + arrows:
        shutil.copy(os.path.join(ART, name + ".png"), item_tex)

    # 2) 폰트
    write_json(os.path.join(sc, "font", "gui.json"), font_json())
    for name, data in {**hud_line_fonts(jar), **skill_row_fonts(jar)}.items():
        write_json(os.path.join(sc, "font", name + ".json"), data)

    # 3) 아이템 모델
    #    체크: 블록 아이템(입체) 앞에 가려지지 않게 GUI에서 앞으로 당김
    write_json(os.path.join(sc, "models", "item", "check.json"),
               generated("servercore:item/check", {"display": {"gui": {"translation": [0, 0, 32]}}}))
    for a in arrows:
        write_json(os.path.join(sc, "models", "item", a + ".json"), generated(f"servercore:item/{a}"))
        write_json(os.path.join(sc, "items", a + ".json"),
                   item_def({"type": "minecraft:model", "model": f"servercore:item/{a}"}))
    write_json(os.path.join(sc, "items", "invisible.json"), item_def({"type": "minecraft:empty"}))
    # 골드 코인 (골드 주머니, 상점의 골드 칸)
    write_json(os.path.join(sc, "models", "item", "gold_coin.json"), generated("servercore:item/gold_coin"))
    write_json(os.path.join(sc, "items", "gold_coin.json"),
               item_def({"type": "minecraft:model", "model": "servercore:item/gold_coin"}))

    # 메뉴의 내 머리: 비스듬한 입체 대신 얼굴 정면이 보이게 (칸에 꽉 차게 1.75배)
    write_json(os.path.join(sc, "models", "item", "player_head_front.json"), {
        "textures": {"particle": "minecraft:block/soul_sand"},
        "gui_light": "front",
        "display": {"gui": {"rotation": [0, 0, 0], "translation": [0, 7, 0], "scale": [1.75, 1.75, 1.75]}},
    })
    vhead = jar.json("assets/minecraft/items/player_head.json")
    if vhead and "model" in vhead:
        m = dict(vhead["model"])
        m["base"] = "servercore:item/player_head_front"
        write_json(os.path.join(sc, "items", "player_head_front.json"), {"model": m})
    else:
        print("  ! jar에서 플레이어 머리 정의를 못 찾았어요. 메뉴 얼굴이 원래 모양으로 보여요")
    # 집터 모형 (작은 집): 문이 +z(남쪽) 면. 플러그인이 아이템 디스플레이로 놓을 때 돌려요
    #   ⚠ 입체 모델(elements)의 텍스처는 블록 그림 모음(atlas)에 있어야 해요 → textures/block 에 넣어요
    #     (textures/item 에 넣으면 26.x 에서 보라·검정 체크무늬로 나와요)
    block_tex = os.path.join(sc, "textures", "block")
    os.makedirs(block_tex, exist_ok=True)
    for n in ("house_wall", "house_roof", "house_door", "house_window", "house_base", "house_chimney"):
        shutil.copy(os.path.join(ART, n + ".png"), block_tex)
    write_json(os.path.join(sc, "models", "item", "house_core.json"), house_model())
    write_json(os.path.join(sc, "items", "house_core.json"),
               item_def({"type": "minecraft:model", "model": "servercore:item/house_core"}))

    # 마을 도구: tools/models 의 모델·텍스처를 넣고, 강화 단계로 모양을 고르는 정의
    #   텍스처는 집터 모형처럼 textures/block 에 (입체 모델이라서)
    tex_src = os.path.join(MODELS, "textures")
    if os.path.isdir(tex_src):
        for fn in os.listdir(tex_src):
            if fn.endswith((".png", ".png.mcmeta")):
                shutil.copy(os.path.join(tex_src, fn), block_tex)
    for tool, vanilla in VILLAGE_TOOLS.items():
        d, have = village_tool_def(jar, tool, vanilla)
        for t in have:
            shutil.copy(os.path.join(MODELS, f"{tool}_{t}.json"), os.path.join(sc, "models", "item", f"{tool}_{t}.json"))
        write_json(os.path.join(sc, "items", tool + ".json"), d)
        print(f"  {tool}: " + (", ".join(f"+{t}" for t in have) + " 모델" if have else "모델 없음 → 바닐라 모양"))

    # 이웃 허가: 플레이어 머리 + 체크
    if vhead and "model" in vhead:
        write_json(os.path.join(sc, "items", "checked_head.json"), item_def({
            "type": "minecraft:composite",
            "models": [vhead["model"], {"type": "minecraft:model", "model": "servercore:item/check"}],
        }))

    ok, missing = 0, []
    for m in mats:
        vanilla = jar.json(f"assets/minecraft/items/{m}.json")
        if not vanilla or "model" not in vanilla:
            missing.append(m)
            continue
        write_json(os.path.join(sc, "items", "checked", m + ".json"), item_def({
            "type": "minecraft:composite",
            "models": [vanilla["model"], {"type": "minecraft:model", "model": "servercore:item/check"}],
        }))
        ok += 1

    # 3-2) 마우스를 올린 칸의 흰 강조 효과 없애기 (투명 그림으로 덮어씀)
    #      ⚠ 마인크래프트 화면 전체(내 인벤토리, 상자 등)에 똑같이 적용돼요
    if not args.keep_slot_highlight:
        hl = [n for n in jar.names
              if n.startswith("assets/minecraft/textures/gui/sprites/container/slot_highlight") and n.endswith(".png")]
        if not hl:
            hl = ["assets/minecraft/textures/gui/sprites/container/slot_highlight_back.png",
                  "assets/minecraft/textures/gui/sprites/container/slot_highlight_front.png"]
            print("  ! jar에서 칸 강조 그림을 못 찾아서 기본 이름으로 덮어써요")
        for n in hl:
            size = (24, 24)
            if n in jar.names:
                import io
                size = Image.open(io.BytesIO(jar.z.read(n))).size
            out = os.path.join(pack, *n.split("/"))
            os.makedirs(os.path.dirname(out), exist_ok=True)
            Image.new("RGBA", size, (0, 0, 0, 0)).save(out)
            print(f"  칸 강조 효과 없앰: {n.rsplit('/', 1)[-1]} {size}")

    # 3-3) 하얀 보스바 막대를 투명하게 (HUD가 하얀 보스바의 제목 자리를 써요)
    #       ⚠ 다른 플러그인이 하얀 보스바를 쓰면 그 막대도 안 보여요 (제목 글자는 보임)
    for n in ("white_background", "white_progress"):
        src = f"assets/minecraft/textures/gui/sprites/boss_bar/{n}.png"
        size = (182, 5)
        if src in jar.names:
            import io
            size = Image.open(io.BytesIO(jar.z.read(src))).size
        out = os.path.join(pack, *src.split("/"))
        os.makedirs(os.path.dirname(out), exist_ok=True)
        Image.new("RGBA", size, (0, 0, 0, 0)).save(out)

    # 4) pack.mcmeta
    fmt = args.pack_format or jar.pack_format()
    if fmt is None:
        sys.exit("jar에서 pack format 번호를 못 찾았어요. --pack-format 숫자 로 직접 넣어주세요.")
    with open(os.path.join(pack, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"pack": {"description": "ServerCore - 스타듀밸리 메뉴·도감", "min_format": fmt, "max_format": fmt}},
                  f, ensure_ascii=False, indent=2)

    zip_path = os.path.join(args.out, "servercore_pack.zip")
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for root, _dirs, files in os.walk(pack):
            for fn in sorted(files):
                full = os.path.join(root, fn)
                z.write(full, os.path.relpath(full, pack).replace(os.sep, "/"))
    sha1 = hashlib.sha1(open(zip_path, "rb").read()).hexdigest()

    print()
    for m in missing:
        print(f"  ! {m}: 마인크래프트에 없는 아이템이라 체크 모델을 못 만들었어요 (dogam.yml에서 고치거나 지우세요)")
    print(f"체크 모델 {ok}개 / 도감 아이템 {len(mats)}개")
    print(f"pack format: {fmt}")
    print(f"완성: {zip_path}")
    print()
    print("server.properties 에 넣을 값:")
    print("  resource-pack=<zip을 올린 다운로드 주소>")
    print(f"  resource-pack-sha1={sha1}")


if __name__ == "__main__":
    main()
