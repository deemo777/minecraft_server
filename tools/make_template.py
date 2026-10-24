"""
리소스팩 고정 그림 만들기 (한 번만 실행하면 됨, 결과는 tools/pack_template/ 에 들어 있음)
  - 체크, 숫자, 화살표, 진행도 막대, 버튼 모양
색이나 모양을 바꾸고 싶으면 이 파일의 색 값을 고치고 다시 실행하세요:  python make_template.py
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "pack_template", "assets")
SC = os.path.join(OUT, "servercore", "textures", "font")
BTN = os.path.join(OUT, "minecraft", "textures", "gui", "sprites", "widget")
os.makedirs(SC, exist_ok=True)
os.makedirs(BTN, exist_ok=True)


def c(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


GREEN, GREEN_DARK = c("#1fc21f"), c("#128a12")
KHAKI, KHAKI_HI = c("#8a8466"), c("#e2d6a8")
SLOT, SLOT_HI = c("#0c0a0b", 235), c("#1f1a17", 245)
BAR_LINE, BAR_FILL, BAR_FILL_HI, BAR_EMPTY = c("#5f5b4c"), c("#cdbf8e"), c("#e2d6a8"), c("#0d0b0c", 220)
ARROW, ARROW_OFF, ARROW_EDGE = c("#e8e8e8"), c("#55524a"), c("#1a1715")

# ---------- 체크 (32x32, 게임에서는 16 크기로 표시 → 선명) ----------
def make_check():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    p = im.load()
    body = set()

    def dot(x, y, r=2):
        for dx in range(-r, r + 1):
            for dy in range(-r, r + 1):
                if abs(dx) + abs(dy) <= r + 1:
                    body.add((x + dx, y + dy))

    for i in range(9):            # 짧은 획
        dot(4 + i, 16 + i)
    for i in range(18):           # 긴 획
        dot(12 + i, 24 - i)
    body = {(x, y) for x, y in body if 0 <= x < 32 and 0 <= y < 32}
    for x, y in body:
        p[x, y] = GREEN
    for x, y in body:
        if (x, y + 1) not in body or (x, y + 2) not in body:
            p[x, y] = GREEN_DARK
    return im


# ---------- 숫자 0~9 (4x7 한 줄 이미지, 흰색 → 게임에서 글자 색으로 칠해짐) ----------
DIGITS = {
    "0": ["####", "#..#", "#..#", "#..#", "#..#", "#..#", "####"],
    "1": [".##.", "###.", ".##.", ".##.", ".##.", ".##.", "####"],
    "2": ["####", "...#", "...#", "####", "#...", "#...", "####"],
    "3": ["####", "...#", "...#", ".###", "...#", "...#", "####"],
    "4": ["#..#", "#..#", "#..#", "####", "...#", "...#", "...#"],
    "5": ["####", "#...", "#...", "####", "...#", "...#", "####"],
    "6": ["####", "#...", "#...", "####", "#..#", "#..#", "####"],
    "7": ["####", "...#", "...#", "..#.", ".#..", ".#..", ".#.."],
    "8": ["####", "#..#", "#..#", "####", "#..#", "#..#", "####"],
    "9": ["####", "#..#", "#..#", "####", "...#", "...#", "####"],
}

def make_digits():
    im = Image.new("RGBA", (40, 7), (0, 0, 0, 0))
    p = im.load()
    for d in range(10):
        rows = DIGITS[str(d)]
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == "#":
                    p[d * 4 + x, y] = (255, 255, 255, 255)
    return im


# ---------- 화살표 (32x32) ----------
def make_arrow(right: bool, on: bool):
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    p = im.load()
    fill = ARROW if on else ARROW_OFF
    # 삼각형: 높이 20, 너비 12, 가운데
    tri = set()
    for x in range(12):
        half = int(10 * (1 - x / 12)) if right else int(10 * (x / 12))
        for y in range(16 - half, 16 + half):
            xx = 10 + x
            tri.add((xx, y))
    for x, y in tri:
        p[x, y] = fill
    for x, y in tri:  # 가장자리
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in tri and 0 <= q[0] < 32 and 0 <= q[1] < 32:
                p[q] = ARROW_EDGE
    return im


# ---------- 진행도 막대 ----------
def make_bar_track():
    im = Image.new("RGBA", (152, 9), BAR_EMPTY)
    p = im.load()
    for x in range(152):
        p[x, 0] = BAR_LINE
        p[x, 8] = BAR_LINE
    for y in range(9):
        p[0, y] = BAR_LINE
        p[151, y] = BAR_LINE
    return im


def make_bar_fill():
    im = Image.new("RGBA", (5, 7), BAR_FILL)
    p = im.load()
    for x in range(5):
        p[x, 0] = BAR_FILL_HI
    return im


# ---------- 버튼 (200x20, nine-slice) — 도감 칸 모양 ----------
# ⚠ 서버에 접속해 있는 동안은 설정 화면 등 "모든 버튼"이 이 모양이 돼요.
def make_button(fill, line):
    im = Image.new("RGBA", (200, 20), fill)
    p = im.load()
    for x in range(200):
        p[x, 0] = line
        p[x, 19] = line
    for y in range(20):
        p[0, y] = line
        p[199, y] = line
    return im


BUTTON_MCMETA = '{\n  "gui": {\n    "scaling": { "type": "nine_slice", "width": 200, "height": 20, "border": 1 }\n  }\n}\n'

make_check().save(os.path.join(SC, "check.png"))
make_digits().save(os.path.join(SC, "digits.png"))
make_arrow(False, True).save(os.path.join(SC, "arrow_left.png"))
make_arrow(True, True).save(os.path.join(SC, "arrow_right.png"))
make_arrow(False, False).save(os.path.join(SC, "arrow_left_off.png"))
make_arrow(True, False).save(os.path.join(SC, "arrow_right_off.png"))
make_bar_track().save(os.path.join(SC, "bar_track.png"))
make_bar_fill().save(os.path.join(SC, "bar_fill.png"))

for name, fill, line in (("button", SLOT, KHAKI),
                         ("button_highlighted", SLOT_HI, KHAKI_HI),
                         ("button_disabled", SLOT, c("#3a3730"))):
    make_button(fill, line).save(os.path.join(BTN, name + ".png"))
    with open(os.path.join(BTN, name + ".png.mcmeta"), "w", encoding="utf-8") as f:
        f.write(BUTTON_MCMETA)

print("pack_template 완료:", OUT)
