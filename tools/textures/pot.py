"""Writes the cooking pot's block and item textures from the text grids below.

Run from the repository root after changing a grid or the palette:

    python3 tools/textures/pot.py

Needs Pillow. The block textures follow the UVs in models/block/cooking_pot*.json:
- cooking_pot_side: rows 0-6 are the outside of a wall from the rim down, row 7 the side of the base.
  Rows 8-15 only show up as break particles.
- cooking_pot_inner: rows 0-6 are the inside of a wall from the rim down, rows 7-15 the floor.
- cooking_pot_metal: handles, stilts and the underside.
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEXTURES = ROOT / "src/main/resources/assets/dynamic_cooking/textures"

# Iron, lit from the top left. Darks lean slightly cool, like vanilla iron and cauldrons.
PALETTE = {
	"H": "#E8E8EC",  # highlight
	"R": "#CACAD0",  # rim
	"L": "#B4B4BA",  # light
	"M": "#9A9AA1",  # mid
	"S": "#7A7A82",  # shadow
	"D": "#5E5E66",  # deep
	"O": "#46464E",  # outline, top left
	"X": "#2C2C33",  # outline, bottom right
	"I": "#3A3A42",  # inside
	"J": "#26262C",  # inside, deepest
}

SIDE = """
RRHRRRRRLRRRHRRR
SSSDSSSSSSSDSSSS
MLLMMMMMMLLLMMMM
MMMMMMSMMMMMMMSM
MMMLLMMMMMMMLLMM
SMMMMMMMSMMMMMMM
SSSSDSSSSSSSDSSS
DDODDDDDDODDDDDD
MMMMLLMMMMMMMSMM
MSMMMMMMMMLLMMMM
MMMMMMMSMMMMMMMM
MLLMMMMMMMMMLLMM
MMMMMMSMMMMMMMMS
MMMMLLMMMMSMMMMM
SMMMMMMMMMMMLLMM
MMMMMMMLLMMMMMMM
"""

INNER = """
MMMSMMMMMMSMMMMM
DDDDDSDDDDDDDSDD
OOOODOOOOOOODOOO
OOOOOOOIOOOOOOOO
IIIOIIIIIIIOIIII
IIIIIIIIJIIIIIII
JIIIIJIIIIIIJIII
IIIIIIIIIIIIIIII
IIJIIIIIOIIIIJII
IIIIIOIIIIIIIIII
IOIIIIIIIJIIIOII
IIIIJIIIIIIIIIII
IIIIIIIOIIIJIIII
IJIIIIIIIIIIIIOI
IIIIOIIIJIIIIIII
IIIIIIIIIIIOIIJI
"""

METAL = """
SSDSSSSSMSSSSDSS
SSSSSDSSSSSSSSSS
MSSSSSSSSDSSSMSS
SSSSMSSSSSSSSSSD
SDSSSSSDSSMSSSSS
SSSSSSSSSSSSDSSS
SSMSSDSSSSSSSSSM
DSSSSSSSMSSDSSSS
SSSSSSSSSSSSSSSS
SSSDSSMSSSSSDSSS
SMSSSSSSSDSSSSSS
SSSSSSSSSSSSSMSS
SSDSSMSSSSSDSSSS
SSSSSSSSSSSSSSSS
MSSSSSSDSSMSSSDS
SSSSDSSSSSSSSSSS
"""

# The item sprite is drawn in full colour in art/cooking_pot.png.
ITEM_ART = Path(__file__).resolve().parent / "art/cooking_pot.png"

SPRITES = {
	"block/cooking_pot_side": SIDE,
	"block/cooking_pot_inner": INNER,
	"block/cooking_pot_metal": METAL,
}


def rgba(hex_color):
	return tuple(int(hex_color[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def render(grid):
	rows = grid.strip("\n").split("\n")
	assert len(rows) == 16 and all(len(row) == 16 for row in rows), [len(row) for row in rows]
	image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	for y, row in enumerate(rows):
		for x, ch in enumerate(row):
			if ch != ".":
				image.putpixel((x, y), rgba(PALETTE[ch]))
	return image


def main():
	for name, grid in SPRITES.items():
		path = TEXTURES / f"{name}.png"
		path.parent.mkdir(parents=True, exist_ok=True)
		render(grid).save(path)
		print("wrote", path.relative_to(ROOT))

	Image.open(ITEM_ART).convert("RGBA").save(TEXTURES / "item/cooking_pot.png")
	print("wrote", (TEXTURES / "item/cooking_pot.png").relative_to(ROOT))


if __name__ == "__main__":
	main()
