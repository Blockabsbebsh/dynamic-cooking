"""Renders dish previews for review: each dish at 32x with a pixel grid next to reference sprites, then a row of
flavour variants at 4x and 1x on the grey inventory slot colour.

    python3 tools/textures/preview.py OUT_DIR [REFERENCE_DIR] [dish ...]

REFERENCE_DIR holds 16x16 PNGs named after the `refs` and `vanilla` sprite of each dish (vanilla sprites are not in
this repository). Without it, dishes drawn on a vanilla sprite preview as their overlay only.
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from dishes import DISHES
import generate
from generate import compose, dish_flavors

SLOT = (0x8B, 0x8B, 0x8B, 255)
BACK = (0x50, 0x50, 0x50, 255)


def font(size):
	try:
		return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", size)
	except OSError:
		return ImageFont.load_default()


def big(image, scale):
	out = Image.new("RGBA", (16 * scale, 16 * scale), SLOT)
	out.alpha_composite(image.resize((16 * scale, 16 * scale), Image.NEAREST))
	if scale >= 16:
		draw = ImageDraw.Draw(out)
		for i in range(17):
			draw.line([(i * scale, 0), (i * scale, 16 * scale)], fill=(0, 0, 0, 40))
			draw.line([(0, i * scale), (16 * scale, i * scale)], fill=(0, 0, 0, 40))
	return out


def colors(image):
	return len({p for p in image.getdata() if p[3]})


def preview(name, flavors, refs_dir):
	dish = DISHES[name]
	default = compose(name, dish.get("defaults") or [])
	refs = []
	for ref in dish.get("refs", []):
		path = refs_dir / f"{ref}.png" if refs_dir else None
		if path and path.exists():
			refs.append((f"{ref} (vanilla)", Image.open(path).convert("RGBA")))

	scale, pad = 24, 20
	tiles = [(f"{name} ({colors(default)} colours)", default)] + refs
	variants = [(flavor, compose(name, [flavor] if "b" not in dish["layers"] else [flavor, flavors[(i + 3) % len(flavors)]]))
			for i, flavor in enumerate(flavors)]

	per_row = 8
	var_rows = (len(variants) + per_row - 1) // per_row
	width = max(pad + len(tiles) * (16 * scale + pad), pad + per_row * (64 + 12 + 16 + pad))
	height = 40 + 16 * scale + pad + var_rows * (64 + 40) + pad
	canvas = Image.new("RGBA", (width, height), BACK)
	draw = ImageDraw.Draw(canvas)
	label = font(18)

	for i, (text, image) in enumerate(tiles):
		x = pad + i * (16 * scale + pad)
		draw.text((x, 10), text, fill="white", font=label)
		canvas.alpha_composite(big(image, scale), (x, 36))

	top = 36 + 16 * scale + pad
	for i, (text, image) in enumerate(variants):
		x = pad + (i % per_row) * (64 + 12 + 16 + pad)
		y = top + (i // per_row) * (64 + 40)
		draw.text((x, y), text, fill="white", font=font(13))
		canvas.alpha_composite(big(image, 4), (x, y + 18))
		slot = Image.new("RGBA", (20, 20), SLOT)
		slot.alpha_composite(image, (2, 2))
		canvas.alpha_composite(slot, (x + 64 + 8, y + 18))

	return canvas


def overview(names):
	scale, pad = 6, 10
	cell = 16 * scale + pad
	canvas = Image.new("RGBA", (pad + len(names) * cell, 16 * scale + 2 * pad + 60), BACK)
	draw = ImageDraw.Draw(canvas)
	for i, name in enumerate(names):
		image = compose(name, DISHES[name].get("defaults") or [])
		x = pad + i * cell
		canvas.alpha_composite(big(image, scale), (x, pad))
		slot = Image.new("RGBA", (20, 20), SLOT)
		slot.alpha_composite(image, (2, 2))
		canvas.alpha_composite(slot, (x, pad + 16 * scale + 6))
		draw.text((x + 24, pad + 16 * scale + 8), name, fill="white", font=font(11))
	return canvas


def main():
	out = Path(sys.argv[1])
	refs_dir = Path(sys.argv[2]) if len(sys.argv) > 2 else None
	generate.VANILLA_DIR = refs_dir
	names = sys.argv[3:] or list(DISHES)
	out.mkdir(parents=True, exist_ok=True)
	flavors = dish_flavors()

	for name in names:
		preview(name, flavors.get(name) or ["beef"], refs_dir).convert("RGB").save(out / f"{name}_preview.png")
		compose(name, DISHES[name].get("defaults") or []).save(out / f"{name}.png")
	overview(names).convert("RGB").save(out / "overview.png")


if __name__ == "__main__":
	main()
