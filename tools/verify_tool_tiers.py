"""Verify shifted design unlocks for all five tools, including cast and water states."""
import json
from pathlib import Path
import zipfile


def verify(path):
    count=0
    with zipfile.ZipFile(path) as pack:
        assert pack.testzip() is None
        names=set(pack.namelist())
        for tool in ('pickaxe','axe','hoe','fishing_rod','watering_can'):
            definition=json.loads(pack.read(f'assets/servercore/items/village_{tool}.json'))['model']
            assert [entry['threshold'] for entry in definition['entries']]==[10,15,20,25],tool
            for level in range(26):
                source=0 if level<10 else 5 if level<15 else 10 if level<20 else 15 if level<25 else 20
                choice=definition['fallback']
                for entry in definition['entries']:
                    if level>=entry['threshold']: choice=entry['model']
                for state in (False,True) if tool in ('fishing_rod','watering_can') else (False,):
                    selected=choice['on_true' if state else 'on_false'] if choice['type']=='minecraft:condition' else choice
                    suffix=('_cast' if tool=='fishing_rod' else '_full') if state else ''
                    expected=f'servercore:item/village_{tool}_{source}{suffix}'
                    assert selected=={'type':'minecraft:model','model':expected},(tool,level,state)
                    assert f'assets/servercore/models/item/village_{tool}_{source}{suffix}.json' in names
                    count+=1
    print(f'PASS: {count} tool level/state selections; +0/+5 share base, old +5/+10/+15/+20 unlock at +10/+15/+20/+25.')


if __name__=='__main__':
    verify(Path(__file__).resolve().parent/'build/servercore_pack.zip')
