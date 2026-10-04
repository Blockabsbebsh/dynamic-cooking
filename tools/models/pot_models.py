"""Writes the cooking pot's block models and blockstate.

Run from the repository root after changing the pot model, its fill levels or the floating chunks:

	python3 tools/models/pot_models.py

The pot body, models/block/cooking_pot.json, is drawn by hand and read from disk. Everything else is generated from it:
- cooking_pot_campfire.json: the pot sunk CAMPFIRE_DROP pixels into a campfire, with its floor raised as far, so the
  campfire's flames, which reach a pixel above their block, stop at the floor and never show inside the pot
- legs for a campfire (short) and for fire, lava or a gap (long)
- liquid and mash surfaces for each fill level, and one floating chunk per ingredient tinted with its color, each with
  a campfire copy that fills the shallower campfire pot
"""

import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/dynamic_cooking"
MODELS = ASSETS / "models/block"
NS = "dynamic_cooking"

# Must match CookingPotBlock.CAMPFIRE_DROP and CookingPotBlock.FILL.
CAMPFIRE_DROP = 4
MAX_FILL = 5
# Campfire logs are 7 pixels tall, so legs on a campfire reach down to 9 pixels below the pot's block.
CAMPFIRE_LOGS = -9

# Interior of the pot, in pixels.
INNER_MIN, INNER_MAX = 4, 12
# One chunk per ingredient slot: x, z, width, depth. Spread out so they don't touch.
CHUNKS = [(5, 5, 2, 2), (9, 5, 2, 2), (7, 9, 2, 2), (10, 9, 1, 2), (5, 10, 1, 1)]

SURFACES = {
	"liquid": "minecraft:block/water_still",
	"mash": "minecraft:block/white_concrete_powder",
}
CHUNK_TEXTURE = "minecraft:block/white_concrete_powder"


# Height of the pot's floor and of a full pot's surface, in pixels.
FLOOR, FULL = 1, 7


def surface_height(fill, floor=FLOOR):
	"""Must match CookingPotBlock.surfaceY."""
	return round(floor + fill * (FULL - floor) / MAX_FILL, 1)


def write(name, model):
	(MODELS / f"{name}.json").write_text(json.dumps(model, indent="\t") + "\n")



def sunk(model, pixels):
	"""A copy of the model moved down by the given number of pixels."""
	out = copy.deepcopy(model)

	for element in out["elements"]:
		element["from"][1] = round(element["from"][1] - pixels, 1)
		element["to"][1] = round(element["to"][1] - pixels, 1)

	return out


def campfire_pot(pot):
	"""The pot with a false floor at the height the campfire's flames reach once it is sunk, then sunk."""
	out = copy.deepcopy(pot)
	up = {"texture": "#inside", "uv": [INNER_MIN, INNER_MIN + 3, INNER_MAX, INNER_MAX + 3]}
	out["elements"].append(box(INNER_MIN, FLOOR, INNER_MIN, INNER_MAX, FLOOR + CAMPFIRE_DROP, INNER_MAX, {"up": up}))
	return sunk(out, CAMPFIRE_DROP)


def box(x1, y1, z1, x2, y2, z2, faces):
	return {"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": faces}


def surface(kind, fill, floor=FLOOR):
	texture = SURFACES[kind]
	face = {"texture": f"#{kind}", "uv": [INNER_MIN, INNER_MIN, INNER_MAX, INNER_MAX], "tintindex": 0}
	return {
		"textures": {"particle": texture, kind: texture},
		"elements": [box(INNER_MIN, floor, INNER_MIN, INNER_MAX, surface_height(fill, floor), INNER_MAX, {"up": face})],
	}


def chunks(fill, floor=FLOOR):
	"""The chunks of the first `fill` ingredients, half under the surface. Tint index 1 is the first ingredient."""
	top = surface_height(fill, floor)
	elements = []

	for slot, (x, z, width, depth) in enumerate(CHUNKS[:fill], start=1):
		def face(u, v, w, h):
			return {"texture": "#chunk", "uv": [u, v, u + w, v + h], "tintindex": slot}

		elements.append(box(x, top - 0.5, z, x + width, top + 0.5, z + depth, {
			"up": face(x, z, width, depth),
			"north": face(x, 0, width, 1),
			"south": face(x, 0, width, 1),
			"east": face(z, 0, depth, 1),
			"west": face(z, 0, depth, 1),
		}))

	return {"textures": {"particle": CHUNK_TEXTURE, "chunk": CHUNK_TEXTURE}, "elements": elements}


def legs(bottom, top):
	height = top - bottom
	side = {"texture": "#leg", "uv": [0, 0, 1, height]}
	elements = []

	for x in (4, 11):
		for z in (4, 11):
			elements.append(box(x, bottom, z, x + 1, top, z + 1, {
				"north": side, "south": side, "east": side, "west": side,
				"down": {"texture": "#leg", "uv": [0, 0, 1, 1]},
			}))

	texture = f"{NS}:block/cooking_pot_metal"
	return {"textures": {"particle": texture, "leg": texture}, "elements": elements}


def part(model, **when):
	entry = {"apply": {"model": f"{NS}:block/{model}"}}

	if when:
		entry["when"] = when

	return entry


def main():
	pot = json.loads((MODELS / "cooking_pot.json").read_text())
	write("cooking_pot_campfire", campfire_pot(pot))
	write("cooking_pot_legs_short", legs(CAMPFIRE_LOGS, -CAMPFIRE_DROP))
	write("cooking_pot_legs_long", legs(-16, 0))

	off_campfire = "none|long"
	parts = [
		part("cooking_pot", legs=off_campfire),
		part("cooking_pot_campfire", legs="short"),
		part("cooking_pot_legs_short", legs="short"),
		part("cooking_pot_legs_long", legs="long"),
	]

	for fill in range(1, MAX_FILL + 1):
		campfire_floor = FLOOR + CAMPFIRE_DROP
		models = {f"{kind}_{fill}": (surface(kind, fill), surface(kind, fill, campfire_floor)) for kind in SURFACES}
		models[f"chunks_{fill}"] = (chunks(fill), chunks(fill, campfire_floor))

		for name, (model, on_campfire) in models.items():
			write(f"cooking_pot_{name}", model)
			write(f"cooking_pot_{name}_campfire", sunk(on_campfire, CAMPFIRE_DROP))

		for name, extra in ((f"liquid_{fill}", {"liquid": "true"}), (f"mash_{fill}", {"liquid": "false"}), (f"chunks_{fill}", {})):
			parts.append(part(f"cooking_pot_{name}", fill=str(fill), legs=off_campfire, **extra))
			parts.append(part(f"cooking_pot_{name}_campfire", fill=str(fill), legs="short", **extra))

	(ASSETS / "blockstates/cooking_pot.json").write_text(json.dumps({"multipart": parts}, indent="\t") + "\n")


if __name__ == "__main__":
	main()
