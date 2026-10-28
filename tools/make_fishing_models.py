"""Import the supplied winged fishing rods without changing geometry or PNGs."""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
MODELS = HERE / 'models'
REFERENCES = MODELS / 'references' / 'fishing_rods'
TIERS = (0, 5, 10, 15, 20)


def source_path(tier, cast=False):
    return REFERENCES / (f'fishing_rod_wing_progress_lv{tier:02}' + ('_cast' if cast else ''))


def model_data(tier, cast=False):
    data = json.loads(source_path(tier, cast).with_suffix('.json').read_text(encoding='utf-8'))
    name = f'village_fishing_rod_{tier}' + ('_cast' if cast else '')
    data['textures'] = {key: f'servercore:block/{name}' for key in data['textures']}
    return data


def generate():
    textures = MODELS / 'textures'
    textures.mkdir(parents=True, exist_ok=True)
    for tier in TIERS:
        for cast in (False, True):
            name = f'village_fishing_rod_{tier}' + ('_cast' if cast else '')
            data = model_data(tier, cast)
            for element in data['elements']:
                assert all(-16 <= a <= b <= 32 for a, b in zip(element['from'], element['to']))
                if 'rotation' in element:
                    assert element['rotation']['angle'] in (-45, -22.5, 0, 22.5, 45)
            (MODELS / (name + '.json')).write_text(json.dumps(data, separators=(',', ':')), encoding='utf-8')
            (textures / (name + '.png')).write_bytes(source_path(tier, cast).with_suffix('.png').read_bytes())
            print(name, len(data['elements']), 'imported UV parts')
    print('Imported five winged rod tiers and five cast variants; original PNG bytes preserved.')


if __name__ == '__main__':
    generate()
