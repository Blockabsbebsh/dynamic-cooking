"""Writes the placed cake's overlay textures, block models and blockstate, and the cake slice item.

Run from the repository root after changing a grid, a grey or the slice shapes:

	python3 tools/models/cake_block.py

Needs Pillow. The cake is the vanilla cake (minecraft:block/cake_*, loaded from the game, never copied here) with an
overlay element on top of it in the same place. The overlays are drawn in greys and tinted per cake by
DynamicCookingClient: tint 0 is the cake's first flavour colour (the fruit on top), tint 1 its second, or the first
again (the filling between the sponge layers). Light grey takes the ingredient colour as it is, darker greys shade it.

- cake_fruit_top: fruit on the frosting. The pieces sit over the vanilla berries so those never show through.
- cake_filling_side: rows 8-15 are the outside of the cake (frosting on top, sponge below), as in vanilla cake_side.
- cake_filling_inner: rows 8-15 are the cut face of a bitten cake, as in vanilla cake_inner.

The cake slice item, cut from a placed cake with a sword, is drawn here too: a full-colour wedge (cake_slice, from the art in
tools/textures/art) and a grey layers on top: the cherry (cake_slice_fruit), tinted with the slice's first flavour colour, and the line of filling
(cake_slice_filling), tinted with its second. CakeCutting gives one-flavour slices the first colour twice.

Pass a folder of vanilla block textures to also write a preview: python3 tools/models/cake_block.py VANILLA_DIR OUT
"""

import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/dynamic_cooking"
NS = "dynamic_cooking"

# Must match DishCakeBlock.MAX_BITES: a bite takes 2 pixels off the west side.
MAX_BITES = 6

GREYS = {
	"L": "#FFFFFF",  # lit side of a piece, the ingredient colour itself
	"M": "#D2D2D2",  # body
	"S": "#A4A4A4",  # shadow, bottom right
	"D": "#787878",  # deepest shadow and edges
}

FRUIT_TOP = """
................
................
...........M....
....LM..........
....MS..........
...........S....
.......LM.......
..M...LMMS......
......MMSD......
.......SD....M..
................
...LM...........
...SD.....S.....
................
................
................
"""

FILLING_SIDE = """
................
................
................
................
................
................
................
................
................
................
................
................
................
.DMMSMMLMMSMMMD.
.....S....S.....
................
"""

FILLING_INNER = """
................
................
................
................
................
................
................
................
................
................
................
....M...........
.DMLMMSMMLMMSMD.
.........S......
................
................
"""

# The slice itself is drawn in full colour in tools/textures/art/cake_slice.png. Its cherry is redrawn here in greys,
# so it takes the slice's fruit colour, and those pixels are cut out of the art.
SLICE_ART = ROOT / "tools/textures/art/cake_slice.png"

SLICE_FRUIT = """
................
.......SD.......
......DLMD......
......DMSD......
.......DD.......
................
................
................
................
................
................
................
................
................
................
................
"""

# The line of filling through the sponge, tinted with the slice's second flavour colour (the first again when it has one).
SLICE_FILLING = """
................
................
................
................
................
................
................
..SM............
....LM..........
......LM........
........M.......
................
................
................
................
................
"""

# Shown on a slice that carries no colour, like one cut from a vanilla cake: the vanilla cake's red berries.
SLICE_DEFAULT_COLOR = "#C42430"
SLICE_FILLING_DEFAULT_COLOR = "#F8DE92"

TEXTURES = {
	"cake_fruit_top": FRUIT_TOP,
	"cake_filling_side": FILLING_SIDE,
	"cake_filling_inner": FILLING_INNER,
}


