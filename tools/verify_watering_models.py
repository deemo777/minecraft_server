"""Verify both water states at every enhancement level in the built pack."""
import io
import json
from pathlib import Path
import zipfile
from PIL import Image

from make_watering_models import MODELS, REFERENCES, source_paths


def verify(pack_path):
    expected = {0: 'iron', 5: 'gilded_phoenix', 10: 'demons_blood',
                15: 'viridian', 20: 'winterthorn'}
    with zipfile.ZipFile(pack_path) as pack:
        assert pack.testzip() is None, 'ZIP CRC failure'
        definition = json.loads(pack.read('assets/servercore/items/village_watering_can.json'))['model']
        assert definition['type'] == 'minecraft:range_dispatch'
        assert definition['property'] == 'minecraft:custom_model_data' and definition['index'] == 0
        assert [entry['threshold'] for entry in definition['entries']] == [5, 10, 15, 20]
        for level in range(21):
            tier = max(value for value in expected if value <= level)
            choice = definition['fallback']
            for entry in definition['entries']:
                if level >= entry['threshold']:
                    choice = entry['model']
            assert choice['type'] == 'minecraft:condition'
            assert choice['property'] == 'minecraft:custom_model_data' and choice['index'] == 0
            for full in (False, True):
                name = f'village_watering_can_{tier}' + ('_full' if full else '')
                assert choice['on_true' if full else 'on_false'] == {
                    'type': 'minecraft:model', 'model': f'servercore:item/{name}'}
        for tier, design in expected.items():
            for full in (False, True):
                state = 'full' if full else 'empty'
                name = f'village_watering_can_{tier}' + ('_full' if full else '')
                model = json.loads(pack.read(f'assets/servercore/models/item/{name}.json'))
                if tier == 0:
                    original = json.loads((REFERENCES / 'vanilla/3d_watering_can.json').read_text(encoding='utf-8'))
                    texture = REFERENCES / 'vanilla' / ('water_bucket.png' if full else 'bucket.png')
                else:
                    source = REFERENCES / f'{design}_watering_can_{state}'
                    original = json.loads(source.with_suffix('.json').read_text(encoding='utf-8'))
                    texture = source.with_suffix('.png')
                for key, value in original.items():
                    if key != 'textures':
                        assert model[key] == value, (name, key)
                assert 'parent' not in model, name
                assert set(model['textures'].values()) == {f'servercore:block/{name}'}
                data = pack.read(f'assets/servercore/textures/block/{name}.png')
                assert data == texture.read_bytes(), f'{name}: changed PNG bytes'
                with Image.open(io.BytesIO(data)) as image:
                    image.verify()
                assert model == json.loads((MODELS / (name + '.json')).read_text(encoding='utf-8'))
                for element in model['elements']:
                    assert all(-16 <= a <= b <= 32 for a, b in zip(element['from'], element['to']))
                    if 'rotation' in element:
                        assert element['rotation']['angle'] in (-45, -22.5, 0, 22.5, 45)
                    for face in element['faces'].values():
                        assert face['texture'].removeprefix('#') in model['textures']
        for tier in expected:
            assert source_paths(tier, False)[1].read_bytes() != source_paths(tier, True)[1].read_bytes()
    print('PASS: 42 level/state selections; 10 original models, UVs, original displays and byte-identical PNGs; valid planes, rotations and references.')


if __name__ == '__main__':
    verify(Path(__file__).resolve().parent / 'build/servercore_pack.zip')
