"""
ServerCore 그림 만들기 (밝고 귀여운 스타듀밸리 느낌)  →  tools/art/
  python make_art.py

배경은 GUI 좌표(1배) 기준으로 설계하고 2배 해상도로 그려요 (게임에서 더 선명).
창 위로 튀어나온 간판·꽃을 그리려고 그림 위쪽에 TOP(=20)px 여백이 있어요.
  - dogam_bg.png  176 x (20+222)  : 칠판 간판 + 수집품 판(9x5) + 아래 조작 줄 + 가방
  - menu_bg.png   176 x (20+204)  : 칠판 간판 + 일러스트 큰 버튼 4개 + 가방
  - skill_bg.png  176 x (20+222)  : 칠판 간판 + 분야별 카드 5줄 + 가방
  - land_bg.png   176 x (20+222)  : 칠판 간판 + 청크 지도(풀밭 7x5) + 버튼 줄 + 가방
  - check.png, arrow_*.png        : 아이템 모델용 그림
  - bar_full.png, digits.png      : 진행도 게이지 조각, 페이지 숫자(갈색)

⚠ 칸·게이지·숫자 위치와 TOP 여백은 플러그인(DogamGui.java)·build_pack.py 와 약속이에요.
필요: pip install pillow  (한글 글씨는 아래 FONT_PATHS 중 있는 글꼴, 메뉴 이름표는 tools/fonts/Galmuri11-Bold.ttf)
"""
import os
import random
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "art")
os.makedirs(OUT, exist_ok=True)
S = 2      # 해상도 배율
TOP = 20   # 창 위쪽으로 튀어나오는 장식 공간 (GUI px) — build_pack.py 의 TOP 과 같아야 해요

FONT_PATHS = [
    ("C:/Windows/Fonts/malgunbd.ttf", 0),
    ("/usr/share/fonts/opentype/noto/NotoSansCJK-Black.ttc", 1),
    ("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", 1),
    ("/System/Library/Fonts/AppleSDGothicNeo.ttc", 0),
]


def font(px):
    for path, idx in FONT_PATHS:
        if os.path.exists(path):
            return ImageFont.truetype(path, int(px * S), index=idx)
    return ImageFont.load_default()


