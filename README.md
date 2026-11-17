# Jake's Ore Fish Farm

Forge 1.20.1. Ore fish you keep in tanks.

- **Fish Tank**: one fish + fish food. Each piece of food eaten rolls the fish's product chance (scales, teeth). One output slot: pipe it out.
- **Breeding Tank**: two fish + food. Same fish -> a baby (breed_chance). Different fish -> a hybrid if a breeding file pairs them.
- **Power Tank**: one power fish (e.g. Cuprum Eel) + food -> Forge Energy every tick it eats, on every side.
- Hoppers, pipes and funnels: fish and food go in, only the output comes out.

## Adding fish with a datapack
- `data/<ns>/orefishfarm_fish/<name>.json`: `{"fish": id, "product": {"item": id, "count": n}, "product_chance": 0.25, "breed_chance": 0.08, "power": 0}`
- `data/<ns>/orefishfarm_breeding/<name>.json`: `{"parents": [a, b], "result": {"item": id}, "chance": 0.04}`
- `data/<ns>/orefishfarm_food/<name>.json`: `{"item": id, "ticks": 400}`
- Tags: `#orefishfarm:tank_fish` (fish with no file: breed only), `#orefishfarm:fish_food`, `#orefishfarm:power_fish` and `#orefishfarm:hybrid_fish` (tooltip lines).
- Ordinary fishing catches: `data/orefishfarm/loot_modifiers/fish_in_fishing.json`.
- Global multipliers: `config/orefishfarm-common.toml`.
