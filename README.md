# Dynamic Cooking

A Fabric mod for Minecraft 26.3 that brings Breath of the Wild style cooking to vanilla ingredients. Put up to five ingredients in a pot, or lay simple dishes out on a crafting table, and get a dish named after what went in: Carrot Cake, Chorus Fruit Cake, Beef and Potato Stew.

## Status

Early development. The cooking pot makes cakes, pies, cookies, stews, soups, omelettes and roasts. Sandwiches, skewers, kelp rolls, salads and juice are crafted on a crafting table, one ingredient per slot in any shape. Sample dishes made through the real rules are in the Dynamic Cooking creative tab.

## How it fits together

- `cooking/`: the rules engine. Plain Java with no Minecraft imports, covered by unit tests.
- `registry/`: data pack registries and their JSON formats.
- `dish/`: turns ingredient stacks into a dish stack (name, food, buff, look).
- `block/`: the cooking pot.
- `recipe/`: the crafting table recipe for crafted dishes.
- `component/`, `item/`: the `dish` component, the dish items and the creative tab.
- `src/main/resources/data/dynamic_cooking/dynamic_cooking/`: every ingredient and dish type, as JSON. Data packs can add or override entries.

### Adding an ingredient

Add a file to `data/<namespace>/dynamic_cooking/ingredient/`:

```json
{
	"items": ["minecraft:carrot"],
	"roles": ["produce"],
	"flavor": "carrot",
	"nutrition": 3,
	"saturation": 3.6
}
```

`buff` is optional: `{"effect": "minecraft:night_vision", "potency": 1}`.

`effects` lists side effects the ingredient always gives, like hunger from rotten flesh: `[{"effect": "minecraft:hunger", "seconds": 30, "chance": 0.8}]`.

`nutrition` and `saturation` follow the vanilla food values. Ingredients that aren't eaten on their own get a value from the vanilla foods they go into, like wheat from bread.

A raw ingredient gets a `raw` block. Its `nutrition` and `saturation` are those of its cooked version. The pot swaps it for its `cooks_into` item, and copies of one raw ingredient on their own just come out cooked, like raw potatoes as baked potatoes. Crafted dishes keep it raw: it is worth `nutrition_penalty` and `saturation_penalty` less (down to the vanilla raw value), and its own `effects` may apply. A raw dish can be put back in the pot on its own to cook it.

```json
"raw": {
	"cooks_into": "minecraft:cooked_chicken",
	"nutrition_penalty": 4,
	"saturation_penalty": 6.0,
	"effects": [{"effect": "minecraft:hunger", "seconds": 30, "chance": 0.3}]
}
```

### Adding a dish type

Add a file to `data/<namespace>/dynamic_cooking/dish_type/`. Dish types are tried from the lowest `priority` up and the first one whose `requires` can all be filled wins. Requirements marked `flavor` name and color the dish. `method` is `pot` (the default) or `crafting`; only dish types with the matching method are tried. Anything the pot can't place becomes Dubious Mush, while a crafting grid that fits no dish just doesn't craft.

Ingredients left over once the requirements are filled must suit the dish: they need one of the dish's `flavor_roles` (produce, protein and mushroom by default), a role the dish already requires, or a role any dish takes as an extra (seasoning, seeds). That is why a stick next to bread makes neither a skewer nor a sandwich. Milk is also an extra any dish takes. `raw_ok` lists raw ingredients the dish takes without a penalty, like fish in a kelp roll. `makes` is how many items one batch gives, like 4 kelp rolls.

```json
{
	"item": "dynamic_cooking:cake",
	"priority": 1,
	"requires": [
		{ "match": { "roles": ["flour"] } },
		{ "match": { "roles": ["sweet"] }, "flavor": true },
		{ "match": { "roles": ["egg"] } }
	],
	"forbids": [ { "roles": ["protein", "mushroom"] } ],
	"flavor_roles": ["produce"],
	"bonus_nutrition": 4,
	"bonus_saturation": 2.0
}
```

A dish's food value is the sum of its ingredients' `nutrition`, plus 1 for every ingredient used (a bowl, bottle, stick or water bucket doesn't count), plus the dish's `bonus_nutrition`, plus 1 for each flavor after the first, capped at 20. Saturation adds up the same way, with 0.5 per ingredient. A dish that `makes` several shares that out between them. The numbers live in `CookingRules`.

### Textures

Each dish is drawn once in `tools/textures/dishes.py` as a fixed base layer (bowl, bottle, stick, bread) plus one or two flavour layers in 8 grey keys. Each flavour has an 8-colour palette in `tools/textures/palettes.py`. At resource load, Minecraft's `paletted_permutations` atlas source recolours every flavour layer once per palette, and the dish's item model picks the sprites from the flavours cooking wrote into `custom_model_data`. A flavour without a palette, for example one from a data pack, shows the grey layer tinted with its ingredient's `color`.

After changing a template, a palette, a dish type or an ingredient, regenerate the textures, atlas, models and item definitions (needs Pillow):

```
python3 tools/textures/generate.py
```

`python3 tools/textures/preview.py OUT_DIR` renders review sheets with every flavour of every dish.

### Pot models

The pot body is drawn by hand in `models/block/cooking_pot.json`. Its legs (short ones down to a campfire's logs, since the pot stays a block up so the flames don't show through its floor), liquid and mash levels, floating ingredient chunks and the blockstate are generated from it:

```
python3 tools/models/pot_models.py
```

### Placed cakes

A cooked cake is placed on the ground and eaten a slice at a time, like a vanilla cake. Each slice gives the cake's buff and a share of its food value. The block is the vanilla cake with a grey fruit and filling overlay, tinted with the cake's ingredient colours. Sneak and right-click a placed cake (ours or vanilla) with a sword to cut it into cake slice items, one per slice left, each worth one slice. The overlays, the seven slice models, the blockstate and the cake slice item are generated by:

```
python3 tools/models/cake_block.py
```

## Building

Needs Java 25. `./gradlew build` builds the mod and runs the tests; `./gradlew runClient` starts a dev client.

## License

MIT, see [LICENSE](LICENSE).