def c(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


CLEAR = (0, 0, 0, 0)
# ---------------- 밝은 팔레트 (외곽선은 검정 대신 따뜻한 갈색) ----------------
LINE = c("#6b4226")
WOOD, WOOD_LT, WOOD_HL, WOOD_DK = c("#e0a35c"), c("#f0c07e"), c("#ffe0a6"), c("#c47f3e")
BOARD, BOARD_DK = c("#4f7d57"), c("#3c6344")
PANEL = c("#e8c088")  # 도감 판 바탕색 = 아래 조작 줄과 같은 색 — 칸 안쪽도 같은 색
SLOT_LINE, SLOT_FILL, SLOT_SHADE = c("#b07a42"), PANEL, PANEL
INV_BG, INV_SLOT_LINE, INV_SLOT, INV_SLOT_SH = c("#f2cf93"), c("#cf9a58"), c("#fbe6bf"), c("#efd2a0")
WHITE = c("#ffffff")


class Canvas:
    """GUI 좌표로 그리면 (TOP 여백 + S배)로 칠해주는 도우미"""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.im = Image.new("RGBA", (w * S, (h + TOP) * S), CLEAR)
        self.d = ImageDraw.Draw(self.im)

    def _y(self, y):
        return (y + TOP) * S

    def rect(self, x0, y0, x1, y1, col):
        if x1 > x0 and y1 > y0:
            self.d.rectangle([int(x0 * S), int(self._y(y0)), int(x1 * S) - 1, int(self._y(y1)) - 1], fill=col)

    def px(self, x, y, col):
        self.rect(x, y, x + 1, y + 1, col)

    def box(self, x0, y0, x1, y1, fill, line=LINE, round_=True):
        """외곽선 1px + 채움, 모서리 둥글게 (모서리는 원래 밑에 있던 색으로 되돌려서 구멍이 안 나게)"""
        corners = [(x0, y0), (x1 - 1, y0), (x0, y1 - 1), (x1 - 1, y1 - 1)]
        under = []
        for (x, y) in corners:
            X, Y = int(x * S), int(self._y(y))
            ok = 0 <= X < self.im.width and 0 <= Y < self.im.height
            under.append(self.im.getpixel((X, Y)) if ok else CLEAR)
        self.rect(x0, y0, x1, y1, line)
        self.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill)
        if round_:
            for (x, y), col in zip(corners, under):
                self.px(x, y, col)

    def bevel(self, x0, y0, x1, y1, hi, lo):
        self.rect(x0 + 1, y0 + 1, x1 - 1, y0 + 2, hi)
        self.rect(x0 + 1, y0 + 1, x0 + 2, y1 - 1, hi)
        self.rect(x0 + 1, y1 - 2, x1 - 1, y1 - 1, lo)
        self.rect(x1 - 2, y0 + 1, x1 - 1, y1 - 1, lo)

    def wood_frame(self, x0, y0, x1, y1, t=3):
        """밝은 나무 테두리 (안쪽은 나중에 덮어 그림)"""
        self.box(x0, y0, x1, y1, WOOD)
        self.bevel(x0, y0, x1, y1, WOOD_HL, WOOD_DK)
        rnd = random.Random(x0 * 7 + y0 + x1)
        for _ in range((x1 - x0) // 8):
            x = rnd.randint(x0 + 3, x1 - 8)
            self.rect(x, y0 + 1, x + rnd.randint(2, 5), y0 + 2, WOOD_LT)
        self.rect(x0 + t - 1, y0 + t - 1, x1 - t + 1, y1 - t + 1, WOOD_DK)

    def text(self, x, y, s, size, fill, stroke=None, sw=1.0):
        f = font(size)
        kw = {"stroke_width": max(1, int(sw * S)), "stroke_fill": stroke} if stroke else {}
        self.d.text((x * S, self._y(y)), s, font=f, fill=fill, anchor="mm", **kw)

    def sprite(self, x, y, rows, pal, k=None):
        """한 글자 = k 이미지 픽셀 (기본 S → 1 GUI px, 1이면 반 GUI px)"""
        k = k or S
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch in pal:
                    X = int(x * S) + i * k
                    Y = int(self._y(y)) + j * k
                    self.d.rectangle([X, Y, X + k - 1, Y + k - 1], fill=pal[ch])

    def save(self, name):
        # 반투명 픽셀이 생기면 상자 뒤 세상이 비치니까 0 또는 255로 정리
        a = self.im.getchannel("A").point(lambda v: 255 if v >= 128 else 0)
        self.im.putalpha(a)
        self.im.save(os.path.join(OUT, name))


# ---------------- 장식 스프라이트 ----------------
PAL = {"r": c("#ff6b6b"), "R": c("#d9474f"), "y": c("#ffd34d"), "Y": c("#e8a91e"),
       "p": c("#ff9ec4"), "P": c("#e8679a"), "w": WHITE, "b": c("#8fc8ff"),
       "g": c("#7acb52"), "G": c("#4a9a34"), "o": LINE}
TULIP_R = [".r.r.", "rrrrr", "rRrRr", ".rRr.", "..g..", "g.g.g", ".ggg.", "..g.."]
TULIP_P = [".p.p.", "ppppp", "pPpPp", ".pPp.", "..g..", "g.g.g", ".ggg.", "..g.."]
DAISY = [".w.w.", "wwyww", ".www.", "..g..", ".gg..", "..g.."]
SUN = [".y.y.", "yyYyy", ".yyy.", "..g..", "..gg.", "..g.."]
BUSH = ["...GGGGG...", ".GGgggggGG.", "GgggggggggG", "GggggggggGG", ".GGGGGGGGG."]


def flowers(cv, x, y, mirror=False):
    """창 모서리 위에 덤불 + 꽃 (y = 덤불이 창 윗변에 걸치는 높이)"""
    items = [(0, -6, TULIP_R), (5, -8, TULIP_P), (9, -5, DAISY)] if not mirror else \
            [(1, -5, DAISY), (5, -8, TULIP_R), (9, -6, SUN)]
    k = 1.5  # 1.5배 크게
    for dx, dy, spr in items:
        cv.sprite(x + dx * k, y + dy * k, spr, PAL, k=int(S * k))
    cv.sprite(x, y, BUSH, PAL, k=int(S * k))


def sparkle(cv, x, y, col=WHITE, k=1):
    for (dx, dy) in [(1, 0), (0, 1), (1, 1), (2, 1), (1, 2)]:
        cv.rect(x + dx * k, y + dy * k, x + dx * k + k, y + dy * k + k, col)


def chalk_sign(cv, cx, y0, w, h, label, board=None, board_dk=None, shine=None, stroke=None):
    """창 위로 튀어나온 칠판 간판 (y0는 음수 = 창보다 위)"""
    board, board_dk = board or BOARD, board_dk or BOARD_DK
    x0, x1 = cx - w // 2, cx + w // 2
    cv.rect(x0 + 8, y0 + h - 1, x0 + 10, 3, LINE)      # 기둥
    cv.rect(x1 - 10, y0 + h - 1, x1 - 8, 3, LINE)
    cv.box(x0, y0, x1, y0 + h, WOOD)
    cv.bevel(x0, y0, x1, y0 + h, WOOD_HL, WOOD_DK)
    cv.rect(x0 + 3, y0 + 3, x1 - 3, y0 + h - 3, LINE)
    cv.rect(x0 + 4, y0 + 4, x1 - 4, y0 + h - 4, board)
    cv.rect(x0 + 4, y0 + h - 5, x1 - 4, y0 + h - 4, board_dk)
    cv.rect(x0 + 6, y0 + 5, x0 + 11, y0 + 6, shine or c("#79a882"))   # 반사
    cv.text(cx, y0 + h / 2, label, 9, c("#fffbe8"), stroke=stroke or c("#2c4a33"), sw=0.5)
    cv.rect(x0 + 6, y0 + h - 7, x0 + 10, y0 + h - 6, c("#ff9ec4"))  # 분필 낙서
    cv.rect(x1 - 11, y0 + h - 7, x1 - 6, y0 + h - 6, c("#f7d36b"))


def inventory(cv, label_y, inv_y, hotbar_y, bottom):
    """아래 가방: 밝은 나무 + 크림색 칸 ('보관함' 글씨는 게임이 (8,label_y)에 써요)"""
    cv.box(0, label_y - 4, cv.w, bottom, INV_BG)
    cv.bevel(0, label_y - 4, cv.w, bottom, c("#ffe7b8"), c("#d9a868"))

    def slot(ix, iy):
        x0, y0 = ix - 1, iy - 1
        cv.rect(x0, y0, x0 + 18, y0 + 18, INV_SLOT_LINE)
        cv.rect(x0 + 1, y0 + 1, x0 + 17, y0 + 17, INV_SLOT)
        cv.rect(x0 + 1, y0 + 1, x0 + 17, y0 + 2, INV_SLOT_SH)
        cv.rect(x0 + 1, y0 + 1, x0 + 2, y0 + 17, INV_SLOT_SH)
    for r in range(3):
        for col in range(9):
            slot(8 + 18 * col, inv_y + 18 * r)
    for col in range(9):
        slot(8 + 18 * col, hotbar_y)


PIXEL_FONT = os.path.join(HERE, "fonts", "Galmuri11-Bold.ttf")   # 갈무리11 굵게 (SIL OFL, 도트 한글 글꼴)


def crisp_label(cv, cx, cy, label, size, fill=WHITE, line=LINE, shadow=None):
    """또렷한 도트 글씨: 도트 글꼴(갈무리)을 원래 픽셀 크기로 → 1px 테두리 + 아래 그림자.
    도트 글꼴이 없으면 보통 글꼴을 크게 그려서 줄여요."""
    from PIL import ImageFilter
    W_, H_ = int(len(label) * 14 + 12), 24
    small = Image.new("L", (W_, H_), 0)
    if os.path.exists(PIXEL_FONT):
        d = ImageDraw.Draw(small)
        d.fontmode = "1"                                  # 번짐 없이
        d.text((W_ // 2, H_ // 2), label, font=ImageFont.truetype(PIXEL_FONT, 12), fill=255, anchor="mm")
    else:
        k = 8
        big = Image.new("L", (W_ * k, H_ * k), 0)
        ImageDraw.Draw(big).text((W_ * k / 2, H_ * k / 2), label, font=font(size * k), fill=255, anchor="mm")
        small = big.resize((W_, H_), Image.BOX).point(lambda v: 255 if v >= 110 else 0)
    ring = small.filter(ImageFilter.MaxFilter(3))
    shadow_mask = ring.transform(ring.size, Image.AFFINE, (1, 0, 0, 0, 1, -1))   # 1px 아래로
    ox = int(round(cx * S - W_ / 2))
    oy = int(round(cv._y(cy) - H_ / 2))
    layer = Image.new("RGBA", (W_, H_), CLEAR)
    if shadow:
        layer.paste(Image.new("RGBA", (W_, H_), shadow), (0, 0), shadow_mask)
    layer.paste(Image.new("RGBA", (W_, H_), line), (0, 0), ring)
    layer.paste(Image.new("RGBA", (W_, H_), fill), (0, 0), small)
    cv.im.alpha_composite(layer, (ox, oy))


def round_button(cv, x0, y0, x1, y1, fill, hi, lo):
    cv.box(x0, y0, x1, y1, fill)
    cv.bevel(x0, y0, x1, y1, hi, lo)


# ======================================================================
#  도감 (6줄 상자) — 칸 9x5 + 아래 조작 줄
#    5번 줄: 0열 ◀, 8열 ▶, 게이지 안쪽 x 31~118 / y 114~118, 페이지 숫자 가운데 x 136 / 윗변 y 113
# ======================================================================
def draw_dogam():
    W, H = 176, 222
    cv = Canvas(W, H)
    inventory(cv, label_y=128, inv_y=140, hotbar_y=198, bottom=H)

    cv.wood_frame(0, 0, W, 128)
    # 제목 띠 (게임이 (8,6)에 '도감 진행도 (x/y)'를 씀)
    cv.rect(3, 3, W - 3, 16, c("#fff0cc"))
    cv.rect(3, 15, W - 3, 16, c("#f0d49c"))
    # 수집품 판 (도트 무늬 크림색)
    cv.rect(3, 16, W - 3, 107, PANEL)
    for y in range(18, 106, 6):
        for x in range(5 + (y // 6) % 2 * 3, W - 4, 6):
            cv.px(x, y, c("#e2b87e"))

    for r in range(5):
        for col in range(9):
            x0, y0 = 7 + 18 * col, 17 + 18 * r
            cv.box(x0, y0, x0 + 18, y0 + 18, SLOT_FILL, line=SLOT_LINE)
            cv.rect(x0 + 1, y0 + 1, x0 + 17, y0 + 2, SLOT_SHADE)
            cv.rect(x0 + 1, y0 + 1, x0 + 2, y0 + 17, SLOT_SHADE)

    # 아래 조작 줄 (y 107~125)
    cv.rect(3, 107, W - 3, 125, PANEL)
    cv.rect(3, 107, W - 3, 108, SLOT_LINE)
    for ax in (8, 152):   # ◀ ▶ 둥근 초록 버튼 (15x15, 칸 가운데)
        round_button(cv, ax + 0.5, 108.5, ax + 15.5, 123.5, c("#8fd16a"), c("#c8f0a0"), c("#5fa840"))
    # 게이지·페이지 상자: 둘 다 y 110~123 (안쪽 위아래 여백 똑같이)
    # 게이지 채움은 x 31~118 / y 114~118 (위 3px, 아래 3px), 숫자는 y 113~119 (위 2px, 아래 2px)
    cv.box(27, 110, 123, 123, c("#fffaf0"))
    cv.rect(30, 113, 120, 120, c("#d9bf98"))
    cv.rect(31, 114, 119, 119, c("#f1e2c8"))
    # 페이지 숫자 자리
    cv.box(125, 110, 148, 123, WHITE)

    # 위쪽 장식: 칠판 간판 + 꽃 + 반짝이
    chalk_sign(cv, 88, -19, 56, 17, "도 감")
    flowers(cv, 2, -4)
    flowers(cv, W - 21, -4, mirror=True)
    sparkle(cv, 52, -14, c("#fff3a0"))
    sparkle(cv, 121, -12, WHITE)

    cv.save("dogam_bg.png")
    return cv


# ======================================================================
#  상점 (6줄 상자) — 주민 교환처럼 [아이템] → [골드] 줄이 왼쪽 5개 + 오른쪽 5개
#    상점마다 다른 색·간판·장식
#    5번 줄: 0열 ◀, 8열 ▶ (페이지가 여러 장일 때), 가운데 잔액 상자 x 27~148 (숫자 가운데 x 92 / 윗변 y 113)
#    ⚠ 테마 순서는 build_pack.py 의 SHOP_THEMES, Glyphs.java 의 SHOP_THEMES 와 같아야 해요
# ======================================================================
SHOP_THEMES = {
    "fish": dict(label="생선 가게", panel="#cfe9ef", dot="#b9dde7", line="#6fa3b8",
                 band="#eef9fb", band_line="#bcdfe9", board="#2f6f80", board_dk="#23596a",
                 shine="#5e9cab", stroke="#173f4b"),
    "crops": dict(label="농작물 가게", panel="#e4edc0", dot="#d3e0a3", line="#93ad55",
                  band="#f7fbe2", band_line="#d8e5ad", board=None, board_dk=None, shine=None, stroke=None),
    "ores": dict(label="광물 가게", panel="#ddd8d0", dot="#cdc6bb", line="#8c8276",
                 band="#f5f2ec", band_line="#d7d0c5", board="#4a4552", board_dk="#3a3542",
                 shine="#77707f", stroke="#26222c"),
}

FISH = {"o": LINE, "b": c("#5ab0e0"), "B": c("#3a88c0"), "w": WHITE, "e": c("#2a2a2a")}
FISH_SPR = ["..ooo...", ".obbbo.o", "obwebboo", "obbbbbBo", ".oBBBo.o", "..ooo..."]
WHEAT = {"y": c("#e8c050"), "Y": c("#c89a30"), "g": c("#7acb52"), "o": LINE}
WHEAT_SPR = [".y.y.y.", "yYyYyYy", ".yYyYy.", "..yYy..", "...g...", "..ogo..", "...g...", "...g..."]
CARROT = {"o": c("#ff9a3a"), "O": c("#e07020"), "g": c("#5cc04a")}
CARROT_SPR = [".g.g", "..g.", ".oo.", ".oO.", ".oO.", "..o."]
GEM = {"a": c("#7ae0f0"), "A": c("#3ab0d0"), "w": WHITE, "o": LINE}
GEM_SPR = ["..ooo..", ".oawao.", "oaaaaAo", ".oaaAo.", "..oAo..", "...o..."]
AME = {"a": c("#c99cf0"), "A": c("#9a6ad0"), "w": WHITE, "o": LINE}
PICK = {"t": c("#9a6030"), "i": c("#c9c3d6"), "I": c("#8a8496"), "o": LINE}
PICK_SPR = [".iiiii.", "iIoooIi", "i..t..i", "...t...", "...t...", "...t...", "...t..."]


def theme_deco(cv, theme, W):
    """창 위 양쪽 모서리 장식 (스프라이트 한 칸 = 2px)"""
    if theme == "fish":
        cv.sprite(3, -11, FISH_SPR, FISH, k=4)
        cv.sprite(W - 19, -14, [r[::-1] for r in FISH_SPR], FISH, k=4)
        for (x, y) in [(21, -17), (24, -12), (W - 25, -18)]:
            cv.rect(x, y, x + 2, y + 2, c("#bfe8ff"))
            cv.px(x, y, WHITE)
    elif theme == "crops":
        cv.sprite(2, -15, WHEAT_SPR, WHEAT, k=4)
        cv.sprite(16, -11, CARROT_SPR, CARROT, k=4)
        cv.sprite(W - 16, -15, WHEAT_SPR, WHEAT, k=4)
        cv.sprite(W - 25, -11, CARROT_SPR, CARROT, k=4)
    else:
        cv.sprite(3, -11, GEM_SPR, GEM, k=4)
        cv.sprite(18, -13, PICK_SPR, PICK, k=4)
        cv.sprite(W - 17, -11, GEM_SPR, AME, k=4)
        cv.sprite(W - 32, -13, [r[::-1] for r in PICK_SPR], PICK, k=4)


def draw_shop(theme):
    t = SHOP_THEMES[theme]
    panel, line = c(t["panel"]), c(t["line"])
    W, H = 176, 222
    cv = Canvas(W, H)
    inventory(cv, label_y=128, inv_y=140, hotbar_y=198, bottom=H)
    cv.wood_frame(0, 0, W, 128)
    cv.rect(3, 3, W - 3, 16, c(t["band"]))           # 제목 띠 (게임이 상점 이름을 씀)
    cv.rect(3, 15, W - 3, 16, c(t["band_line"]))
    cv.rect(3, 16, W - 3, 107, panel)
    for y in range(18, 106, 6):
        for x in range(5 + (y // 6) % 2 * 3, W - 4, 6):
            cv.px(x, y, c(t["dot"]))
    # 교환 줄: 왼쪽 [1열 아이템] → [3열 골드], 오른쪽 [5열 아이템] → [7열 골드] × 5줄
    #   ⚠ ShopGui.java 의 ITEM_COL / GOLD_COL 과 같아야 해요
    card = c(t["band"])
    gold_slot = c("#fff4c8")
    arrow_spr = [".....o....",
                 ".....oo...",
                 "oooooowo..",
                 "owwwwwwwo.",
                 "oooooowo..",
                 ".....oo...",
                 ".....o...."]
    arrow_pal = {"o": line, "w": WHITE}
    for r in range(5):
        y0 = 17 + 18 * r
        for (ci, cg) in ((1, 3), (5, 7)):
            xi, xg = 7 + 18 * ci, 7 + 18 * cg
            cv.box(xi, y0, xg + 18, y0 + 18, card, line=line)          # 교환 카드
            cv.box(xi, y0, xi + 18, y0 + 18, panel, line=line)          # 아이템 칸
            cv.box(xg, y0, xg + 18, y0 + 18, gold_slot, line=line)      # 골드 칸
            cv.sprite(xi + 22, y0 + 5, arrow_spr, arrow_pal)            # →
    for y in range(19, 106, 3):                                         # 가운데 점선
        cv.px(88, y, line)
    # 아래 줄: ◀ ▶ 버튼 + 잔액 상자
    cv.rect(3, 107, W - 3, 125, panel)
    cv.rect(3, 107, W - 3, 108, line)
    for ax in (8, 152):
        round_button(cv, ax + 0.5, 108.5, ax + 15.5, 123.5, c("#8fd16a"), c("#c8f0a0"), c("#5fa840"))
    cv.box(27, 110, 148, 123, c("#fffaf0"))
    coin = {"y": c("#ffd34d"), "Y": c("#e0a820"), "w": c("#fff3b0"), "o": LINE}
    cv.sprite(31, 112, [".oooo.", "oywyyo", "oyyYYo", "oyyYYo", "oyYYYo", ".oooo."], coin, k=2)
    chalk_sign(cv, 88, -19, 70, 17, t["label"],
               board=c(t["board"]) if t["board"] else None, board_dk=c(t["board_dk"]) if t["board_dk"] else None,
               shine=c(t["shine"]) if t["shine"] else None, stroke=c(t["stroke"]) if t["stroke"] else None)
    theme_deco(cv, theme, W)
    sparkle(cv, 46, -14, c("#fff3a0"))
    sparkle(cv, 127, -12, WHITE)
    cv.save(f"shop_{theme}_bg.png")
    return cv


# ======================================================================
#  메뉴 (5줄 상자) — 모든 그림은 자기 칸(18x18 격자) 안에만 그려요
#    칸 c열 r줄의 영역: x 7+18c ~ 25+18c, y 17+18r ~ 35+18r
#    0번 줄: 0열 내 머리, 1·7열 하트, 2~6열 "어서 오세요!", 8열 닫기
#    1~2줄: 0~2열 도감,   6~8열 로비
#    1~4줄: 3~5열 워프 (가운데 큰 버튼)
#    3~4줄: 0~2열 야생,   6~8열 마을로
#    ⚠ menu.yml 의 slots 와 같아야 해요
# ======================================================================
def cell(c0, c1, r0, r1):
    """열 c0~c1, 줄 r0~r1 칸들을 합친 영역 (x0, y0, x1, y1)"""
    return 7 + 18 * c0, 17 + 18 * r0, 7 + 18 * (c1 + 1), 17 + 18 * (r1 + 1)


def tile_dogam(cv, x0, y0, x1, y1):
    cv.rect(x0, y0, x1, y1, c("#c4ecff"))
    cv.rect(x0, y1 - 5, x1, y1, c("#8fd16a"))
    cv.rect(x0, y1 - 5, x1, y1 - 4, c("#b6e88c"))
    for (fx, fc) in [(x0 + 3, "#ff6b6b"), (x0 + 8, "#ff9ec4"), (x1 - 5, "#ffffff"), (x1 - 10, "#ffd34d")]:
        cv.px(fx, y1 - 3, c(fc))
    book = {"o": LINE, "r": c("#e85c5c"), "w": c("#fffaf0"), "l": c("#c9b48e"), "m": c("#7acb52")}
    cv.sprite((x0 + x1) // 2 - 11, y0 + 3, [
        "..ooooooooo.ooooooooo..",
        ".owwwwwwwwwowwwwwwwwwo.",
        ".owllllllwwowwmmmmmwwo.",
        ".owwwwwwwwwowwmmmmmwwo.",
        ".owlllllwwwowwmmmmmwwo.",
        ".owwwwwwwwwowwwwwwwwwo.",
        ".owllllllwwowwllllllwo.",
        ".owwwwwwwwwowwwwwwwwwo.",
        "orrrrrrrrrrorrrrrrrrrro",
        ".ooooooooooooooooooooo.",
    ], book)
    sparkle(cv, x1 - 7, y0 + 2, c("#ffd34d"))
    sparkle(cv, x0 + 2, y0 + 5, WHITE)


def tile_shop(cv, x0, y0, x1, y1):
    cv.rect(x0, y0, x1, y1, c("#ffe9b0"))
    for i, x in enumerate(range(x0, x1, 5)):     # 빨강/흰 줄무늬 차양
        col = c("#f26b5b") if i % 2 == 0 else WHITE
        cv.rect(x, y0, min(x + 5, x1), y0 + 5, col)
        cv.rect(x + 1, y0 + 5, min(x + 4, x1), y0 + 7, col)
    cv.box(x0 + 3, y0 + 9, x0 + 29, y1 - 1, c("#a8e0ff"))     # 진열창
    cv.rect(x0 + 5, y0 + 11, x0 + 9, y0 + 12, WHITE)
    for i, col in enumerate(["#ff6b6b", "#ffd34d", "#8fd16a", "#ff9ec4"]):
        cv.box(x0 + 5 + i * 6, y1 - 6, x0 + 10 + i * 6, y1 - 2, c(col))
    cv.box(x1 - 15, y0 + 9, x1 - 4, y1 + 1, c("#c98a4a"))     # 문
    cv.rect(x1 - 13, y0 + 11, x1 - 6, y0 + 15, c("#a8e0ff"))
    cv.px(x1 - 7, y0 + 17, c("#ffd34d"))


def tile_lobby(cv, x0, y0, x1, y1):
    """로비: 빨간 박공지붕 회관(흰 기둥·큰 문·깃발)과 돌 광장, 양옆 가로등·화분"""
    W, H = x1 - x0, y1 - y0
    cv.rect(x0, y0, x1, y1, c("#bfe6ff"))                     # 하늘
    cv.rect(x0 + 3, y0 + 3, x0 + 10, y0 + 5, WHITE)           # 구름
    cv.rect(x0 + 5, y0 + 2, x0 + 8, y0 + 3, WHITE)
    cv.rect(x1 - 10, y0 + 5, x1 - 3, y0 + 7, WHITE)
    cv.rect(x0, y1 - 4, x1, y1, c("#e8dcc8"))                 # 돌 광장
    for x in range(x0 + 1, x1, 5):
        cv.px(x, y1 - 2, c("#cfc0a6"))
    mx = (x0 + x1) // 2
    bx0, bx1 = mx - 15, mx + 15
    roof_top, wall_top, wall_bot = y0 + 3, y0 + 9, y1 - 5
    # 깃발 (지붕 꼭대기, 칸 안쪽)
    cv.rect(mx, y0 + 0, mx + 1, roof_top + 1, LINE)
    cv.rect(mx + 1, y0 + 0, mx + 5, y0 + 2, c("#5cb84a"))
    # 박공 지붕 (외곽선 + 빨강, 아래로 갈수록 넓게)
    for i in range(wall_top - roof_top):
        hw = 3 + i * 3
        cv.rect(mx - hw, roof_top + i, mx + hw + 1, roof_top + i + 1, c("#f26b5b"))
        cv.px(mx - hw, roof_top + i, LINE)
        cv.px(mx + hw, roof_top + i, LINE)
    cv.rect(mx - 1, roof_top + 2, mx + 2, roof_top + 4, c("#ffd34d"))   # 지붕 창 (동그라미 대신 노란 창)
    cv.rect(bx0 - 2, wall_top, bx1 + 3, wall_top + 1, LINE)            # 처마
    # 벽 + 기둥
    cv.rect(bx0, wall_top + 1, bx1 + 1, wall_bot, c("#fff3d6"))
    for cx in (bx0 + 1, bx0 + 7, bx1 - 9, bx1 - 3):
        cv.rect(cx, wall_top + 1, cx + 3, wall_bot, WHITE)
        cv.rect(cx + 2, wall_top + 1, cx + 3, wall_bot, c("#e2d6c0"))
    # 큰 문 (아치 느낌)
    cv.rect(mx - 3, wall_top + 3, mx + 4, wall_bot, LINE)
    cv.rect(mx - 2, wall_top + 4, mx + 3, wall_bot, c("#c98a4a"))
    cv.rect(mx, wall_top + 4, mx + 1, wall_bot, c("#a8703c"))
    cv.px(mx + 2, wall_top + 7, c("#ffd34d"))
    cv.rect(bx0 - 1, wall_bot, bx1 + 2, wall_bot + 1, LINE)           # 계단
    cv.rect(bx0 - 3, wall_bot + 1, bx1 + 4, wall_bot + 2, c("#d8ccb4"))
    # 양옆 가로등
    for lx in (x0 + 3, x1 - 5):
        cv.rect(lx, y0 + 9, lx + 2, y1 - 3, LINE)
        cv.rect(lx - 1, y0 + 7, lx + 3, y0 + 10, c("#ffd34d"))
        cv.rect(lx - 1, y0 + 6, lx + 3, y0 + 7, LINE)
    # 화분
    for fx in (x0 + 7, x1 - 11):
        cv.rect(fx, y1 - 7, fx + 4, y1 - 4, c("#c98a4a"))
        cv.rect(fx, y1 - 9, fx + 4, y1 - 7, c("#5cc04a"))
        cv.px(fx + 1, y1 - 9, c("#ff6b6b"))
        cv.px(fx + 3, y1 - 8, c("#ff9ec4"))


def tile_wild(cv, x0, y0, x1, y1):
    """야생: 해, 눈 덮인 산 두 개, 침엽수 숲, 굽이치는 강, 텐트와 모닥불"""
    cv.rect(x0, y0, x1, y1, c("#a8dcff"))                     # 하늘
    cv.rect(x0, y0 + 7, x1, y1, c("#c4e8ff"))
    sun = [".yyy.", "yyyyy", "yyyyy", "yyyyy", ".yyy."]
    cv.sprite(x1 - 8, y0 + 1, sun, {"y": c("#ffe066")})

    def mountain(cx, top, base, col, shade, snow):
        for i in range(base - top):
            cv.rect(cx - i, top + i, cx + i + 1, top + i + 1, col)
            cv.rect(cx + 1, top + i, cx + i + 1, top + i + 1, shade)   # 오른쪽 그늘
        for i in range(3):
            cv.rect(cx - i, top + i, cx + i + 1, top + i + 1, snow)
    mountain(x0 + 13, y0 + 2, y0 + 13, c("#93aecb"), c("#7a96b6"), WHITE)
    mountain(x0 + 26, y0 + 5, y0 + 13, c("#a3bcd6"), c("#8aa6c4"), c("#eef6ff"))
    # 들판
    cv.rect(x0, y0 + 12, x1, y1, c("#79c656"))
    cv.rect(x0, y0 + 12, x1, y0 + 13, c("#9ad870"))
    # 침엽수 숲 (오른쪽)
    pine = {"g": c("#3f9a3a"), "G": c("#2a6e2a"), "t": c("#8a5a30")}
    rows = ["...g...", "..ggG..", ".gggGG.", "..ggG..", ".gggGG.", "ggggGGG", "...t..."]
    for (tx, ty) in [(x1 - 24, y0 + 7), (x1 - 18, y0 + 5), (x1 - 12, y0 + 7), (x1 - 7, y0 + 9)]:
        cv.sprite(tx, ty, rows, pine)
    # 강: 숲 사이에서 왼쪽 아래로
    river = [(x1 - 26, 3), (x1 - 29, 4), (x1 - 33, 5), (x1 - 38, 6), (x1 - 44, 7), (x1 - 51, 8)]
    for i, (rx, rw) in enumerate(river):
        yy = y0 + 13 + i * 2
        if yy >= y1:
            break
        cv.rect(max(x0, rx), yy, min(x1, rx + rw), min(y1, yy + 2), c("#5cb0f0"))
        cv.px(max(x0, rx + 1), yy, c("#d0f0ff"))
    # 텐트 + 모닥불 (왼쪽)
    tent = ["...o...", "..oro..", ".orrRo.", "orrdRRo", "oooooo."]
    cv.sprite(x0 + 3, y1 - 6, tent, {"o": LINE, "r": c("#ff9a3a"), "R": c("#e07020"), "d": c("#8a5a30")})
    fx = x0 + 12
    cv.rect(fx, y1 - 2, fx + 4, y1 - 1, c("#8a5a30"))
    cv.rect(fx + 1, y1 - 4, fx + 3, y1 - 2, c("#ff7a2a"))
    cv.px(fx + 1, y1 - 5, c("#ffd34d"))
    for (px_, pc) in [(x0 + 22, "#ffffff"), (x1 - 3, "#ff9ec4"), (x0 + 30, "#ffd34d")]:
        cv.px(px_, y1 - 2, c(pc))


def tile_home(cv, x0, y0, x1, y1):
    cv.rect(x0, y0, x1, y1, c("#a8e0ff"))
    cv.rect(x0, y1 - 3, x1, y1, c("#5cc0ea"))
    for (cx, cy) in [(x0 + 3, y0 + 4), (x1 - 13, y0 + 6)]:   # 구름
        cv.rect(cx, cy, cx + 9, cy + 2, WHITE)
        cv.rect(cx + 2, cy - 1, cx + 6, cy, WHITE)
    cv.box(x0 + 7, y1 - 6, x1 - 7, y1 - 1, c("#8fd16a"))      # 섬
    house = {"o": LINE, "r": c("#f26b5b"), "R": c("#d04a3a"), "w": c("#fff3d6"), "b": c("#a8e0ff"), "d": c("#b0703a")}
    cv.sprite((x0 + x1) // 2 - 9, y1 - 16, [
        ".....oo.....",
        "....orro....",
        "...orRRro...",
        "..orrrrrro..",
        ".oooooooooo.",
        "..owbbwddo..",
        "..owbbwddo..",
        "..owwwwddo..",
        "..oooooooo..",
    ], house)
    palm = {"t": c("#b0703a"), "g": c("#5cc04a"), "G": c("#3a8a32")}
    cv.sprite(x1 - 15, y1 - 15, ["gg.gg", "GgggG", ".gtg.", "..t..", "..t..", "...t.", "...t.", "...t.", "...t.", "...t."], palm)


def tile_village(cv, x0, y0, x1, y1):
    """마을로: 언덕 아래 알록달록한 집들과 큰 나무, 광장 길"""
    cv.rect(x0, y0, x1, y1, c("#bfe8ff"))
    cv.rect(x0 + 2, y0 + 3, x0 + 10, y0 + 5, WHITE)           # 구름
    cv.rect(x0 + 4, y0 + 2, x0 + 8, y0 + 3, WHITE)
    cv.rect(x0, y0 + 9, x1, y1, c("#a6dc7c"))                 # 먼 언덕
    cv.rect(x0 + 14, y0 + 7, x0 + 30, y0 + 9, c("#a6dc7c"))
    cv.rect(x0, y1 - 5, x1, y1, c("#8fd16a"))                 # 앞 잔디
    cv.rect(x0 + 20, y1 - 5, x0 + 30, y1, c("#f0d49c"))       # 광장 길
    cv.rect(x0 + 22, y1 - 6, x0 + 28, y1 - 5, c("#f0d49c"))
    house = lambda roof, roof_dk: {"o": LINE, "r": c(roof), "R": c(roof_dk), "w": c("#fff3d6"),
                                   "b": c("#a8e0ff"), "d": c("#b0703a")}
    rows = ["....oo....",
            "...orro...",
            "..orRRro..",
            ".orrrrrro.",
            "oooooooooo",
            ".owbwwddo.",
            ".owwwwddo.",
            ".oooooooo."]
    cv.sprite(x0 + 3, y1 - 12, rows, house("#ff9ec4", "#e8679a"))     # 분홍 지붕 집
    cv.sprite(x0 + 12, y1 - 13, rows, house("#ffd34d", "#e8a91e"))    # 노란 지붕 집
    cv.sprite(x1 - 12, y1 - 12, rows, house("#7ab8ff", "#4a88d8"))    # 파란 지붕 집
    tree = {"g": c("#5cc04a"), "G": c("#3a8a32"), "t": c("#9a6030"), "a": c("#ff6b6b")}
    cv.sprite(x1 - 21, y1 - 15, [".ggg.", "gggaG", "gaggG", "ggggG", ".GGG.", "..t..", "..t..", "..t.."], tree)
    for (fx, fc) in [(x0 + 1, "#ff6b6b"), (x0 + 16, "#ffffff"), (x1 - 3, "#ffd34d")]:
        cv.px(fx, y1 - 2, c(fc))


def tile_warp(cv, x0, y0, x1, y1):
    """가운데 큰 버튼: 별밤 언덕 위의 돌 아치 포털"""
    cv.rect(x0, y0, x1, y1, c("#8f7ae8"))
    cv.rect(x0, y0 + 14, x1, y1, c("#a693f0"))
    cv.rect(x0, y0 + 28, x1, y1, c("#bdaef5"))
    rnd = random.Random(7)
    for _ in range(14):                                        # 별
        cv.px(rnd.randint(x0 + 1, x1 - 2), rnd.randint(y0 + 1, y0 + 26), c(rnd.choice(["#ffffff", "#fff3a0", "#d8ccff"])))
    sparkle(cv, x0 + 5, y0 + 4, WHITE)
    sparkle(cv, x1 - 9, y0 + 9, c("#fff3a0"))
    # 언덕 + 길
    cv.rect(x0, y1 - 10, x1, y1, c("#8fd16a"))
    cv.rect(x0, y1 - 10, x1, y1 - 9, c("#b6e88c"))
    cv.rect(x0 + 6, y1 - 12, x0 + 12, y1 - 10, c("#8fd16a"))
    cv.rect(x1 - 14, y1 - 13, x1 - 6, y1 - 10, c("#8fd16a"))
    mx = (x0 + x1) // 2
    for i, w in enumerate((6, 8, 10, 12)):
        cv.rect(mx - w // 2, y1 - 9 + i * 2, mx + w // 2, y1 - 8 + i * 2, c("#f0d49c"))
    # 돌 아치 (바깥 26 x 34)
    ax0, ax1, ay0, ay1 = mx - 13, mx + 13, y0 + 15, y1 - 9
    stone, stone_dk, stone_hl = c("#c9c3d6"), c("#9a93ad"), c("#ece8f5")
    cv.rect(ax0 + 4, ay0 - 2, ax1 - 4, ay0, LINE)
    cv.rect(ax0 + 2, ay0, ax1 - 2, ay0 + 2, LINE)
    cv.rect(ax0, ay0 + 2, ax1, ay1, LINE)
    cv.rect(ax0 + 5, ay0 - 1, ax1 - 5, ay0 + 1, stone)
    cv.rect(ax0 + 3, ay0 + 1, ax1 - 3, ay0 + 3, stone)
    cv.rect(ax0 + 1, ay0 + 3, ax1 - 1, ay1, stone)
    for yy in range(ay0 + 6, ay1, 6):                         # 돌 이음매
        cv.rect(ax0 + 1, yy, ax0 + 5, yy + 1, stone_dk)
        cv.rect(ax1 - 5, yy, ax1 - 1, yy + 1, stone_dk)
    cv.rect(ax0 + 1, ay0 + 3, ax0 + 2, ay1, stone_hl)
    # 포털 (안쪽 소용돌이)
    px0, px1, py0, py1 = ax0 + 5, ax1 - 5, ay0 + 5, ay1
    cv.rect(px0 + 2, py0 - 1, px1 - 2, py0, LINE)
    cv.rect(px0, py0, px1, py1, LINE)
    cv.rect(px0 + 1, py0 + 1, px1 - 1, py1, c("#6a3fd8"))
    cv.rect(px0 + 3, py0 + 3, px1 - 3, py1 - 1, c("#8f5cf5"))
    cv.rect(px0 + 5, py0 + 6, px1 - 5, py1 - 3, c("#b98cff"))
    cv.rect(px0 + 7, py0 + 9, px1 - 7, py1 - 6, c("#e2cfff"))
    for (dx, dy) in [(2, 4), (12, 7), (4, 15), (13, 18), (8, 2)]:
        cv.px(px0 + dx, py0 + dy, WHITE)
    # 아치 위 꽃덩굴
    for (vx, col) in [(ax0 + 2, "#ff9ec4"), (ax1 - 4, "#ffd34d"), (ax0 + 6, "#ffffff")]:
        cv.px(vx, ay0 + 3, c(col))
        cv.px(vx + 1, ay0 + 4, c("#5cc04a"))


MENU_TILES = [  # (열 범위, 줄 범위, 그리기, 이름표, 이름표 색)
    ((0, 2), (1, 2), tile_dogam, "도감", "#5cb84a"),
    ((6, 8), (1, 2), tile_lobby, "로비", "#f2a33a"),
    ((3, 5), (1, 4), tile_warp, "워프", "#8a5ce0"),
    ((0, 2), (3, 4), tile_wild, "야생", "#4aa0e8"),
    ((6, 8), (3, 4), tile_village, "마을로", "#e8709a"),
]


def draw_menu():
    W, H = 176, 204
    cv = Canvas(W, H)
    inventory(cv, label_y=110, inv_y=122, hotbar_y=180, bottom=H)
    cv.wood_frame(0, 0, W, 110)
    cv.rect(3, 3, W - 3, 107, c("#fff3d6"))
    for x in range(3, W - 3, 8):                     # 줄무늬 벽지
        cv.rect(x, 3, x + 4, 107, c("#fdecc8"))
    cv.rect(3, 3, W - 3, 4, c("#f0d49c"))

    # 0번 줄 -------------------------------------------------
    x0, y0, x1, y1 = cell(0, 0, 0, 0)                # 내 머리 액자 (머리는 x0+1~x1-1 에 그려짐)
    cv.box(x0, y0, x1, y1, c("#fffaf0"), line=c("#d9a820"))
    cv.bevel(x0, y0, x1, y1, c("#ffd34d"), c("#ffd34d"))
    x0, y0, x1, y1 = cell(8, 8, 0, 0)                # 닫기
    round_button(cv, x0 + 1, y0 + 1, x1 - 1, y1 - 1, c("#ff7b6b"), c("#ffb3a8"), c("#d9534a"))
    for i in range(8):
        cv.rect(x0 + 5 + i, y0 + 5 + i, x0 + 7 + i, y0 + 7 + i, WHITE)
        cv.rect(x1 - 7 - i, y0 + 5 + i, x1 - 5 - i, y0 + 7 + i, WHITE)
    x0, y0, x1, y1 = cell(2, 6, 0, 0)                # 리본
    cv.box(x0 + 1, y0 + 3, x1 - 1, y1 - 3, c("#ff9ec4"))
    cv.bevel(x0 + 1, y0 + 3, x1 - 1, y1 - 3, c("#ffd0e2"), c("#e8679a"))
    cv.text((x0 + x1) / 2, (y0 + y1) / 2, "어서 오세요!", 7, WHITE, stroke=c("#b04a72"), sw=0.5)
    for col in (1, 7):                               # 하트 (자기 칸 가운데)
        x0, y0, x1, y1 = cell(col, col, 0, 0)
        cv.sprite(x0 + 6, y0 + 7, [".hh.hh.", "hhhhhhH", ".hhhhH.", "..hhH..", "...H..."],
                  {"h": c("#ff6b8a"), "H": c("#d94a6a")})

    # 큰 버튼: 칸 영역 안쪽 1px부터 그려서 밖으로 안 나가게 ---------
    for (c0, c1), (r0, r1), draw, label, band in MENU_TILES:
        x0, y0, x1, y1 = cell(c0, c1, r0, r1)
        x0, y0, x1, y1 = x0 + 1, y0 + 1, x1 - 1, y1 - 1
        cv.rect(x0, y0, x1, y1, LINE)
        for (x, y) in [(x0, y0), (x1 - 1, y0), (x0, y1 - 1), (x1 - 1, y1 - 1)]:
            cv.px(x, y, c("#fdecc8"))
        band_top = y1 - 11                           # 아래 이름 띠 (높이 10)
        draw(cv, x0 + 1, y0 + 1, x1 - 1, band_top)
        cv.rect(x0 + 1, y0 + 1, x1 - 1, y0 + 2, c("#ffffff"))
        cv.rect(x0 + 1, band_top, x1 - 1, band_top + 1, LINE)
        cv.rect(x0 + 1, band_top + 1, x1 - 1, y1 - 1, c(band))
        cv.rect(x0 + 1, band_top + 1, x1 - 1, band_top + 2, c("#ffffff"))
        crisp_label(cv, (x0 + x1) / 2, band_top + 5.5, label, 6.5, WHITE, LINE,
                    shadow=tuple(max(0, v - 60) for v in c(band)[:3]) + (255,))

    chalk_sign(cv, 88, -19, 70, 17, "MENU")
    flowers(cv, 2, -4)
    flowers(cv, W - 21, -4, mirror=True)
    sparkle(cv, 45, -14, c("#fff3a0"))
    sparkle(cv, 128, -12, WHITE)
    cv.save("menu_bg.png")
    return cv


def draw_gold_coin():
    """금화: 진한 테두리, 안쪽 고리, 가운데 G, 왼쪽 위 반짝임, 아래쪽 두께"""
    N = 16
    img = Image.new("RGBA", (N, N), CLEAR)
    px = img.load()
    out_c, edge_c = c("#7a4a0c"), c("#c0841a")
    body, rim, rim_dk = c("#ffcc33"), c("#f0b028"), c("#d99a1c")
    shine, shine2 = c("#fff6c0"), c("#ffe27a")
    g_c, g_hl = c("#9a640c"), c("#ffe9a0")
    cx, cy, r = 7.5, 7.0, 6.6
    for y in range(N):
        for x in range(N):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            d2 = ((x - cx) ** 2 + (y - cy - 1) ** 2) ** 0.5     # 아래로 1칸 = 동전 두께
            if d <= r:
                if d > r - 1:
                    px[x, y] = out_c
                elif d > r - 2.2:
                    px[x, y] = rim_dk if y > cy + 2 else rim   # 안쪽 고리 (아래쪽은 살짝 어둡게)
                else:
                    px[x, y] = body
            elif d2 <= r:
                px[x, y] = edge_c if d2 <= r - 1 else out_c
    # 반짝임
    for (x, y, col) in [(4, 3, shine), (5, 3, shine), (3, 4, shine), (3, 5, shine2), (6, 3, shine2)]:
        px[x, y] = col
    # G (5x7)
    G = [".####",
         "#....",
         "#....",
         "#.###",
         "#...#",
         "#...#",
         ".####"]
    gx, gy = 5, 4
    for j, row in enumerate(G):
        for i, ch in enumerate(row):
            if ch == "#":
                px[gx + i, gy + j] = g_c
    img.resize((32, 32), Image.NEAREST).save(os.path.join(OUT, "gold_coin.png"))


# ======================================================================
#  HUD (보스바 제목 자리에 그리는 왼쪽 위 상자, 아이콘, 경험치 이름표)
#    ⚠ 크기·순서는 HudFeature.java, build_pack.py 와 약속
#    hud_panel.png  88x40 (2배 176x80) 반투명 어두운 상자
#    hud_coin.png / hud_<skill>.png  8x8 (2배 16x16)
#    hud_label_<skill>.png  34x8 (2배 68x16) "농사 레벨" 같은 글자 (오른쪽 정렬)
# ======================================================================
SKILL_ICONS = ["farming", "mining", "woodcutting", "fishing", "combat"]   # Skill.java 순서
SKILL_LABELS = {"farming": "농사 레벨", "mining": "채광 레벨", "woodcutting": "벌목 레벨",
                "fishing": "낚시 레벨", "combat": "전투 레벨"}
HUD_ICON_ROWS = {
    "farming": ["..y.y...", ".yYyYy..", "..yYy...", "...g....", "..ggg...", "...g....", "...g....", "........"],
    "mining": ["..iiiii.", ".....tii", "....t..i", "...t...i", "..t.....", ".t......", "t.......", "........"],
    "woodcutting": ["...iii..", "..iiiii.", "..iiit..", "....t...", "...t....", "..t.....", ".t......", "t......."],
    "fishing": ["........", "..bbb..b", ".bwbbbbb", ".bbbbbbb", "..bbb..b", "........", "........", "........"],
    "combat": ["......ii", ".....ii.", "....ii..", ".g.ii...", "..gi....", "..tg....", ".t..g...", "t......."],
}
HUD_ICON_PAL = {"y": c("#f2cc4a"), "Y": c("#c99a2a"), "g": c("#7acb52"), "i": c("#d8dde6"),
                "t": c("#b0773a"), "b": c("#6cc4f0"), "w": WHITE}


def _fix_width(img):
    """마인크래프트가 글자 폭을 잴 때 이미지 폭 그대로 되게 오른쪽 끝에 거의 안 보이는 점 (알파 1)"""
    w, h = img.size
    if not any(img.getpixel((w - 1, y))[3] > 0 for y in range(h)):
        img.putpixel((w - 1, 0), (0, 0, 0, 1))
    return img


def draw_hud():
    # 상자
    panel = Image.new("RGBA", (176, 80), CLEAR)
    d = ImageDraw.Draw(panel)
    d.rounded_rectangle([0, 0, 175, 79], radius=8, fill=(20, 16, 24, 150))
    d.rounded_rectangle([2, 2, 173, 77], radius=7, outline=(255, 255, 255, 40), width=2)
    panel.save(os.path.join(OUT, "hud_panel.png"))
    # 코인 (금화 그림을 16x16 으로)
    coin = Image.open(os.path.join(OUT, "gold_coin.png")).resize((16, 16), Image.NEAREST)
    _fix_width(coin).save(os.path.join(OUT, "hud_coin.png"))
    # 스킬 아이콘
    for key in SKILL_ICONS:
        img = Image.new("RGBA", (16, 16), CLEAR)
        dd = ImageDraw.Draw(img)
        for y, row in enumerate(HUD_ICON_ROWS[key]):
            for x, ch in enumerate(row):
                if ch in HUD_ICON_PAL:
                    dd.rectangle([x * 2, y * 2, x * 2 + 1, y * 2 + 1], fill=HUD_ICON_PAL[ch])
        _fix_width(img).save(os.path.join(OUT, f"hud_{key}.png"))
    # 경험치 이름표 ("농사 레벨")
    f = font(8.5)
    for key in SKILL_ICONS:
        img = Image.new("RGBA", (68, 16), CLEAR)      # 34x8, 글자는 오른쪽에 붙여서 숫자와 이어지게
        dd = ImageDraw.Draw(img)
        dd.text((67, 8), SKILL_LABELS[key], font=f, fill=c("#c8f0a0"), anchor="rm")
        a = img.getchannel("A").point(lambda v: 255 if v >= 110 else 0)   # 반투명 가장자리 정리
        img.putalpha(a)
        _fix_width(img).save(os.path.join(OUT, f"hud_label_{key}.png"))


# ======================================================================
#  아이템 모델용 그림 / 폰트 조각
# ======================================================================
def draw_items():
    # 체크 (32x32): 오른쪽 아래 구석의 작은 초록 체크 + 흰 테두리
    im = Image.new("RGBA", (32, 32), CLEAR)
    p = im.load()
    body = set()

    def dot(x, y, r=1):
        for dx in range(-r, r + 1):
            for dy in range(-r, r + 1):
                if abs(dx) + abs(dy) <= r + 1:
                    body.add((x + dx, y + dy))
    for i in range(4):
        dot(20 + i, 24 + i)
    for i in range(8):
        dot(23 + i, 27 - i)
    body = {(x, y) for x, y in body if 0 <= x < 32 and 0 <= y < 32}
    edge = {(x + dx, y + dy) for x, y in body for dx in (-1, 0, 1) for dy in (-1, 0, 1)} - body
    for q in edge:
        if 0 <= q[0] < 32 and 0 <= q[1] < 32:
            p[q] = WHITE
    for q in body:
        p[q] = c("#3fc93a")
    for x, y in body:
        if (x, y + 1) not in body:
            p[x, y] = c("#249a2a")
    im.save(os.path.join(OUT, "check.png"))

    # 골드 코인 (16x16 도트 → 2배 32x32): G가 새겨진 금화
    draw_gold_coin()

    # 화살표 (흰 화살표 + 갈색 외곽선) — 초록 버튼 위에 놓임. 꺼짐은 회색
    # 12x11 화살표를 2배로 → 32x32 가운데 (게임에선 약 12x11 px)
    rows = [".....o......",
            "....oo......",
            "...owo......",
            "..owwoooooo.",
            ".owwwwwwwwwo",
            "owwwwwwwwwwo",
            ".owwwwwwwwwo",
            "..owwoooooo.",
            "...owo......",
            "....oo......",
            ".....o......"]

    def arrow(right, on):
        rr = [r[::-1] for r in rows] if right else rows
        pal = {"o": LINE if on else c("#9a8a7a"), "w": WHITE if on else c("#d8d0c4")}
        img = Image.new("RGBA", (32, 32), CLEAR)
        d = ImageDraw.Draw(img)
        for j, row in enumerate(rr):
            for i, ch in enumerate(row):
                if ch in pal:
                    d.rectangle([4 + i * 2, 5 + j * 2, 5 + i * 2, 6 + j * 2], fill=pal[ch])
        return img
    arrow(False, True).save(os.path.join(OUT, "arrow_left.png"))
    arrow(True, True).save(os.path.join(OUT, "arrow_right.png"))
    arrow(False, False).save(os.path.join(OUT, "arrow_left_off.png"))
    arrow(True, False).save(os.path.join(OUT, "arrow_right_off.png"))

    # 게이지 조각 (4x5 → 2배 8x10): 밝은 초록 + 윗줄 반짝
    bar = Image.new("RGBA", (8, 10), c("#6fd34a"))
    bd = ImageDraw.Draw(bar)
    bd.rectangle([0, 0, 7, 1], fill=c("#c4f59a"))
    bd.rectangle([0, 8, 7, 9], fill=c("#47a832"))
    bar.save(os.path.join(OUT, "bar_full.png"))

    # 페이지 숫자 0~9 + '/' (갈색, 각 4x7 → 2배 8x14). 흰 상자 위에 놓이니 글자 색을 미리 칠해요
    DIG = {
        "0": [".##.", "#..#", "#..#", "#..#", "#..#", "#..#", ".##."],
        "1": ["..#.", ".##.", "..#.", "..#.", "..#.", "..#.", ".###"],
        "2": [".##.", "#..#", "...#", "..#.", ".#..", "#...", "####"],
        "3": ["###.", "...#", "...#", ".##.", "...#", "...#", "###."],
        "4": ["#..#", "#..#", "#..#", "####", "...#", "...#", "...#"],
        "5": ["####", "#...", "###.", "...#", "...#", "#..#", ".##."],
        "6": [".##.", "#...", "#...", "###.", "#..#", "#..#", ".##."],
        "7": ["####", "...#", "...#", "..#.", ".#..", ".#..", ".#.."],
        "8": [".##.", "#..#", "#..#", ".##.", "#..#", "#..#", ".##."],
        "9": [".##.", "#..#", "#..#", ".###", "...#", "...#", ".##."],
        "/": ["...#", "...#", "..#.", ".##.", ".#..", "#...", "#..."],
        ",": ["....", "....", "....", "....", ".##.", ".##.", "#..."],
        "G": [".###", "#...", "#...", "#.##", "#..#", "#..#", ".###"],
    }
    order = "0123456789/,G"   # \uE010~\uE01C (Glyphs.java 와 같은 순서)
    dimg = Image.new("RGBA", (len(order) * 8, 14), CLEAR)
    dd = ImageDraw.Draw(dimg)
    for k, ch in enumerate(order):
        for y, row in enumerate(DIG[ch]):
            for x, v in enumerate(row):
                if v == "#":
                    dd.rectangle([k * 8 + x * 2, y * 2, k * 8 + x * 2 + 1, y * 2 + 1], fill=LINE)
        # 마인크래프트는 글자 폭을 "오른쪽 끝 색칠된 줄"로 재요. 모든 글자 폭을 4로 맞추려고
        # 맨 오른쪽 줄이 비어 있으면 거의 안 보이는 점(알파 1)을 하나 찍어요
        if not any(row[3] == "#" for row in DIG[ch]):
            dimg.putpixel((k * 8 + 7, 0), (0, 0, 0, 1))
    dimg.save(os.path.join(OUT, "digits.png"))


# ======================================================================
#  스킬 창 (6줄 상자) — 메뉴에서 내 얼굴을 누르면
#    0~4줄: 0열 아이콘 칸, 1~8열 카드 (x 25~169)
#      줄 r 칸 윗변 y0 = 17 + 18r
#      "농사" 글씨 가운데 x 38, Lv 글자 x 52~, 경험치 숫자 오른쪽 끝 x 165 (윗변 y0+2, 플러그인이 씀)
#      게이지 상자 x 28~167 / y0+10~y0+17, 안쪽 채움 x 29~ / y0+11~y0+15 (플러그인이 씀)
#    5줄: 0열 ◀ (메뉴로)
#    ⚠ SkillGui.java, build_pack.py 의 SKILL_ROW_TOP 과 약속
# ======================================================================
SKILL_NAMES = ["농사", "채광", "벌목", "낚시", "전투"]          # Skill.java 순서
SKILL_COLORS = ["#7acb52", "#8fb4d8", "#c99a5a", "#5ab0e0", "#e07a6a"]   # 아이콘 칸 색


def draw_skill():
    W, H = 176, 222
    cv = Canvas(W, H)
    inventory(cv, label_y=128, inv_y=140, hotbar_y=198, bottom=H)
    cv.wood_frame(0, 0, W, 128)
    cv.rect(3, 3, W - 3, 16, c("#fff0cc"))           # 제목 띠 (게임이 '총 레벨 N'을 씀)
    cv.rect(3, 15, W - 3, 16, c("#f0d49c"))
    cv.rect(3, 16, W - 3, 107, PANEL)
    for y in range(18, 106, 6):
        for x in range(5 + (y // 6) % 2 * 3, W - 4, 6):
            cv.px(x, y, c("#e2b87e"))
    for r in range(5):
        y0 = 17 + 18 * r
        # 아이콘 칸 (분야 색 테두리)
        cv.box(7, y0, 25, y0 + 18, c("#fff4dc"), line=c(SKILL_COLORS[r]))
        cv.rect(8, y0 + 1, 24, y0 + 2, c("#f6e2bc"))
        # 카드
        cv.box(25, y0, 170, y0 + 18, c("#fffaf0"), line=SLOT_LINE)
        cv.text(38, y0 + 5.5, SKILL_NAMES[r], 7.5, LINE)
        # 게이지 상자
        cv.box(28, y0 + 10, 167, y0 + 17, c("#d9bf98"), line=c("#d9bf98"), round_=False)
        cv.rect(29, y0 + 11, 166, y0 + 16, c("#f1e2c8"))
    # 아래 줄: ◀ 메뉴로
    cv.rect(3, 107, W - 3, 125, PANEL)
    cv.rect(3, 107, W - 3, 108, SLOT_LINE)
    round_button(cv, 8.5, 108.5, 23.5, 123.5, c("#8fd16a"), c("#c8f0a0"), c("#5fa840"))
    cv.text(46, 116, "메뉴로", 7, c("#9a6a3e"))
    chalk_sign(cv, 88, -19, 56, 17, "스 킬")
    flowers(cv, 2, -4)
    flowers(cv, W - 21, -4, mirror=True)
    sparkle(cv, 52, -14, c("#fff3a0"))
    sparkle(cv, 121, -12, WHITE)
    cv.save("skill_bg.png")
    return cv


# ======================================================================
#  집 지도 창 (6줄 상자) — /land, 메뉴의 "집으로" (집이 없을 때)
#    0~4줄 2~6열: 청크 지도 5x5 (가운데 = 집터 청크, 위 = 북쪽) — 풀밭 격자
#    왼쪽 북쪽 표시, 오른쪽 꽃·울타리,  5줄: 0열 ◀, 3~5열 버튼 칸 (빚·이웃·테두리)
#    ⚠ LandGui.java 와 약속
# ======================================================================
def draw_land():
    W, H = 176, 222
    cv = Canvas(W, H)
    inventory(cv, label_y=128, inv_y=140, hotbar_y=198, bottom=H)
    cv.wood_frame(0, 0, W, 128)
    cv.rect(3, 3, W - 3, 16, c("#fff0cc"))           # 제목 띠 (게임이 '내 집 · 땅 2/4'를 씀)
    cv.rect(3, 15, W - 3, 16, c("#f0d49c"))
    cv.rect(3, 16, W - 3, 107, PANEL)
    for y in range(18, 106, 6):
        for x in range(5 + (y // 6) % 2 * 3, W - 4, 6):
            cv.px(x, y, c("#e2b87e"))
    # 지도 (풀밭): 2~6열 x 0~4줄 = 집터에서 상하좌우 2칸
    x0, y0, x1, y1 = 7 + 18 * 2, 17, 7 + 18 * 7, 17 + 18 * 5
    cv.box(x0 - 2, y0 - 1, x1 + 2, y1 + 1, c("#9fd46e"), line=c("#5f9a3c"))
    rnd = random.Random(7)
    for r in range(5):
        for col in range(5):
            cx0, cy0 = x0 + 18 * col, y0 + 18 * r
            base = c("#a8dc78") if (r + col) % 2 == 0 else c("#9cd26c")
            cv.rect(cx0, cy0, cx0 + 18, cy0 + 18, base)
            for _ in range(3):                                   # 풀잎 점
                gx, gy = cx0 + rnd.randint(2, 14), cy0 + rnd.randint(2, 14)
                cv.rect(gx, gy, gx + 1, gy + 2, c("#7cbf52"))
    # 가운데(집터) 칸 살짝 밝게
    cv.rect(x0 + 36 + 1, y0 + 36 + 1, x0 + 54 - 1, y0 + 54 - 1, c("#bfe890"))
    for col in range(1, 5):                                      # 청크 경계 점선
        for y in range(y0, y1, 2):
            cv.px(x0 + 18 * col, y, c("#86c25a"))
    for r in range(1, 5):
        for x in range(x0, x1, 2):
            cv.px(x, y0 + 18 * r, c("#86c25a"))
    # 왼쪽: 방향 안내 (작은 집 그림 — 문이 아래 = 지도 아래쪽이 집 앞)
    cv.box(12, 20, 34, 48, c("#fffaf0"))
    house = ["...rr...",
             "..rrrr..",
             ".rrrrrr.",
             "rrrrrrrr",
             ".wwwwww.",
             ".wbwwbw.",
             ".wwddww.",
             ".wwddww."]
    cv.sprite(15, 22, house, {"r": c("#d9604a"), "w": c("#f0d6a8"), "b": c("#7ac0e8"), "d": c("#8a5a30")}, k=int(S * 2))
    cv.text(23, 43, "▼ 앞", 5.5, LINE)
    for i, (fx, fy, spr) in enumerate([(14, 70, TULIP_R), (24, 84, DAISY), (146, 30, SUN), (156, 50, TULIP_P),
                                        (148, 80, DAISY), (16, 96, SUN)]):
        cv.sprite(fx, fy, spr, PAL, k=S)
    for fx in (138, 150, 162):                                   # 울타리
        cv.rect(fx, 62, fx + 2, 72, c("#c99a5a"))
    cv.rect(136, 64, 166, 66, c("#c99a5a"))
    cv.rect(136, 68, 166, 70, c("#c99a5a"))
    # 아래 줄: ◀ + 버튼 칸 3개 (3~5열: 빚 · 이웃 · 테두리)
    cv.rect(3, 107, W - 3, 125, PANEL)
    cv.rect(3, 107, W - 3, 108, SLOT_LINE)
    round_button(cv, 8.5, 108.5, 23.5, 123.5, c("#8fd16a"), c("#c8f0a0"), c("#5fa840"))
    for col in range(3, 6):
        bx = 7 + 18 * col
        cv.box(bx, 107, bx + 18, 125, c("#fff4dc"), line=SLOT_LINE)
    chalk_sign(cv, 88, -19, 56, 17, "내 집")
    flowers(cv, 2, -4)
    flowers(cv, W - 21, -4, mirror=True)
    sparkle(cv, 52, -14, c("#fff3a0"))
    sparkle(cv, 121, -12, WHITE)
    cv.save("land_bg.png")
    # 지도 위 "+ 넓히기" 표시 (아이템 모델): 노란 동그라미 + 흰 십자 / 잠김(빚)은 회색
    for name, fill, ring in (("land_plus", "#ffd34d", "#c9921e"), ("land_plus_off", "#d8d0c4", "#9a8a7a")):
        img = Image.new("RGBA", (32, 32), CLEAR)
        d = ImageDraw.Draw(img)
        d.ellipse([4, 4, 27, 27], fill=c(ring))
        d.ellipse([6, 6, 25, 25], fill=c(fill))
        d.rectangle([14, 9, 17, 22], fill=WHITE)
        d.rectangle([9, 14, 22, 17], fill=WHITE)
        img.save(os.path.join(OUT, name + ".png"))
    return cv


# ======================================================================
#  이웃 허가 창 (6줄 상자) — 서버 사람들 얼굴 9x5 + 아래 줄
#    5줄: 0열 ◀ (집 지도로), 3열 ◀ 5열 ▶ (페이지), 4열 페이지 숫자 상자 (숫자 가운데 x 88 / 윗변 y 113)
#    ⚠ MembersGui.java 와 약속
# ======================================================================
def draw_members():
    W, H = 176, 222
    cv = Canvas(W, H)
    inventory(cv, label_y=128, inv_y=140, hotbar_y=198, bottom=H)
    cv.wood_frame(0, 0, W, 128)
    cv.rect(3, 3, W - 3, 16, c("#fff0cc"))
    cv.rect(3, 15, W - 3, 16, c("#f0d49c"))
    cv.rect(3, 16, W - 3, 107, PANEL)
    for y in range(18, 106, 6):
        for x in range(5 + (y // 6) % 2 * 3, W - 4, 6):
            cv.px(x, y, c("#e2b87e"))
    for r in range(5):
        for col in range(9):
            x0, y0 = 7 + 18 * col, 17 + 18 * r
            cv.box(x0, y0, x0 + 18, y0 + 18, c("#fff4dc"), line=SLOT_LINE)
            cv.rect(x0 + 1, y0 + 1, x0 + 17, y0 + 2, c("#f6e2bc"))
    cv.rect(3, 107, W - 3, 125, PANEL)
    cv.rect(3, 107, W - 3, 108, SLOT_LINE)
    for ax in (8, 8 + 18 * 3, 8 + 18 * 5):
        round_button(cv, ax + 0.5, 108.5, ax + 15.5, 123.5, c("#8fd16a"), c("#c8f0a0"), c("#5fa840"))
    cv.box(7 + 18 * 4 - 2, 110, 7 + 18 * 5 + 2, 123, WHITE)     # 페이지 숫자 상자
    chalk_sign(cv, 88, -19, 56, 17, "이 웃")
    flowers(cv, 2, -4)
    flowers(cv, W - 21, -4, mirror=True)
    sparkle(cv, 52, -14, c("#fff3a0"))
    sparkle(cv, 121, -12, WHITE)
    cv.save("members_bg.png")
    return cv


# ======================================================================
#  집터 모형 텍스처 (16x16) — build_pack.py 가 servercore:house_core 모델로 묶어요
# ======================================================================
def draw_house_textures():
    def tex(name, fn):
        img = Image.new("RGBA", (16, 16), CLEAR)
        fn(ImageDraw.Draw(img), img)
        img.save(os.path.join(OUT, name + ".png"))

    def wall(d, img):          # 따뜻한 나무 판자 (가로줄)
        d.rectangle([0, 0, 15, 15], fill=c("#f0d6a8"))
        for y in (3, 7, 11, 15):
            d.line([0, y, 15, y], fill=c("#d9b47e"))
        for (x, y) in ((4, 1), (11, 5), (2, 9), (9, 13)):
            d.point((x, y), fill=c("#c99a5a"))
        d.line([0, 0, 0, 15], fill=c("#c99a5a"))
        d.line([15, 0, 15, 15], fill=c("#c99a5a"))

    def roof(d, img):          # 빨간 기와
        d.rectangle([0, 0, 15, 15], fill=c("#d9604a"))
        for y in range(0, 16, 4):
            d.line([0, y + 3, 15, y + 3], fill=c("#a8402e"))
            off = 0 if (y // 4) % 2 == 0 else 2
            for x in range(off, 16, 4):
                d.point((x, y + 1), fill=c("#ec8a6c"))

    def door(d, img):          # 나무 문 + 손잡이
        d.rectangle([0, 0, 15, 15], fill=c("#8a5a30"))
        d.rectangle([1, 1, 14, 14], fill=c("#a8703c"))
        d.line([7, 1, 7, 14], fill=c("#8a5a30"))
        d.rectangle([2, 2, 5, 5], fill=c("#c08a50"))
        d.rectangle([9, 2, 12, 5], fill=c("#c08a50"))
        d.rectangle([11, 8, 12, 9], fill=c("#ffd34d"))

    def window(d, img):        # 창문 (하늘색 유리 + 흰 창틀)
        d.rectangle([0, 0, 15, 15], fill=WHITE)
        d.rectangle([2, 2, 13, 13], fill=c("#7ac0e8"))
        d.line([2, 7, 13, 7], fill=WHITE)
        d.line([7, 2, 7, 13], fill=WHITE)
        d.rectangle([3, 3, 4, 4], fill=c("#c8ecff"))

    def base(d, img):          # 돌 바닥
        d.rectangle([0, 0, 15, 15], fill=c("#b8b0a4"))
        for (x, y) in ((2, 3), (8, 1), (12, 6), (5, 10), (10, 13)):
            d.rectangle([x, y, x + 2, y + 1], fill=c("#9a9286"))

    def chimney(d, img):       # 굴뚝 벽돌
        d.rectangle([0, 0, 15, 15], fill=c("#b05a40"))
        for y in range(0, 16, 4):
            d.line([0, y, 15, y], fill=c("#8a4030"))

    for n, f in (("house_wall", wall), ("house_roof", roof), ("house_door", door), ("house_window", window),
                 ("house_base", base), ("house_chimney", chimney)):
        tex(n, f)


if __name__ == "__main__":
    draw_dogam()
    for t in SHOP_THEMES:
        draw_shop(t)
    draw_menu()
    draw_items()
    draw_hud()
    draw_skill()
    draw_land()
    draw_members()
    draw_house_textures()
    print("완료:", OUT)
