"""Check numeric drop parameters and optionally coverage against the local vanilla client."""
import argparse
import json
import zipfile
from pathlib import Path
import yaml

parser=argparse.ArgumentParser()
parser.add_argument('--jar',type=Path)
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
path=root/'src/main/resources/numeric-drops.yml'
text=path.read_text('utf8');rules=yaml.safe_load(text)
assert 'enchantment' not in text
count=0
for block,rule in rules.items():
    assert block==block.upper()
    for material,spec in rule.get('bonuses',{}).items():
        assert spec['formula'] in {'chance','ore_drops','uniform_bonus_count','binomial_with_bonus_count'}
        if spec['formula']=='chance':assert spec['chances'] and all(0<=c<=1 for c in spec['chances'])
        if spec['formula']=='binomial_with_bonus_count':assert 0<=spec['probability']<=1
        count+=1
if args.jar:
    with zipfile.ZipFile(args.jar) as jar:
        expected={name.split('/')[-1][:-5].upper() for name in jar.namelist()
                  if name.startswith('data/minecraft/loot_table/blocks/') and name.endswith('.json')
                  and b'minecraft:fortune' in jar.read(name)}
        assert expected<=rules.keys(),expected-rules.keys()
    print(f'PASS: coverage for {len(expected)} vanilla bonus-sensitive block types.')
for path in (root/'src/main/java/com/htk/core/tools').glob('*.java'):
    source=path.read_text('utf8')
    assert '.addEnchant(' not in source and '.addUnsafeEnchantment(' not in source,path
print(f'PASS: {len(rules)} numeric block rules, {count} bonus formulas; no enchanted items/proxies.')
