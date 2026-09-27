#!/usr/bin/env python3
"""Convert DeluxeMenus cosmetic menus into a CosmeticPlugin catalog.yml.

Usage:
  python tools/dm2catalog.py --out deploy/sky/catalog.yml [--existing deploy/sky/catalog.yml] [--default-price 999999]
      "<category_id>=<Display Name>=<file1,file2,...>[=ICON_MATERIAL]" ...

Example (the Sky server):
  python tools/dm2catalog.py --out deploy/sky/catalog.yml --existing deploy/sky/catalog.yml \
      "name_colours=&a&lName Colours=reference/deluxemenus/namecolours.yml=NAME_TAG" \
      "disguises=&b&lDisguises=reference/deluxemenus/disguises.yml,reference/deluxemenus/disguises2.yml,reference/deluxemenus/disguises3.yml=CREEPER_HEAD" \
      "tags_normal=&e&lColour Tags=reference/deluxemenus/colourtags_normal.yml=NAME_TAG" ...

Rules:
  - skips fillers, nav buttons, player heads and the priority>=2 "[No Access]" twins
  - permissions = every `has permission` node + every %luckperms_(has|inherits)_permission_<node>% placeholder
  - `[console] ct give ...` and other console lines become purchase-commands (vote-point deductions are dropped)
  - &-codes become MiniMessage; "[No Access]" and "Cost: N" lore lines are dropped
  - prices are carried over by item id from --existing, otherwise --default-price with a TODO comment
  - hdb-N / nexo-x icons become PLAYER_HEAD with a TODO comment; basehead-/texture- become head:<base64>
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

import yaml

LEGACY_COLOURS = {
    "0": "black", "1": "dark_blue", "2": "dark_green", "3": "dark_aqua", "4": "dark_red", "5": "dark_purple",
    "6": "gold", "7": "gray", "8": "dark_gray", "9": "blue", "a": "green", "b": "aqua", "c": "red",
    "d": "light_purple", "e": "yellow", "f": "white",
}
LEGACY_FORMATS = {"l": "bold", "o": "italic", "n": "underlined", "m": "strikethrough", "k": "obfuscated"}
FILLER_MATERIALS = {"compass", "iron_door", "barrier"}
DROP_COMMAND_PREFIXES = ("av user", "[refresh]", "[message]", "[close]", "[sound]", "[openguimenu]", "[connect]",
                         "[json]", "[broadcast]", "[minimessage]", "[chat]")
LP_PLACEHOLDER = re.compile(r"%luckperms_(?:has|inherits)_permission_([^%]+)%")
LEGACY_CODE = re.compile(r"(?:&|§)(#[0-9a-fA-F]{6}|[0-9a-fk-orA-FK-OR])")
PLACEHOLDER_PRICE = 999999   # must match CosmeticItem.PLACEHOLDER_PRICE


class MergingLoader(yaml.SafeLoader):
    """DeluxeMenus files sometimes repeat a key (two `lore:` blocks). Merge lists instead of keeping only the last."""


def _construct_mapping(loader: MergingLoader, node: yaml.MappingNode, deep: bool = False):
    mapping: dict = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=True)
        value = loader.construct_object(value_node, deep=True)
        if key in mapping and isinstance(mapping[key], list) and isinstance(value, list):
            mapping[key] = mapping[key] + value
        else:
            mapping[key] = value
    return mapping


MergingLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, _construct_mapping)


def legacy_to_minimessage(text: str) -> str:
    """&a&lHi &7there -> <green><bold>Hi <reset><gray>there (a colour code resets formatting, like the client does)."""
    out: list[str] = []
    pos = 0
    open_tags = False
    for m in LEGACY_CODE.finditer(text):
        out.append(text[pos:m.start()])
        code = m.group(1).lower()
        if code.startswith("#"):
            out.append(("<reset>" if open_tags else "") + f"<{code}>")
            open_tags = True
        elif code in LEGACY_COLOURS:
            out.append(("<reset>" if open_tags else "") + f"<{LEGACY_COLOURS[code]}>")
            open_tags = True
        elif code in LEGACY_FORMATS:
            out.append(f"<{LEGACY_FORMATS[code]}>")
            open_tags = True
        elif code == "r":
            out.append("<reset>")
            open_tags = False
        pos = m.end()
    out.append(text[pos:])
    return "".join(out)


def strip_codes(text: str) -> str:
    return LEGACY_CODE.sub("", text)


def slug(text: str) -> str:
    return re.sub(r"_+", "_", re.sub(r"[^a-z0-9_]", "_", text.lower())).strip("_")


def icon_for(material: str) -> tuple[str, str | None]:
    """Returns (icon spec, todo note)."""
    m = str(material).strip()
    low = m.lower()
    if low.startswith("basehead-") or low.startswith("texture-"):
        return "head:" + m.split("-", 1)[1], None
    if low.startswith("hdb-") or low.startswith("nexo-") or low.startswith("itemsadder-") or low.startswith("oraxen-"):
        return "PLAYER_HEAD", f"TODO icon: was {m}"
    if low.startswith("head-"):
        return "PLAYER_HEAD", None
    return m.upper(), None


def is_skippable(key: str, item: dict) -> str | None:
    material = str(item.get("material", "")).lower()
    if "slots" in item:
        return "filler (slots list)"
    if material.endswith("stained_glass_pane") or material in FILLER_MATERIALS or material.startswith("head-%"):
        return f"decoration ({material})"
    if int(item.get("priority", 1) or 1) >= 2:
        return "no-access twin"
    if "display_name" not in item:
        return "no display_name"
    for cmd in item.get("click_commands", []) or []:
        low = str(cmd).lower()
        if low.startswith("[openguimenu]") or low.startswith("[close]") or low.startswith("[connect]"):
            return "navigation"
    return None


def permissions_of(item: dict) -> list[str]:
    perms: list[str] = []
    for block_name in ("view_requirement", "click_requirement", "left_click_requirement"):
        block = item.get(block_name) or {}
        for req in (block.get("requirements") or {}).values():
            if not isinstance(req, dict):
                continue
            t = str(req.get("type", "")).lower()
            if t in ("has permission", "!has permission") and req.get("permission"):
                if t == "has permission":
                    perms.append(str(req["permission"]).strip())
            for field in ("input", "expression"):
                for m in LP_PLACEHOLDER.finditer(str(req.get(field, ""))):
                    perms.append(m.group(1).strip())
    seen: list[str] = []
    for p in perms:
        if p not in seen:
            seen.append(p)
    return seen


def commands_of(item: dict) -> tuple[list[str], list[str]]:
    console: list[str] = []
    player: list[str] = []
    for key in ("click_commands", "left_click_commands"):
        for raw in item.get(key, []) or []:
            cmd = str(raw).strip()
            low = cmd.lower()
            if low.startswith("[console]"):
                body = cmd[len("[console]"):].strip()
                if body.lower().startswith("av user"):
                    continue
                console.append(body.replace("%player_name%", "%player%").replace("%player_uuid%", "%uuid%"))
            elif low.startswith("[player]"):
                player.append(cmd[len("[player]"):].strip().replace("%player_name%", "%player%"))
            elif low.startswith(DROP_COMMAND_PREFIXES):
                continue
    return console, player


def lore_of(item: dict) -> list[str]:
    lines: list[str] = []
    for raw in item.get("lore", []) or []:
        plain = strip_codes(str(raw)).strip()
        if plain.lower() in ("[no access]",) or plain.lower().startswith("cost:"):
            continue
        lines.append(legacy_to_minimessage(str(raw)))
    while lines and strip_codes(lines[0]).strip() == "":
        lines.pop(0)
    while lines and strip_codes(lines[-1]).strip() == "":
        lines.pop()
    return lines


def q(s: str) -> str:
    """YAML double-quoted scalar (JSON strings are valid YAML)."""
    return json.dumps(s, ensure_ascii=False)


def load_existing_prices(path: Path | None) -> dict[str, int]:
    if not path or not path.exists():
        return {}
    data = yaml.load(path.read_text(encoding="utf-8"), Loader=MergingLoader) or {}
    prices: dict[str, int] = {}
    for cat in (data.get("categories") or {}).values():
        for item_id, item in (cat.get("items") or {}).items():
            if isinstance(item, dict) and isinstance(item.get("price"), int) and item["price"] < PLACEHOLDER_PRICE:
                prices[item_id] = item["price"]   # untouched placeholders stay TODO
    return prices


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True, help="catalog.yml to write")
    ap.add_argument("--existing", help="previous catalog.yml; its prices are kept by item id")
    ap.add_argument("--default-price", type=int, default=999999, help="placeholder price for new items")
    ap.add_argument("specs", nargs="+", help="<category_id>=<Display Name>=<file1,file2,...>[=ICON]")
    a = ap.parse_args()

    existing = load_existing_prices(Path(a.existing) if a.existing else None)
    out: list[str] = [
        "# Generated by tools/dm2catalog.py from the DeluxeMenus menus. Edit prices (TODO) and names freely;",
        "# re-running the converter with --existing keeps the prices you set.",
        "categories:",
    ]
    total_items = 0
    todo_prices = 0
    todo_icons = 0
    used_ids: set[str] = set()
    for spec in a.specs:
        parts = spec.split("=")
        if len(parts) < 3:
            print(f"bad spec: {spec}", file=sys.stderr)
            return 2
        cat_id, cat_name, files = slug(parts[0]), parts[1], parts[2].split(",")
        cat_icon = parts[3].upper() if len(parts) > 3 else "CHEST"
        out.append(f"  {cat_id}:")
        out.append(f"    name: {q(legacy_to_minimessage(cat_name))}")
        out.append(f"    icon: {cat_icon}")
        out.append("    items:")
        count = 0
        skipped: dict[str, int] = {}
        for file_index, file in enumerate(files):
            data = yaml.load(Path(file).read_text(encoding="utf-8"), Loader=MergingLoader) or {}
            for key, item in (data.get("items") or {}).items():
                if not isinstance(item, dict):
                    continue
                why = is_skippable(str(key), item)
                if why:
                    skipped[why] = skipped.get(why, 0) + 1
                    continue
                perms = permissions_of(item)
                console, player = commands_of(item)
                if not perms and not console:
                    skipped["no permission or console command"] = skipped.get("no permission or console command", 0) + 1
                    continue
                item_id = slug(f"{cat_id}_{key}")
                base, n = item_id, 2
                while item_id in used_ids:
                    item_id = f"{base}_{n}"
                    n += 1
                used_ids.add(item_id)
                icon, icon_note = icon_for(item.get("material", "STONE"))
                price = existing.get(item_id)
                price_note = None
                if price is None:
                    price = a.default_price
                    price_note = "TODO: set price"
                    todo_prices += 1
                if icon_note:
                    todo_icons += 1
                out.append(f"      {item_id}:")
                out.append(f"        name: {q(legacy_to_minimessage(str(item['display_name'])))}")
                out.append(f"        icon: {q(icon)}" + (f"   # {icon_note}" if icon_note else ""))
                lore = lore_of(item)
                if lore:
                    out.append("        lore:")
                    for line in lore:
                        out.append(f"          - {q(line)}")
                out.append(f"        price: {price}" + (f"   # {price_note}" if price_note else ""))
                if perms:
                    out.append("        permissions:")
                    for p in perms:
                        out.append(f"          - {q(p)}")
                if console or player:
                    out.append("        purchase-commands:")
                    if console:
                        out.append("          console:")
                        for c in console:
                            out.append(f"            - {q(c)}")
                    if player:
                        out.append("          player:")
                        for c in player:
                            out.append(f"            - {q(c)}")
                out.append(f"        sort: {file_index * 100 + int(item.get('slot', 0) or 0)}")
                count += 1
        total_items += count
        skipped_text = ", ".join(f"{v} {k}" for k, v in skipped.items()) or "nothing"
        print(f"{cat_id}: {count} items (skipped {skipped_text})")

    Path(a.out).parent.mkdir(parents=True, exist_ok=True)
    Path(a.out).write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {a.out}: {total_items} items, {todo_prices} TODO prices, {todo_icons} TODO icons")
    return 0


if __name__ == "__main__":
    sys.exit(main())
