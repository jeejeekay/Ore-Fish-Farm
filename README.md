# Jake's Ore Fish Farm

Forge 1.20.1. Ore fish you keep in tanks.

- **Fish Tank**: one fish + fish food. One piece of food feeds one fish for 4 minutes. Every 20 seconds of eating is a bite, and each bite rolls the fish's product chance (scales, teeth). One output slot: pipe it out.
- **Rarity tiers** (chance per bite): common 90% (Obsidian Fish), uncommon 75% (Ferrofin, Zincray, Cuprum Eel), rare 60% (Gilded Carp, Brass Bream). Rarer tiers for new fish: epic 45%, legendary 25%, mythic 10%.
- **Breeding** (switch in the tank screen): pairs sometimes breed on a bite. Same fish -> a baby (breed_chance); different fish -> a crossbreed if a breeding file pairs them (and their genes match it). A baby takes a free fish slot, shows `*` until you hover it, and only comes out when you click it holding its habitat's bucket (water bucket, lava bucket, or an empty bucket for void fish). No free slot, no baby.
- **Stats** (each 0-1, shown as bars once a fish is in a bucket): Yield (product chance), Hunger (how fast it eats; the tank eats at all its fish's combined rate), Charge (FE). 0.5 is normal; 0 = half, 1 = 1.5x. Babies take each stat from a parent, nudged a little.
- **Genes** (at most 3, just names): only breeding files and recipes read them. A baby starts with its species' genes, then gets the parents' in turn (A1, B1, A2, B2, A3, B3) at 50% each until it has 3.
- **Power Tank**: one power fish (e.g. Cuprum Eel) + food -> Forge Energy every tick it eats, on every side.
- Hoppers, pipes and funnels: fish and food go in, only the output comes out.

## Adding fish with a datapack
- `data/<ns>/orefishfarm_fish/<name>.json`: `{"fish": id, "product": {"item": id, "count": n}, "product_chance": 0.75, "breed_chance": 0.08, "power": 0}`
- `data/<ns>/orefishfarm_breeding/<name>.json`: `{"parents": [a, {"species": b, "genes": ["orefishfarm:mutagenic"]}], "requires": [gene...], "result": {"item": id}, "chance": 0.04}` (a parent can be an id or an object that also needs genes; `requires`: genes either parent must have)
- **Genes:** a gene is an item tag in a `genes/` folder: `data/<ns>/tags/items/genes/<gene>.json` lists the species born with it (`orefishfarm:genes/mutagenic` ships empty). Optional colour: `data/<ns>/orefishfarm_gene/<gene>.json` `{"color": "#C040C0"}`; name: lang key `gene.<ns>.<gene>`.
- **Recipe ingredient** `{"type": "orefishfarm:fish", "species": id or "#tag", "genes": [...], "none_of": [...], "habitat": id}` (all optional): a live fish with those genes, in any recipe.
- **Fish models:** shared category models in `models/fish_category/` (ore, ray, stone, power, alloy, each with a `_tail`). A fish's `models/fish/<fish>.json` just names one as its parent and gives its texture.
- `data/<ns>/orefishfarm_food/<name>.json`: `{"item": id, "ticks": 4800}` (how long one piece feeds one fish; chances are rolled per bite, every `biteTicks` of eating)
- Tags: `#orefishfarm:tank_fish` (fish with no file: breed only), `#orefishfarm:fish_food`, `#orefishfarm:power_fish` and `#orefishfarm:hybrid_fish` (tooltip lines).
- Ordinary fishing catches: `data/orefishfarm/loot_modifiers/fish_in_fishing.json`.
- Global multipliers, default food time and bite length (`biteTicks`): `config/orefishfarm-common.toml`.
