# Dynamic Cooking

A Fabric mod for Minecraft 26.3 that brings Breath of the Wild style cooking to vanilla ingredients. Put up to five ingredients in a pot and get a dish named after what went in: Carrot Cake, Chorus Fruit Cake, Beef and Potato Stew.

## Status

Early development. The cooking rules, ingredient data and dish items exist; the cooking pot does not yet. Sample dishes cooked through the real rules are in the Dynamic Cooking creative tab.

## How it fits together

- `cooking/`: the rules engine. Plain Java with no Minecraft imports, covered by unit tests.
- `registry/`: data pack registries and their JSON formats.
- `dish/`: turns ingredient stacks into a dish stack (name, food, buff, look).
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

### Adding a dish type

Add a file to `data/<namespace>/dynamic_cooking/dish_type/`. Dish types are tried from the lowest `priority` up and the first one whose `requires` can all be filled wins. Requirements marked `flavor` name and color the dish.

```json
{
	"item": "dynamic_cooking:cake",
	"priority": 1,
	"requires": [
		{ "match": { "roles": ["flour"] } },
		{ "match": { "roles": ["sweet"] } },
		{ "match": { "roles": ["egg"] } },
		{ "match": { "roles": ["produce"] }, "flavor": true }
	],
	"forbids": [ { "roles": ["protein"] } ],
	"bonus_nutrition": 4,
	"bonus_saturation": 2.0
}
```

## Building

Needs Java 25. `./gradlew build` builds the mod and runs the tests; `./gradlew runClient` starts a dev client.

## License

MIT, see [LICENSE](LICENSE).
