"""Import the supplied watering-can models, preserving UVs and PNG bytes."""
import json
from pathlib import Path

MODELS = Path(__file__).resolve().parent / 'models'
REFERENCES = MODELS / 'references' / 'watering_cans'
DESIGNS = {0: 'iron', 5: 'gilded_phoenix', 10: 'demons_blood',
           15: 'viridian', 20: 'winterthorn'}


def source_paths(tier, full=False):
    state = 'full' if full else 'empty'
    if tier == 0:
        folder = REFERENCES / 'vanilla'
        return folder / ('water_bucket.json' if full else 'bucket.json'), folder / ('water_bucket.png' if full else 'bucket.png')
    name = f'{DESIGNS[tier]}_watering_can_{state}'
    return REFERENCES / (name + '.json'), REFERENCES / (name + '.png')


def resolve_model(path):
    model = json.loads(path.read_text(encoding='utf-8'))
    parent = model.pop('parent', None)
    if parent:
        base = resolve_model(path.parent / (parent.rsplit('/', 1)[-1] + '.json'))
        textures = base.get('textures', {}) | model.get('textures', {})
        base.update(model)
        base['textures'] = textures
        return base
    return model


def model_data(tier, full=False):
    model = resolve_model(source_paths(tier, full)[0])
    suffix = '_full' if full else ''
    texture = f'servercore:block/village_watering_can_{tier}{suffix}'
    model['textures'] = {key: texture for key in model['textures']}
    return model


def generate():
    textures = MODELS / 'textures'
    textures.mkdir(parents=True, exist_ok=True)
    for tier in DESIGNS:
        for full in (False, True):
            name = f'village_watering_can_{tier}' + ('_full' if full else '')
            model = model_data(tier, full)
            (MODELS / (name + '.json')).write_text(json.dumps(model, separators=(',', ':')), encoding='utf-8')
            (textures / (name + '.png')).write_bytes(source_paths(tier, full)[1].read_bytes())
            print(name, len(model['elements']), 'imported UV parts')


if __name__ == '__main__':
    generate()
