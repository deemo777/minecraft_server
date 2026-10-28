"""Check every tool/state against its source with only the requested hand offset."""
import json
import math
from pathlib import Path
import zipfile

from make_tool_models import PICKAXE_DESIGNS, AXE_DESIGNS, SCYTHE_DESIGNS
from make_fishing_models import source_path as fishing_source
from make_watering_models import source_paths as watering_source, resolve_model


def verify(pack_path):
    models = Path(__file__).resolve().parent / 'models'
    cases = []
    for tool, folder, designs in (
            ('pickaxe', 'pickaxes', PICKAXE_DESIGNS), ('axe', 'axes', AXE_DESIGNS),
            ('hoe', 'scythes', SCYTHE_DESIGNS)):
        for tier, design in designs.items():
            source = models / 'references' / folder / design
            cases.append((f'village_{tool}_{tier}', resolve_model(source.with_suffix('.json')), source.with_suffix('.png')))
    for tier in (0, 5, 10, 15, 20):
        for alternate in (False, True):
            source = fishing_source(tier, alternate)
            cases.append((f'village_fishing_rod_{tier}' + ('_cast' if alternate else ''),
                          resolve_model(source.with_suffix('.json')), source.with_suffix('.png')))
            source, texture = watering_source(tier, alternate)
            cases.append((f'village_watering_can_{tier}' + ('_full' if alternate else ''), resolve_model(source), texture))
    assert len(cases) == 35
    with zipfile.ZipFile(pack_path) as pack:
        assert pack.testzip() is None
        for name, source, texture in cases:
            actual = json.loads(pack.read(f'assets/servercore/models/item/{name}.json'))
            assert set(actual) == set(source), name
            for key, value in source.items():
                if key not in ('textures', 'display'):
                    assert actual[key] == value, (name, key)
            assert set(actual['display']) == set(source['display']), name
            for context, pose in source['display'].items():
                changed = actual['display'][context]
                if context not in ('firstperson_righthand', 'firstperson_lefthand'):
                    assert changed == pose, (name, context)
                    continue
                assert {k: v for k, v in changed.items() if k != 'translation'} == {
                    k: v for k, v in pose.items() if k != 'translation'}, (name, context)
                old = pose.get('translation', [0, 0, 0])
                expected = [old[0] + 3, old[1] - 4, old[2]]
                assert all(math.isclose(a, b, abs_tol=1e-8) for a, b in zip(changed['translation'], expected)), (name, context)
            assert pack.read(f'assets/servercore/textures/block/{name}.png') == texture.read_bytes(), name
    print('PASS: all 35 tool/state models, 70 first-person poses; X +3, Y -4; geometry, rotation, scale, other views and PNGs preserved.')


if __name__ == '__main__':
    verify(Path(__file__).resolve().parent / 'build/servercore_pack.zip')
