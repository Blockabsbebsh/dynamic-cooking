"""Flavour palettes for the dish templates.

Every flavour layer of a dish template is drawn in the 8 grey KEY colours below. At resource load, Minecraft's
paletted_permutations atlas source swaps each key grey for the colour in the same slot of a flavour's palette,
so one template becomes a carrot, beef or chorus fruit version without new art.

Slots, in order:
  0 highlight      small top-left cluster
  1 light
  2 mid            the main body colour
  3 shadow         warmer and more saturated than mid
  4 deep shadow
  5 outline light  top-left edges
  6 outline dark   bottom-right edges
  7 accent         seeds, stems, char: a detail colour

Colours are picked by eye from the vanilla item each flavour comes from (cooked versions for meat and fish),
so a dish matches the ingredient the player put in.
"""

KEY = ["#F0F0F0", "#D8D8D8", "#C0C0C0", "#A8A8A8", "#909090", "#787878", "#606060", "#484848"]

PALETTES = {
	# fruit and veg
	"apple":            ["#FF969D", "#FF2B38", "#DD1725", "#B4131E", "#9C1017", "#7A0E15", "#54090E", "#752802"],
	"beetroot":         ["#DDB5B7", "#C07279", "#B6484C", "#A4272C", "#8F2D2F", "#71160D", "#5B1D17", "#F0D2D2"],
	"carrot":           ["#FFC177", "#FFA73F", "#FF8E09", "#D36A0D", "#AC3900", "#8A2E00", "#752802", "#33BE30"],
	"chocolate":        ["#B07A55", "#976746", "#805530", "#704425", "#5C3519", "#4C2B13", "#301A0A", "#3A200E"],
	"chorus_fruit":     ["#E1D7E1", "#BA9BBA", "#A381A2", "#8E678D", "#785978", "#654864", "#3A143A", "#5E2E5C"],
	"glistering_melon": ["#FFCBC6", "#E0503E", "#BF3123", "#AF160B", "#8A1610", "#F1C905", "#9A7418", "#FFFFFF"],
	"glow_berry":       ["#F7E26B", "#F4C05E", "#F19645", "#D57219", "#BF6717", "#92441A", "#5B4012", "#64922D"],
	"golden_apple":     ["#FEFFE6", "#EAEE57", "#ECCB45", "#DBA213", "#B26411", "#8A4A0E", "#541209", "#752802"],
	"golden_carrot":    ["#FEFFE6", "#EAEE57", "#ECCB45", "#DBA213", "#B26411", "#715008", "#532906", "#301600"],
	"melon":            ["#F28A78", "#E0503E", "#BF3123", "#AF160B", "#8A1610", "#59661A", "#444F0E", "#2A1A10"],
	"potato":           ["#F0CD5A", "#D9AA51", "#C8973A", "#9D772E", "#86693E", "#9A5500", "#6D3701", "#F6DE8A"],
	"pumpkin":          ["#FFC66A", "#F0A23C", "#E3901D", "#C8740F", "#A45413", "#7E3F0C", "#5A2C08", "#6E8A2A"],
	"sweet_berry":      ["#F06A8E", "#DF467E", "#C0102A", "#A50700", "#820B05", "#691F21", "#380E0F", "#286240"],
	"mushroom":         ["#CC9978", "#B5947D", "#916D55", "#725643", "#6A4E3B", "#5A4434", "#3E3129", "#EDE8CA"],
	# meat and fish, cooked
	"beef":             ["#B07458", "#985C43", "#7C4835", "#673728", "#522F1F", "#4E2719", "#2A160D", "#3F2116"],
	"chicken":          ["#EECAAC", "#E6BEA4", "#DE9D7B", "#CD7D4A", "#AC5D31", "#8B4420", "#5A2C10", "#D28E62"],
	"cod":              ["#E2E5C6", "#D6C5AD", "#CFB88C", "#C6A271", "#AE8B67", "#986D4E", "#6B442B", "#4A2E1C"],
	"mutton":           ["#B5806A", "#A3705A", "#9D6147", "#884F40", "#7C402F", "#522F1F", "#2A160D", "#E2D3AC"],
	"porkchop":         ["#E2D3AC", "#DACBA4", "#D3C088", "#C5AD77", "#BCA474", "#997942", "#5F4F27", "#8C6932"],
	"pufferfish":       ["#FCE5BC", "#FFC908", "#FBA70C", "#D8951A", "#BF841B", "#915B15", "#83500E", "#429BBA"],
	"rabbit":           ["#E7BFA2", "#DBA581", "#D28E62", "#A95F32", "#884721", "#7A3D1C", "#592D14", "#4A2410"],
	"salmon":           ["#F0A080", "#DF7D53", "#D3604A", "#BA4F23", "#9E441F", "#733D20", "#3D2B29", "#5D7764"],
}
