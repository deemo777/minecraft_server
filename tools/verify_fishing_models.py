"""Verify original winged fishing assets and every level/cast selection."""
import io
import json
from pathlib import Path
import zipfile
from PIL import Image

from make_fishing_models import MODELS, REFERENCES


def verify(pack_path):
    tiers = (0, 5, 10, 15, 20)
    with zipfile.ZipFile(pack_path) as pack:
        assert pack.testzip() is None, 'ZIP CRC failure'
        definition = json.loads(pack.read('assets/servercore/items/village_fishing_rod.json'))['model']
        assert definition['type'] == 'minecraft:range_dispatch'
        assert definition['property'] == 'minecraft:custom_model_data' and definition['index'] == 0
        assert [e['threshold'] for e in definition['entries']] == [5, 10, 15, 20]
        for level in range(21):
            tier = max(value for value in tiers if value <= level)
            choice = definition['fallback']
            for entry in definition['entries']:
                if level >= entry['threshold']:
                    choice = entry['model']
            assert choice['type'] == 'minecraft:condition'
            assert choice['property'] == 'minecraft:fishing_rod/cast'
            for cast in (False, True):
                name = f'village_fishing_rod_{tier}' + ('_cast' if cast else '')
                assert choice['on_true' if cast else 'on_false'] == {
                    'type': 'minecraft:model', 'model': f'servercore:item/{name}'}
        for tier in tiers:
            for cast in (False, True):
                suffix = '_cast' if cast else ''
                source = REFERENCES / f'fishing_rod_wing_progress_lv{tier:02}{suffix}'
                original = json.loads(source.with_suffix('.json').read_text(encoding='utf-8'))
                name = f'village_fishing_rod_{tier}{suffix}'
                model = json.loads(pack.read(f'assets/servercore/models/item/{name}.json'))
                assert set(model) == set(original), name
                for key, value in original.items():
                    if key != 'textures':
                        assert model[key] == value, (name, key)
                assert set(model['textures']) == set(original['textures'])
                assert set(model['textures'].values()) == {f'servercore:block/{name}'}
                png = pack.read(f'assets/servercore/textures/block/{name}.png')
                assert png == source.with_suffix('.png').read_bytes(), f'{name}: PNG bytes changed'
                with Image.open(io.BytesIO(png)) as image:
                    image.verify()
                assert model == json.loads((MODELS / (name + '.json')).read_text(encoding='utf-8'))
                for element in model['elements']:
                    assert all(-16 <= a <= b <= 32 for a, b in zip(element['from'], element['to']))
                    if 'rotation' in element:
                        assert element['rotation']['angle'] in (-45, -22.5, 0, 22.5, 45)
                    for face in element.get('faces', {}).values():
                        assert face['texture'].removeprefix('#') in model['textures']
    print('PASS: 42 level/cast selections; 10 original models, UVs, displays and byte-identical PNGs; valid bounds, rotations and texture keys.')


if __name__ == '__main__':
    verify(Path(__file__).resolve().parent / 'build/servercore_pack.zip')
