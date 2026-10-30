"""Check shipped weapon UVs, original loops, frame bounds, alpha and visible motion."""
import io
import json
import math
from pathlib import Path
import zipfile

from PIL import Image, ImageChops, ImageDraw

from animate_weapon_textures import FRAMES, ORIGINAL, SKINS, texture_paths

HERE = Path(__file__).resolve().parent


def verify(path):
    original_files = generated_textures = visible_skins = 0
    with zipfile.ZipFile(path) as pack:
        for model_path in sorted((HERE / 'weapons').glob('*/*.json')):
            category, skin = model_path.parent.name, model_path.stem
            base = f'assets/servercore/textures/item/weapons/{category}/'
            model_bytes = pack.read(f'assets/servercore/models/item/weapons/{category}/{skin}.json')
            assert model_bytes == model_path.read_bytes(), f'Model/UV changed: {skin}'
            model = json.loads(model_bytes)
            visible = False
            for texture_path in texture_paths(model_path):
                name = base + texture_path.name
                original = Image.open(texture_path).convert('RGBA')
                if (category, skin) in ORIGINAL:
                    assert pack.read(name) == texture_path.read_bytes(), name
                    sidecar = texture_path.with_name(texture_path.name + '.mcmeta')
                    if sidecar.exists():
                        assert pack.read(name + '.mcmeta') == sidecar.read_bytes(), name
                    continue
                image = Image.open(io.BytesIO(pack.read(name))).convert('RGBA')
                animation = json.loads(pack.read(name + '.mcmeta'))['animation']
                w, h = animation['width'], animation['height']
                assert (w, h) == original.size and image.size == (w, h * FRAMES), name
                assert animation['frametime'] > 0 and animation['interpolate'] is True
                first = image.crop((0, 0, w, h))
                assert first.tobytes() == original.tobytes(), f'Frame zero changed: {name}'
                # Restrict the check to actual visible model UVs, excluding particle/_e alone.
                slots = {key for key, ref in model['textures'].items()
                         if ref.rsplit('/', 1)[-1] + '.png' == texture_path.name}
                used = Image.new('L', (w, h)); draw = ImageDraw.Draw(used)
                for element in model['elements']:
                    for face in element['faces'].values():
                        if face['texture'].lstrip('#') in slots:
                            u0, v0, u1, v1 = face['uv']
                            x0, x1 = sorted((u0 * w / 16, u1 * w / 16))
                            y0, y1 = sorted((v0 * h / 16, v1 * h / 16))
                            if x1 > x0 and y1 > y0:
                                draw.rectangle((math.floor(x0), math.floor(y0),
                                                max(math.floor(x0), math.ceil(x1)-1),
                                                max(math.floor(y0), math.ceil(y1)-1)), fill=255)
                changed = Image.new('L', (w, h))
                alpha = original.getchannel('A')
                for index in range(FRAMES):
                    frame = image.crop((0, index*h, w, (index+1)*h))
                    assert frame.getchannel('A').tobytes() == alpha.tobytes(), name
                    diff = ImageChops.difference(frame.convert('RGB'), original.convert('RGB'))
                    for channel in diff.split():
                        changed = ImageChops.lighter(changed, channel)
                actual = ImageChops.multiply(ImageChops.multiply(changed, used), alpha)
                if actual.getbbox():
                    visible = True
                generated_textures += 1
            if (category, skin) not in ORIGINAL:
                assert visible, f'No animated pixels on visible weapon faces: {skin}'
                visible_skins += 1
        # Validate frame indexes for all shipped weapon animation sidecars, including originals.
        for name in pack.namelist():
            if '/weapons/' not in name or not name.endswith('.png.mcmeta'):
                continue
            relative = name.split('/weapons/', 1)[1]
            original_sidecar = HERE / 'weapons' / relative
            if original_sidecar.exists():
                assert pack.read(name) == original_sidecar.read_bytes(), name
                original_png = original_sidecar.with_name(original_sidecar.name[:-7])
                assert pack.read(name[:-7]) == original_png.read_bytes(), name
                original_files += 1
            animation = json.loads(pack.read(name))['animation']
            image = Image.open(io.BytesIO(pack.read(name[:-7])))
            w = animation.get('width', image.width)
            h = animation.get('height', min(image.size))
            assert image.width % w == 0 and image.height % h == 0, name
            count = image.width // w * (image.height // h)
            for frame in animation.get('frames', range(count)):
                index = frame['index'] if isinstance(frame, dict) else frame
                assert 0 <= index < count, (name, index, count)
                if isinstance(frame, dict):
                    assert frame.get('time', 1) > 0, name
        assert original_files == 10 and visible_skins == 36
    print(f'PASS: 42 unchanged models, {original_files} original loops byte-identical; '
          f'{visible_skins} themed skins / {generated_textures} textures with valid '
          'frames, original frame zero, preserved alpha and visible UV motion.')


if __name__ == '__main__':
    verify(HERE / 'build/servercore_pack.zip')
