# Jake's Ore Fish Farm

Forge 1.20.1. Ore fish you keep in tanks.

- **Fish Tank**: one fish + fish food. One piece of food feeds one fish for 4 minutes. Every 20 seconds of eating is a bite, and each bite rolls the fish's product chance (scales, teeth). One output slot: pipe it out.
- **Rarity tiers** (chance per bite): common 90% (Obsidian Fish), uncommon 75% (Ferrofin, Zincray, Cuprum Eel), rare 60% (Gilded Carp, Brass Bream). Rarer tiers for new fish: epic 45%, legendary 25%, mythic 10%.
- **Breeding Tank**: two fish + food. Same fish -> a baby (breed_chance). Different fish -> a hybrid if a breeding file pairs them.
- **Power Tank**: one power fish (e.g. Cuprum Eel) + food -> Forge Energy every tick it eats, on every side.
- Hoppers, pipes and funnels: fish and food go in, only the output comes out.

## Adding fish with a datapack
- `data/<ns>/orefishfarm_fish/<name>.json`: `{"fish": id, "product": {"item": id, "count": n}, "product_chance": 0.75, "breed_chance": 0.08, "power": 0}`
- `data/<ns>/orefishfarm_breeding/<name>.json`: `{"parents": [a, b], "result": {"item": id}, "chance": 0.04}`
- `data/<ns>/orefishfarm_food/<name>.json`: `{"item": id, "ticks": 4800}` (how long one piece feeds one fish; chances are rolled per bite, every `biteTicks` of eating)
- Tags: `#orefishfarm:tank_fish` (fish with no file: breed only), `#orefishfarm:fish_food`, `#orefishfarm:power_fish` and `#orefishfarm:hybrid_fish` (tooltip lines).
- Ordinary fishing catches: `data/orefishfarm/loot_modifiers/fish_in_fishing.json`.
- Global multipliers, default food time and bite length (`biteTicks`): `config/orefishfarm-common.toml`.
