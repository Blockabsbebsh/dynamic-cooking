"""Writes every dish texture, palette, atlas entry, model and item definition from dishes.py and palettes.py.

Run from the repository root after changing a template, a palette, a dish type or an ingredient:

    python3 tools/textures/generate.py

Needs Pillow. Everything it writes is generated: edit the sources here, not the output.
"""

import json
import shutil
from pathlib import Path

from PIL import Image

from dishes import DISHES
from palettes import KEY, PALETTES

NS = "dynamic_cooking"
ROOT = Path(__file__).resolve().parents[2]
ART = Path(__file__).resolve().parent / "art"
ASSETS = ROOT / "src/main/resources/assets"
DATA = ROOT / "src/main/resources/data" / NS / NS
OUT = ASSETS / NS

SECOND = "abcdefgh"
LAYER_INDEX = {"a": 0, "b": 1}

# Where previews find vanilla sprites for dishes drawn on a vanilla base. The game loads those from its own
# assets, so they are never copied into this repository.
VANILLA_DIR = None


def rgba(hex_color):
	"""#RRGGBB, or #RRGGBBAA for a see-through colour."""
	alpha = int(hex_color[7:9], 16) if len(hex_color) == 9 else 255
	return tuple(int(hex_color[i:i + 2], 16) for i in (1, 3, 5)) + (alpha,)


def argb(hex_color):
	"""Signed 32-bit ARGB, the way vanilla item definitions write tint colours."""
	value = 0xFF000000 | int(hex_color[1:], 16)
	return value - (1 << 32)


def rows(dish):
	grid = [row for row in dish["grid"].strip("\n").split("\n")]
	assert len(grid) == 16 and all(len(row) == 16 for row in grid), (dish["layers"], [len(row) for row in grid])
	return grid


def split_layers(dish):
	"""One RGBA image per layer: the base in its real colours, flavour layers in key greys."""
	images = {name: Image.new("RGBA", (16, 16), (0, 0, 0, 0)) for name in dish["layers"]}
	images["vanilla"] = vanilla_sprite(dish)
	if dish.get("art"):
		images["base"] = Image.open(ART / dish["art"]).convert("RGBA")

	for y, row in enumerate(rows(dish)):
		for x, ch in enumerate(row):
			if ch in ".#":
				continue
			if dish.get("art"):
				# A flavour pixel replaces the art under it.
				images["base"].putpixel((x, y), (0, 0, 0, 0))
			if ch.isdigit():
				images["a"].putpixel((x, y), rgba(KEY[int(ch)]))
			elif ch in SECOND:
				images["b"].putpixel((x, y), rgba(KEY[SECOND.index(ch)]))
			else:
				assert ch in dish.get("pal", {}), f"{dish['layers']}: no colour for {ch!r}"
				images["base"].putpixel((x, y), rgba(dish["pal"][ch]))

	# See-through layers, like jelly. Tinting keeps the alpha.
	for layer, alpha in dish.get("alpha", {}).items():
		image = images[layer]
		for x in range(16):
			for y in range(16):
				pixel = image.getpixel((x, y))
				if pixel[3]:
					image.putpixel((x, y), pixel[:3] + (alpha,))

	return images


def vanilla_sprite(dish):
	"""The vanilla item a dish is drawn on, when the previews have a copy of it. Blank otherwise."""
	path = VANILLA_DIR / f"{dish['vanilla']}.png" if VANILLA_DIR and dish.get("vanilla") else None
	if path and path.exists():
		return Image.open(path).convert("RGBA")
	return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def recolor(image, flavor):
	"""What the paletted_permutations source does at load time, for previews."""
	mapping = {rgba(k): rgba(v) for k, v in zip(KEY, PALETTES[flavor])}
	out = image.copy()
	for x in range(16):
		for y in range(16):
			pixel = out.getpixel((x, y))
			if pixel[3]:
				out.putpixel((x, y), mapping.get(pixel, pixel))
	return out


def compose(name, flavors):
	"""The finished 16x16 sprite for a dish with the given flavours, strongest first."""
	dish = DISHES[name]
	variant = dish.get("variant")
	if variant and flavors and flavors[0] in variant["flavors"]:
		return compose(variant["dish"], flavors)
	images = split_layers(dish)
	main = flavors[0] if flavors else None
	second = flavors[1] if len(flavors) > 1 else main
	out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

	for layer in dish["order"]:
		image = images[layer]
		if layer == "a":
			image = recolor(image, main)
		elif layer == "b":
			image = recolor(image, second)
		out.alpha_composite(image)

	return out


def json_files(folder):
	return [json.loads(path.read_text()) for path in sorted(folder.glob("*.json"))]


