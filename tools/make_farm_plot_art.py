"""비옥한 농지 아이템 모델과 16×16 픽셀 텍스처를 리소스팩에 추가한다."""

import json
import os

from PIL import Image, ImageDraw


def install_farm_plot_assets(pack_dir: str) -> None:
    root = os.path.join(pack_dir, "assets", "servercore")
    textures = os.path.join(root, "textures", "item")
    models = os.path.join(root, "models", "item")
    items = os.path.join(root, "items")
    for directory in (textures, models, items):
        os.makedirs(directory, exist_ok=True)

    image = Image.new("RGBA", (16, 16), (58, 35, 24, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, 14, 14), fill=(119, 74, 42, 255))
    # 흙 고랑과 작은 돌을 반복하지 않게 배치해 땅 질감을 남긴다.
    for x, y, color in (
        (2, 3, (154, 101, 56, 255)), (6, 3, (91, 55, 34, 255)),
        (11, 3, (145, 91, 48, 255)), (3, 6, (86, 51, 32, 255)),
        (12, 7, (158, 101, 53, 255)), (2, 11, (91, 55, 34, 255)),
        (11, 12, (87, 52, 32, 255)), (5, 13, (149, 95, 52, 255)),
    ):
        draw.point((x, y), fill=color)
    for x in range(2, 14):
        if x not in (6, 7, 8, 9):
            draw.point((x, 9), fill=(87, 52, 32, 255))
    # 중앙에 밝은 새싹을 배치하고 양쪽 잎은 서로 다른 높이로 펼친다.
    for point in ((7, 6), (7, 7), (8, 7), (8, 8), (8, 9), (8, 10)):
        draw.point(point, fill=(49, 111, 45, 255))
    for point in ((5, 7), (6, 6), (6, 7), (7, 5), (8, 5),
                  (9, 6), (10, 6), (9, 7), (10, 7)):
        draw.point(point, fill=(91, 171, 62, 255))
    for point in ((6, 6), (7, 5), (8, 5), (9, 6)):
        draw.point(point, fill=(166, 213, 94, 255))
    image.save(os.path.join(textures, "farm_plot.png"))

    model = {"parent": "minecraft:item/generated", "textures": {"layer0": "servercore:item/farm_plot"}}
    item = {"model": {"type": "minecraft:model", "model": "servercore:item/farm_plot"}}
    for path, data in ((os.path.join(models, "farm_plot.json"), model),
                       (os.path.join(items, "farm_plot.json"), item)):
        with open(path, "w", encoding="utf-8") as stream:
            json.dump(data, stream, ensure_ascii=False, indent=2)
