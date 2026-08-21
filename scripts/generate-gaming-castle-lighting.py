#!/usr/bin/env python3
"""Generate the dense Gaming Castle spawn-proof lighting datapack for MC 1.21.1."""

from pathlib import Path
import json
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "build" / "gaming_castle_spawnproof_lighting_v3"
OUT = ROOT / "build" / "gaming_castle_spawnproof_lighting_v3.zip"
FUNCTIONS = BUILD / "data" / "gclight" / "function"


def write(name: str, lines: list[str]) -> None:
    (FUNCTIONS / f"{name}.mcfunction").write_text("\n".join(lines) + "\n", encoding="utf-8")


def grid(x1: int, x2: int, z1: int, z2: int, y_values: list[int], step: int = 8) -> list[str]:
    xs = list(range(x1, x2 + 1, step))
    zs = list(range(z1, z2 + 1, step))
    if xs[-1] != x2:
        xs.append(x2)
    if zs[-1] != z2:
        zs.append(z2)
    return [
        f"setblock {x} {y} {z} minecraft:light[level=15] keep"
        for y in y_values
        for x in xs
        for z in zs
    ]


def points(values: list[tuple[int, int, int]]) -> list[str]:
    return [f"setblock {x} {y} {z} minecraft:light[level=15] keep" for x, y, z in values]


def remove_region(label: str, x1: int, x2: int, y1: int, y2: int, z1: int, z2: int) -> list[str]:
    lines = [f'tellraw @s {{"text":"Removing {label} invisible light blocks...","color":"yellow"}}']
    # Width 3 keeps the largest cleanup fill below vanilla's 32,768-block limit.
    x = x1
    while x <= x2:
        xe = min(x + 2, x2)
        lines.append(f"fill {x} {y1} {z1} {xe} {y2} {z2} air replace minecraft:light")
        x = xe + 1
    return lines


def build() -> None:
    if BUILD.exists():
        shutil.rmtree(BUILD)
    FUNCTIONS.mkdir(parents=True)
    (BUILD / "data" / "minecraft" / "tags" / "function").mkdir(parents=True)

    (BUILD / "pack.mcmeta").write_text(
        json.dumps({"pack": {"pack_format": 48, "description": "Gaming Castle dense spawn-proof lighting V3"}}, indent=2),
        encoding="utf-8",
    )
    (BUILD / "data" / "minecraft" / "tags" / "function" / "load.json").write_text(
        json.dumps({"values": ["gclight:load"]}, indent=2), encoding="utf-8"
    )

    write("load", [
        'tellraw @a [{"text":"[Gaming Castle Lighting] ","color":"gold"},{"text":"Dense spawn-proof lighting pack loaded. Run ","color":"gray"},{"text":"/function gclight:apply_all","color":"yellow"},{"text":" once.","color":"gray"}]'
    ])
    write("ping", ['tellraw @s {"text":"[Gaming Castle Lighting] V3 is loaded and ready.","color":"green"}'])
    write("apply_all", [
        'tellraw @a {"text":"[Gaming Castle Lighting] Starting dense anti-mob lighting pass...","color":"yellow"}',
        "function gclight:hub",
        "schedule function gclight:market 20t replace",
        "schedule function gclight:duels 40t replace",
        "schedule function gclight:staff 60t replace",
        "schedule function gclight:survival 80t replace",
        "schedule function gclight:finish 100t replace",
    ])

    hub = ['tellraw @a {"text":"[Gaming Castle Lighting] 1/5 Lighting the Gaming Castle hub...","color":"light_purple"}']
    hub += grid(-263, -27, -68, 183, [72], 8)
    hub += grid(-205, -85, -25, 115, [78, 86], 8)
    hub += grid(-205, -85, -62, 25, [80, 90, 100], 8)
    hub += grid(-180, -110, 120, 165, [74, 82, 92], 8)
    for cx, cz in [(-218, 7), (-78, 7), (-218, 96), (-78, 96), (-145, 70)]:
        hub += points([(cx + dx, y, cz + dz) for dx in (-6, 0, 6) for dz in (-4, 0, 4) for y in (73, 80)])
    write("hub", hub)

    market = ['tellraw @a {"text":"[Gaming Castle Lighting] 2/5 Lighting Market District...","color":"gold"}']
    market += grid(1442, 1558, -58, 58, [72], 7)
    market += grid(1458, 1542, -44, 24, [76, 83, 90], 7)
    market += grid(1480, 1520, 20, 50, [74, 82], 6)
    market += points([(x, y, z) for x in range(1492, 1509, 4) for z in range(36, 47, 4) for y in (73, 80)])
    write("market", market)

    duels = ['tellraw @a {"text":"[Gaming Castle Lighting] 3/5 Lighting Duel Arena and lobby...","color":"red"}']
    duels += grid(-1562, -1438, -62, 62, [72], 7)
    duels += grid(-1552, -1448, -52, 38, [76, 83, 90], 7)
    duels += grid(-1525, -1475, 28, 52, [74, 82], 6)
    duels += points([(x, y, z) for x in range(-1508, -1491, 4) for z in range(36, 47, 4) for y in (73, 80)])
    write("duels", duels)

    staff = ['tellraw @a {"text":"[Gaming Castle Lighting] 4/5 Lighting Staff Lounge and return corridor...","color":"aqua"}']
    staff += grid(-50, 50, -1550, -1446, [72], 7)
    staff += grid(-42, 42, -1544, -1470, [78, 85, 92], 7)
    staff += grid(-14, 14, -1475, -1455, [74, 81, 88], 5)
    staff += points([(x, y, z) for x in range(-8, 9, 4) for z in range(-1464, -1455, 4) for y in (73, 80)])
    write("staff", staff)

    survival = ['tellraw @a {"text":"[Gaming Castle Lighting] 5/5 Lighting Survival landing and return pad...","color":"green"}']
    survival += grid(-28, 28, 1468, 1540, [72, 80], 7)
    survival += grid(-20, 20, 1480, 1536, [74], 5)
    for cx, cz in [(0, 1500), (0, 1522)]:
        survival += points([(cx + dx, 73, cz + dz) for dx in (-8, -4, 0, 4, 8) for dz in (-8, -4, 0, 4, 8)])
    write("survival", survival)

    write("finish", [
        'tellraw @a [{"text":"[Gaming Castle Lighting] ","color":"gold"},{"text":"Dense lighting complete. Hub, Market, Duels, Staff and Survival teleport areas are spawn-proofed with level-15 light blocks.","color":"green"}]'
    ])

    write("remove_hub", remove_region("hub", -265, -25, 68, 105, -70, 185))
    write("remove_market", remove_region("market", 1440, 1560, 70, 100, -60, 60))
    write("remove_duels", remove_region("duels", -1565, -1435, 70, 96, -65, 65))
    write("remove_staff", remove_region("staff", -52, 52, 70, 100, -1552, -1444))
    write("remove_survival", remove_region("survival landing", -30, 30, 70, 85, 1465, 1545))
    write("remove_all", [
        "function gclight:remove_hub",
        "function gclight:remove_market",
        "function gclight:remove_duels",
        "function gclight:remove_staff",
        "function gclight:remove_survival",
        'tellraw @s {"text":"Gaming Castle invisible lighting removed.","color":"green"}',
    ])

    OUT.parent.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        OUT.unlink()
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as archive:
        for file in BUILD.rglob("*"):
            if file.is_file():
                archive.write(file, file.relative_to(BUILD))
    print(OUT)


if __name__ == "__main__":
    build()
