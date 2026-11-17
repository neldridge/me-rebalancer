#!/usr/bin/env python3
"""Offline resource and binary contract checks. Does not compile or launch the mod."""
import argparse
import contextlib
import hashlib
import io
import json
from pathlib import Path
import tomllib
import zipfile
from inspect_class import inspect

parser = argparse.ArgumentParser()
parser.add_argument('instance', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
instance = json.loads((args.instance / 'instance.json').read_text())
assert instance['mcVersion'] == '26.1.2', instance['mcVersion']
assert instance['modLoader'] == 'neoforge-26.1.2.126', instance['modLoader']
jar = args.instance / 'mods/appliedenergistics2-26.1.13-beta.jar'
contracts = {
 'appeng.me.storage.NetworkStorage': [
  'field 0x12 priorityInventory Ljava/util/NavigableMap;',
  'field 0x2 mountsInUse Z',
  'method 0x2 flushQueuedOperations ()V',
  'method 0x2 isQueuedForRemoval (Lappeng/api/storage/MEStorage;)Z'],
 'appeng.me.storage.DelegatingMEInventory': ['getDelegate ()Lappeng/api/storage/MEStorage;'],
 'appeng.parts.storagebus.StorageBusPart': ['getInternalHandler ()Lappeng/api/storage/MEStorage;'],
 'appeng.api.implementations.blockentities.IChestOrDrive': ['getCellInventory (I)Lappeng/api/storage/MEStorage;'],
 'appeng.api.storage.MEStorage': [
  'insert (Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J',
  'extract (Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J',
  'getAvailableStacks ()Lappeng/api/stacks/KeyCounter;'],
 'appeng.api.stacks.AEItemKey': [
  'field 0x19 CODEC Lcom/mojang/serialization/Codec;',
  'addDrops (JLjava/util/List;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V'],
 'appeng.api.networking.ticking.TickingRequest': ['<init> (IIZ)V'],
 'appeng.api.networking.energy.IEnergySource': ['extractAEPower (DLappeng/api/config/Actionable;Lappeng/api/config/PowerMultiplier;)D'],
 'appeng.blockentity.grid.AENetworkedBlockEntity': ['getMainNode ()Lappeng/api/networking/IManagedGridNode;'],
}
checks = 0
for name, members in contracts.items():
    result = io.StringIO()
    with contextlib.redirect_stdout(result): inspect(jar, name)
    for member in members:
        assert member in result.getvalue(), f'Missing binary contract: {name}: {member}'
        checks += 1
for path in (root / 'src/main/resources').rglob('*.json'):
    json.loads(path.read_text())
    checks += 1
metadata = tomllib.loads((root / 'src/main/resources/META-INF/neoforge.mods.toml').read_text())
assert any(d['modId'] == 'ae2' and d['versionRange'] == '[26.1.13-beta]' for d in metadata['dependencies']['me_rebalancer'])
checks += 1
with zipfile.ZipFile(jar) as z:
    ae_metadata = tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())
    assert ae_metadata['mods'][0]['version'] == '26.1.13-beta'
    language = json.loads(z.read('assets/ae2/lang/en_us.json'))
    recipe = json.loads((root / 'src/main/resources/data/me_rebalancer/recipe/me_rebalancer.json').read_text())
    for value in recipe['key'].values():
        if value.startswith('ae2:'):
            name = value.split(':')[1]
            assert f'item.ae2.{name}' in language or f'block.ae2.{name}' in language, value
            checks += 1
print(f'PASS: {checks} offline resource and installed-AE2 binary contract checks')
print(f'AE2 SHA-256: {hashlib.sha256(jar.read_bytes()).hexdigest()}')
print('Java compilation, unit tests, Mixin application, and gameplay are NOT checked by this script.')
