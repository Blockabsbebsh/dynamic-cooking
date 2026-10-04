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
so a dish matches the ingredient the player put in. A "raw_" palette, from the raw vanilla item, is used when the
ingredient went into a dish uncooked, on the dishes marked `raw` in dishes.py.
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
	# cooked egg: white with yolk in the shadows, for scrambled or fried egg fillings
	"egg":              ["#FFFDF0", "#F7F0D8", "#EDE2BC", "#F2C23A", "#D9A21E", "#C9B58A", "#8E7A4E", "#F5C83C"],
	"mushroom":         ["#CC9978", "#B5947D", "#916D55", "#725643", "#6A4E3B", "#5A4434", "#3E3129", "#EDE8CA"],
	"poisonous_potato": ["#D2E962", "#C4D951", "#B0C83A", "#6B863E", "#49673F", "#315237", "#1F3324", "#D9AA51"],
	# meat and fish, cooked
	"beef":             ["#B07458", "#985C43", "#7C4835", "#673728", "#522F1F", "#4E2719", "#2A160D", "#3F2116"],
	"chicken":          ["#EECAAC", "#E6BEA4", "#DE9D7B", "#CD7D4A", "#AC5D31", "#8B4420", "#5A2C10", "#D28E62"],
	"cod":              ["#E2E5C6", "#D6C5AD", "#CFB88C", "#C6A271", "#AE8B67", "#986D4E", "#6B442B", "#4A2E1C"],
	"mutton":           ["#B5806A", "#A3705A", "#9D6147", "#884F40", "#7C402F", "#522F1F", "#2A160D", "#E2D3AC"],
	"porkchop":         ["#E2D3AC", "#DACBA4", "#D3C088", "#C5AD77", "#BCA474", "#997942", "#5F4F27", "#8C6932"],
	"pufferfish":       ["#FCE5BC", "#FFC908", "#FBA70C", "#D8951A", "#BF841B", "#915B15", "#83500E", "#429BBA"],
	"rabbit":           ["#E7BFA2", "#DBA581", "#D28E62", "#A95F32", "#884721", "#7A3D1C", "#592D14", "#4A2410"],
	"salmon":           ["#F0A080", "#DF7D53", "#D3604A", "#BA4F23", "#9E441F", "#733D20", "#3D2B29", "#5D7764"],
	"rotten_flesh":     ["#C5956A", "#C5815A", "#C56541", "#B44420", "#8B3418", "#622C10", "#28140A", "#6A5D18"],
	"tropical_fish":    ["#FBD8C2", "#F58A48", "#F46F20", "#DF590A", "#BB502C", "#A44222", "#692E09", "#EFBDA1"],
	# meat, fish and potato, raw
	"raw_beef":         ["#EA8873", "#E2625A", "#E03E35", "#C42A22", "#AD1D17", "#7B1713", "#470A06", "#7B1713"],
	"raw_chicken":      ["#FFE7DC", "#F2C9BD", "#EFBCAC", "#DFA996", "#C19280", "#B47B65", "#865245", "#F2C9BD"],
	"raw_cod":          ["#E2D8C4", "#D6C5AD", "#C6A271", "#B18953", "#986D4E", "#6B442B", "#4A2E1C", "#4A2E1C"],
	"raw_mutton":       ["#E88A82", "#E2625A", "#D12E26", "#AD332E", "#96211B", "#7A1A15", "#470A06", "#E2D3AC"],
	"raw_porkchop":     ["#FFC6C6", "#FFADAD", "#FF8C8C", "#EF7070", "#A75353", "#853E3E", "#512626", "#FFE0E0"],
	"raw_potato":       ["#F8D086", "#E9BA62", "#D9AA51", "#C8973A", "#AF8444", "#9A5500", "#6D3701", "#86693E"],
	"raw_rabbit":       ["#FEE5D2", "#F2C9BD", "#EFBCAC", "#DAA08C", "#B88473", "#B47864", "#865245", "#DAA08C"],
	"raw_salmon":       ["#BE4644", "#AB3533", "#902928", "#723530", "#58403C", "#3D2B29", "#1B2A26", "#5D7764"],
	# plants from water, desert, jungle and the Nether
	"kelp":             ["#9CC45A", "#78A83A", "#5A8A2E", "#476F22", "#36561A", "#2A4414", "#1C2E0C", "#A8C66A"],
	"seagrass":         ["#8CCB5A", "#5FAE3A", "#3E8A2A", "#2F7022", "#24581A", "#1A4414", "#102E0C", "#7FC0D8"],
	"sea_pickle":       ["#B8C46A", "#97A84A", "#6B7A2E", "#5A6824", "#48541C", "#3A4416", "#252C0C", "#E8F08A"],
	"cactus":           ["#9CCB5A", "#7AAE40", "#5E8F2F", "#4C7A24", "#3C641C", "#2E4E14", "#1C320C", "#E8E0A0"],
	"bamboo":           ["#D2DC7A", "#B8C85A", "#8FA83A", "#78902E", "#5E7424", "#4A5C1C", "#2E3A10", "#5E8A2A"],
	"sugar_cane":       ["#D8EBA8", "#BFDC8A", "#A8C66A", "#8EAE52", "#76943E", "#5E7A2E", "#3E5220", "#E8F2CC"],
	"fern":             ["#9CC46A", "#7DAA4E", "#5F8A3A", "#4E742E", "#3E5E24", "#2E481A", "#1C2E10", "#A8D07A"],
	"lily_pad":         ["#6ABE4A", "#46A032", "#2F7A2A", "#286A22", "#20561A", "#184414", "#0E2C0A", "#E8B8D8"],
	"nether_wart":      ["#D8504A", "#B83436", "#8A1E22", "#74181C", "#5E1216", "#4A0E10", "#300808", "#F08070"],
	"crimson_fungus":   ["#F06A4A", "#D44A36", "#A8302A", "#902622", "#761E1A", "#5C1614", "#3A0E0C", "#F0C060"],
	"warped_fungus":    ["#5AE8C8", "#2EC8A8", "#14A08A", "#108A76", "#0C7262", "#085A4C", "#043A30", "#F08A3A"],
	# plain looks for dishes with nothing on them, and sweeteners
	"oat":              ["#FAF0D8", "#F2E6C8", "#E0CFA4", "#C8B486", "#A8946A", "#8E7A52", "#6A5A3A", "#D8C68A"],
	"milk":             ["#FFFFFF", "#F8F6F0", "#F0ECE2", "#E2DCCE", "#CFC6B4", "#B8AE9A", "#8E8676", "#FFFFFF"],
	"snow":             ["#FFFFFF", "#F4FAFF", "#E4F0F8", "#CFE0EE", "#B4CAE0", "#9AB2CC", "#7A90AA", "#FFFFFF"],
	"honey":            ["#FFE08A", "#FBC64A", "#F0A62A", "#D8861C", "#B86A12", "#94500C", "#6A3806", "#FFF0B0"],
	"leaf":             ["#C8E09A", "#A8C878", "#8AAE5A", "#6E9444", "#567A32", "#406024", "#2A4016", "#E8F0C8"],
}