def dish_flavors():
	"""Flavours each dish can get, from the shipped dish types and ingredients. Others use the tinted fallback."""
	ingredients = json_files(DATA / "ingredient")
	result = {}

	for dish_type in json_files(DATA / "dish_type"):
		name = dish_type["item"].split(":")[1]
		raw = DISHES.get(name, {}).get("raw", False)
		roles = set(dish_type.get("flavor_roles", []))
		items = set()

		for requirement in dish_type["requires"]:
			if requirement.get("flavor"):
				roles |= set(requirement["match"].get("roles", []))
				items |= set(requirement["match"].get("items", []))

		flavors = []
		for ingredient in ingredients:
			flavor = ingredient.get("flavor")
			if flavor and flavor in PALETTES and flavor not in flavors:
				if roles & set(ingredient["roles"]) or items & set(ingredient["items"]):
					flavors.append(flavor)
					if raw and f"raw_{flavor}" in PALETTES:
						flavors.append(f"raw_{flavor}")

		result[name] = sorted(flavors)

	return result


def write_json(path, value):
	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_text(json.dumps(value, indent="\t") + "\n")


def save_png(image, path):
	path.parent.mkdir(parents=True, exist_ok=True)
	image.save(path)


def model(texture):
	return {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/dish/{texture}"}}


def ref(model_name, tints=None):
	model_id = model_name if ":" in model_name else f"{NS}:item/dish/{model_name}"
	value = {"type": "minecraft:model", "model": model_id}
	if tints:
		value["tints"] = tints
	return value


def flavor_select(template, index, flavors, fallback):
	return {
		"type": "minecraft:select",
		"property": "minecraft:custom_model_data",
		"index": index,
		"cases": [{"when": flavor, "model": ref(f"{template}_{flavor}")} for flavor in flavors],
		"fallback": fallback,
	}


def layer_model(name, layer, flavors, defaults):
	"""A flavour layer: the recoloured sprite for a known flavour, else the grey template tinted with the ingredient colour."""
	dish = DISHES[name]
	template = f"{name}_{dish['layers'][layer]}"
	index = LAYER_INDEX[layer]

	if not flavors:
		# Tinted only, like jelly: the grey template takes the main ingredient's colour.
		return ref(template, [{"type": "minecraft:custom_model_data", "index": 0, "default": argb(dish["tint"])}])

	default = defaults[min(index, len(defaults) - 1)]
	tinted = ref(template, [{"type": "minecraft:custom_model_data", "index": 0, "default": argb(PALETTES[default][2])}])

	if index == 0:
		cooked = flavor_select(template, 0, flavors, tinted)
	else:
		# Only one flavour: the second layer repeats the main one.
		cooked = flavor_select(template, 1, flavors, flavor_select(template, 0, flavors, tinted))

	return {
		"type": "minecraft:condition",
		"property": "minecraft:has_component",
		"component": "minecraft:custom_model_data",
		"on_true": cooked,
		"on_false": ref(f"{template}_{default}"),
	}


def dish_model(name, dish, flavors):
	"""Writes a dish template's sprites and models. Returns its item model and its atlas source."""
	images = split_layers(dish)
	defaults = dish.get("defaults", [])
	# A default can be a look no ingredient has, like plain oats for porridge with nothing on it.
	flavors = [] if dish.get("tint_only") else sorted(set(flavors) | set(defaults))
	parts = []

	for layer in dish["order"]:
		if layer == "vanilla":
			# Drawn on the game's own sprite, so it follows resource packs too.
			parts.append(ref(f"minecraft:item/{dish['vanilla']}"))
			continue

		texture = f"{name}_{dish['layers'][layer]}"
		save_png(images[layer], OUT / f"textures/item/dish/{texture}.png")
		write_json(OUT / f"models/item/dish/{texture}.json", model(texture))

		if layer == "base":
			parts.append(ref(texture))
			continue

		for flavor in flavors:
			write_json(OUT / f"models/item/dish/{texture}_{flavor}.json", model(f"{texture}_{flavor}"))
		parts.append(layer_model(name, layer, flavors, defaults))

	source = None
	templates = [f"{NS}:item/dish/{name}_{dish['layers'][layer]}" for layer in dish["order"] if layer in LAYER_INDEX]
	if templates and flavors:
		source = {
			"type": "minecraft:paletted_permutations",
			"textures": templates,
			"palette_key": f"{NS}:dish/key",
			"permutations": {flavor: f"{NS}:dish/{flavor}" for flavor in flavors},
		}

	item_model = parts[0] if len(parts) == 1 else {"type": "minecraft:composite", "models": parts}
	return item_model, source


def with_variant(base, variant, variant_flavors, defaults):
	"""Shows the variant template when the main flavour is one of variant_flavors, else the dish's own template."""
	cooked = {
		"type": "minecraft:select",
		"property": "minecraft:custom_model_data",
		"index": 0,
		"cases": [{"when": variant_flavors, "model": variant}],
		"fallback": base,
	}
	return {
		"type": "minecraft:condition",
		"property": "minecraft:has_component",
		"component": "minecraft:custom_model_data",
		"on_true": cooked,
		"on_false": variant if defaults and defaults[0] in variant_flavors else base,
	}


def pot_surfaces():
	"""Adds a hidden dish template for each `pot` grid, sharing its dish's layers, palette and flavours."""
	for name, dish in list(DISHES.items()):
		if dish.get("pot"):
			layers = dish["layers"]
			DISHES[f"{name}_pot"] = dict(
				variant_of=name,
				layers=layers,
				order=[layer for layer in dish["order"] if layer in layers],
				defaults=dish.get("defaults", []),
				pal=dish.get("pal", {}),
				grid=dish["pot"],
			)


VANILLA_VESSELS = {"bowl": "bowl", "bottle": "glass_bottle", "bucket": "bucket"}
# The first item model data float of a dish in another container. Must match dish/Vessel.java.
VESSEL_CODES = {"bowl": 1, "bottle": 2, "bucket": 3}


def vessel_variants():
	"""Adds a hidden dish template for each other container a dish can be served in, from `vessels` in dishes.py."""
	for name, dish in list(DISHES.items()):
		for vessel, grid in dish.get("vessels", {}).items():
			layers = {layer: texture for layer, texture in dish["layers"].items() if layer != "base" or any(ch.isupper() for ch in grid)}
			if "b" not in layers:
				# One flavour layer only: second-layer pixels take the main flavour.
				grid = "".join(str(SECOND.index(ch)) if ch in SECOND else ch for ch in grid)
			DISHES[f"{name}_in_{vessel}"] = dict(
				variant_of=name,
				vanilla=VANILLA_VESSELS[vessel],
				layers=layers,
				order=["vanilla"] + [layer for layer in dish["order"] if layer in layers],
				defaults=dish.get("defaults", []),
				pal=dish.get("pal", {}),
				tint=dish.get("tint"),
				grid=grid,
			)


def in_vessels(item_model, name, models):
	"""Draws a dish served in another container, picked by the first item model data float, else its usual look."""
	entries = [{"threshold": code, "model": models[f"{name}_in_{vessel}"]}
		for vessel, code in VESSEL_CODES.items() if f"{name}_in_{vessel}" in models]
	if not entries:
		return item_model
	return {
		"type": "minecraft:range_dispatch",
		"property": "minecraft:custom_model_data",
		"index": 0,
		"entries": entries,
		"fallback": item_model,
	}


def in_pot(item_model, pot_model):
	"""The cooking pot draws a dish with a surface with no display context, which shows the surface."""
	return {
		"type": "minecraft:select",
		"property": "minecraft:display_context",
		"cases": [{"when": "none", "model": pot_model}],
		"fallback": item_model,
	}


def main():
	vessel_variants()
	pot_surfaces()
	flavors_by_dish = dish_flavors()

	for generated in (OUT / "textures/item/dish", OUT / "textures/palettes/dish", OUT / "models/item/dish"):
		shutil.rmtree(generated, ignore_errors=True)

	key = Image.new("RGBA", (8, 1))
	for i, color in enumerate(KEY):
		key.putpixel((i, 0), rgba(color))
	save_png(key, OUT / "textures/palettes/dish/key.png")

	used = sorted({flavor for flavors in flavors_by_dish.values() for flavor in flavors}
		| {flavor for dish in DISHES.values() if not dish.get("tint_only") for flavor in dish.get("defaults", [])})
	for flavor in used:
		strip = Image.new("RGBA", (8, 1))
		for i, color in enumerate(PALETTES[flavor]):
			strip.putpixel((i, 0), rgba(color))
		save_png(strip, OUT / f"textures/palettes/dish/{flavor}.png")

	sources = []
	models = {}

	for name, dish in DISHES.items():
		# A variant template (such as the meat roast) shares its dish's flavours.
		flavors = flavors_by_dish.get(dish.get("variant_of", name), [])
		models[name], source = dish_model(name, dish, flavors)
		if source:
			sources.append(source)

	for name, dish in DISHES.items():
		if dish.get("variant_of"):
			continue
		item_model = models[name]
		variant = dish.get("variant")
		if variant:
			item_model = with_variant(item_model, models[variant["dish"]], variant["flavors"], dish.get("defaults", []))
		item_model = in_vessels(item_model, name, models)
		if dish.get("pot"):
			item_model = in_pot(item_model, models[f"{name}_pot"])
		write_json(OUT / f"items/{name}.json", {"model": item_model})

	# Tells the pot which dishes to draw with their surface.
	write_json(DATA.parent / "tags/item/pot_surface.json", {"values": [f"{NS}:{name}" for name, dish in DISHES.items() if dish.get("pot")]})

	# Atlas files are merged across packs, so this only adds the dish sprites to the vanilla items atlas.
	write_json(ASSETS / "minecraft/atlases/items.json", {"sources": sources})

	print(f"{len(DISHES)} dishes, {len(used)} flavours, {len(sources)} atlas sources")


if __name__ == "__main__":
	main()