def rgba(hex_color):
	return tuple(int(hex_color[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def image(grid, palette=None):
	rows = grid.strip("\n").split("\n")
	assert len(rows) == 16 and all(len(row) == 16 for row in rows), [len(row) for row in rows]
	out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

	for y, row in enumerate(rows):
		for x, ch in enumerate(row):
			if ch != ".":
				out.putpixel((x, y), rgba((palette or GREYS)[ch]))

	return out


def slice_base():
	"""The slice art without the pixels the tinted fruit layer draws."""
	out = Image.open(SLICE_ART).convert("RGBA")
	fruit = image(SLICE_FRUIT)
	filling = image(SLICE_FILLING)

	for y in range(16):
		for x in range(16):
			if fruit.getpixel((x, y))[3] or filling.getpixel((x, y))[3]:
				out.putpixel((x, y), (0, 0, 0, 0))

	return out


def argb(hex_color):
	"""Signed 32-bit ARGB, the way item definitions write tint colours."""
	return (0xFF000000 | int(hex_color[1:], 16)) - (1 << 32)


def model(bites):
	"""The vanilla cake with `bites` slices eaten, plus the tinted overlay in exactly the same place."""
	west = 1 + bites * 2
	cut = "#inside" if bites else "#side"
	cut_overlay = "#filling_inner" if bites else "#filling_side"
	textures = {
		"particle": "minecraft:block/cake_side",
		"bottom": "minecraft:block/cake_bottom",
		"top": "minecraft:block/cake_top",
		"side": "minecraft:block/cake_side",
		"fruit": f"{NS}:block/cake_fruit_top",
		"filling_side": f"{NS}:block/cake_filling_side",
	}

	if bites:
		textures["inside"] = "minecraft:block/cake_inner"
		textures["filling_inner"] = f"{NS}:block/cake_filling_inner"

	cake = {"from": [west, 0, 1], "to": [15, 8, 15], "faces": {
		"down": {"texture": "#bottom", "cullface": "down"},
		"up": {"texture": "#top"},
		"north": {"texture": "#side"},
		"south": {"texture": "#side"},
		"west": {"texture": cut},
		"east": {"texture": "#side"},
	}}
	overlay = {"from": [west, 0, 1], "to": [15, 8, 15], "faces": {
		"up": {"texture": "#fruit", "tintindex": 0},
		"north": {"texture": "#filling_side", "tintindex": 1},
		"south": {"texture": "#filling_side", "tintindex": 1},
		"west": {"texture": cut_overlay, "tintindex": 1},
		"east": {"texture": "#filling_side", "tintindex": 1},
	}}
	return {"textures": textures, "elements": [cake, overlay]}


def write_json(path, data):
	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_text(json.dumps(data, indent="\t") + "\n")


def tinted(sprite, color):
	out = sprite.copy()
	r, g, b = rgba(color)[:3]

	for y in range(16):
		for x in range(16):
			p = out.getpixel((x, y))
			if p[3]:
				out.putpixel((x, y), (p[0] * r // 255, p[1] * g // 255, p[2] * b // 255, 255))

	return out


def preview(vanilla, out_dir, overlays):
	"""Top, side and cut face of the placed cake, then the slice item, for a few ingredient colours, at 16x on the inventory grey.

	Only the parts the model shows are drawn: the top inside its 1px border, the bottom half of the side and cut faces.
	"""
	colors = {"sweet berry": "#A82430", "apple": "#C8432F", "carrot": "#E58A1F", "chorus fruit": "#9B6FA0", "glow berry": "#F0A030"}
	slice_item = slice_base()
	faces = [("cake_top", "cake_fruit_top", (1, 1, 15, 15)), ("cake_side", "cake_filling_side", (1, 8, 15, 16)),
			("cake_inner", "cake_filling_inner", (1, 8, 15, 16))]
	scale, gap, label = 16, 16, 110
	width = label + sum((box[2] - box[0]) * scale + gap for _, _, box in faces) + 16 * scale + gap
	row_height = 14 * scale + gap
	sheet = Image.new("RGBA", (width, gap + len(colors) * row_height), rgba("#8B8B8B"))
	draw = ImageDraw.Draw(sheet)

	for row, (name, color) in enumerate(colors.items()):
		y = gap + row * row_height
		x = label
		draw.text((gap, y + 4), name, fill=(0, 0, 0, 255))

		for base, overlay, box in faces:
			face = Image.open(Path(vanilla) / f"{base}.png").convert("RGBA")
			face.alpha_composite(tinted(overlays[overlay], color))
			part = face.crop(box)
			sheet.alpha_composite(part.resize((part.width * scale, part.height * scale), Image.NEAREST), (x, y))
			x += part.width * scale + gap

		item = slice_item.copy()
		item.alpha_composite(tinted(image(SLICE_FRUIT), color))
		item.alpha_composite(tinted(image(SLICE_FILLING), color))
		sheet.alpha_composite(item.resize((16 * scale, 16 * scale), Image.NEAREST), (x, y - scale))

	Path(out_dir).mkdir(parents=True, exist_ok=True)
	sheet.save(Path(out_dir) / "cake_block_preview.png")


def main():
	overlays = {name: image(grid) for name, grid in TEXTURES.items()}

	for name, sprite in overlays.items():
		path = ASSETS / "textures/block" / f"{name}.png"
		path.parent.mkdir(parents=True, exist_ok=True)
		sprite.save(path)

	variants = {}

	for bites in range(MAX_BITES + 1):
		name = "cake" if bites == 0 else f"cake_slice{bites}"
		write_json(ASSETS / "models/block" / f"{name}.json", model(bites))
		variants[f"bites={bites}"] = {"model": f"{NS}:block/{name}"}

	write_json(ASSETS / "blockstates/cake.json", {"variants": variants})

	slice_base().save(ASSETS / "textures/item/cake_slice.png")
	image(SLICE_FRUIT).save(ASSETS / "textures/item/cake_slice_fruit.png")
	image(SLICE_FILLING).save(ASSETS / "textures/item/cake_slice_filling.png")
	write_json(ASSETS / "models/item/cake_slice.json", {
		"parent": "minecraft:item/generated",
		"textures": {"layer0": f"{NS}:item/cake_slice", "layer1": f"{NS}:item/cake_slice_fruit",
			"layer2": f"{NS}:item/cake_slice_filling"},
	})
	write_json(ASSETS / "items/cake_slice.json", {"model": {
		"type": "minecraft:model",
		"model": f"{NS}:item/cake_slice",
		"tints": [
			{"type": "minecraft:constant", "value": -1},
			{"type": "minecraft:custom_model_data", "index": 0, "default": argb(SLICE_DEFAULT_COLOR)},
			{"type": "minecraft:custom_model_data", "index": 1, "default": argb(SLICE_FILLING_DEFAULT_COLOR)},
		],
	}})

	if len(sys.argv) == 3:
		preview(sys.argv[1], sys.argv[2], overlays)

	print(f"{len(overlays)} overlays, {MAX_BITES + 1} models, slice item")


if __name__ == "__main__":
	main()
