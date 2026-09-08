# BountyfulSeas

A Paper plugin that decides which fish you catch from where you are fishing.

It reads the classified water map produced by
[minecraft-water-map-generator](https://github.com/VonNekyia/minecraft-water-map-generator),
matches the water under your bobber against fish definitions, and hands out
[Nexo](https://nexomc.com/) items.

```text
Minecraft world
      |  water-analyzer (offline, Rust)
water_regions.bin
      |  BountyfulSeas
custom fish  ->  Nexo items, Pl3xMap overlay
```

## What it does

- Reads `water_regions.bin` and resolves any block position to its water region.
- Rolls a rarity tier, then an entry within it, from those that accept the water
  type, terrain, climate, depth, modifiers and current world conditions.
- Generates a Nexo item definition for every fish, so a new fish only needs a
  texture drawn for it.
- Draws the fishable water on [Pl3xMap](https://modrinth.com/plugin/pl3xmap) as
  traced polygons, one per region.
- Keeps per-player catch totals, and shows them as a guide with milestones.

## How a catch is decided

Five questions, in order. Any of them can end with vanilla keeping its fish.

1. **Where is this?** The hook's position is resolved against the scanned water
   map. Unclassified water is left to vanilla.
2. **What lives here?** Every entry is filtered against the spot: water type,
   terrain, climate, depth, water modifiers, and what the world is doing. An
   empty trait list places no restriction on that axis.
3. **Which tier?** A rarity is drawn from the configured chances, counting only
   tiers actually present here.
4. **Which entry?** Within that tier, `spawn_weight` decides.
5. **How big?** Length is rolled between the entry's bounds.

The tier is drawn **before** the entry on purpose. Rolling in one pass would make
a legendary likelier in water that happens to hold three of them, which is the
opposite of what a rarity is for. Water holding one legendary and water holding
ten give it the same odds.

### Rarity tiers

| Tier | Default chance | Notes |
|---|---|---|
| `common` | 75 | |
| `rare` | 15 | |
| `trash` | 5 | not a fish, no length |
| `epic` | 2 | |
| `misc` | 2 | not a fish, no length |
| `legendary` | 0.5 | |
| `treasure` | 0.5 | not a fish, no length |
| `mythic` | 0.05 | rolls its length in the top tenth of its range |
| `signature` | 0 | never rolled; for hand-placed fish |

Chances are **relative weights**, not percentages. They are normalised against
the tiers present at a spot, so they need not add to 100, and a tier nobody has
written an entry for costs nothing.

### Rod enchantments

Both scale a tier's **base** chance per level rather than adding percentage
points, so the balance survives whatever the base numbers are set to.

- **Lure** lifts every *fish* tier above common. It does nothing for `trash`,
  `misc` or `treasure`.
- **Luck of the Sea** lifts `treasure`, and only `treasure`.

Because chances are normalised, raising the rarer tiers lowers `common` on its
own. Nothing is subtracted by hand. Both rates are set in `config.yml`.

### Swarms

An entry with the `swarm` modifier gathers in a few water regions at a time and
moves on a timer, so it can only be caught where its swarm currently is. The
number of swarms and how often they move are configurable; swarming regions are
drawn **gold** on the web map.

## Fish definitions

One file per category in `plugins/BountyfulSeas/fishes/`. The file name is the
category, and `_schema.yaml` documents every field.

```yaml
herring:
  fish_name: "<gray>Hering</gray>"
  fish_item: "nexo:herring"
  lore:
    - "Travels in silver walls that turn as one."

  min_length: 15
  max_length: 32

  water_type: [ocean]
  vegetation: [cold, temperate]
  depth: [shallow]

  spawn_weight: 140
  rarity: common

  on_eat:
    saturation: 2
    effects: []
```

Every trait list is a filter: leaving one out places no restriction on that axis.
A fish without an `on_eat` block cannot be eaten at all, and one in a tier that is
not a fish carries no length.

Three axes are worth knowing about because their names do not say what they hold:
`vegetation` holds the water's **temperature** (`cold`/`temperate`/`warm`), and
`modifier` holds two unrelated things — `coral`/`ice` describe the water and are
matched against it, while `swarm` describes behaviour and is asked separately.

## Statistics and the guide

Every catch adds to a per-player running total: how many, and the longest and
shortest ever landed. Totals are aggregated rows rather than a log of every catch,
so a player fishing all year adds rows equal to the number of distinct fish.

`/bs guide` opens a menu of categories with completion bars; each category lists
its entries with their records. Undiscovered entries show as `???` and give away
nothing but the conditions needed to find them.

Milestones sit at 1, 5, 10, 20, 50, 100, 200, 500, 750 and 1000 catches, shown as
Roman numerals **I** to **X**. Crossing one announces itself in chat — on the
catch that lands exactly on the threshold, not on every catch past it.

Without a database the plugin still fishes; only the totals and the guide go
empty.

Definitions are validated on startup, and anything rejected is reported on the
console with the file, the fish and what is wrong. A broken file costs its own
fish and nothing else.

## Nexo items

Nexo has no API for creating items at runtime, so BountyfulSeas writes
`plugins/Nexo/items/bountyfulseas/fish.yml` during `onLoad`, before Nexo reads its
items. In that file:

- `material`, `Pack` and `Mechanics` are written once and never touched again, so
  swapping a texture by hand survives restarts.
- `itemname`, `lore` and the food components are derived from the fish definition
  and rewritten every start, so they cannot go stale.

Items default to a non-food material, which is why a fish is inedible unless its
definition asks for it.

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/bs debug` | `bountyfulseas.debug` | The water under your bobber and the odds of every fish there |
| `/bs watermapregenerate` | `bountyfulseas.watermapregenerate` | Rescans the world, reloads the map and redraws the overlay |

`/bs watermapregenerate` needs `water-map.analyzer` in `config.yml` pointing at the
`water-analyzer` executable.

## Building

Java 25 and Paper 26.2.

```bash
gradle build
```

`gradle deployToTestServer` copies the jar into a sibling test server's plugin
folder; override with `-PtestServerPluginFolder=<path>`.

## Layout

| Package | Imports from this project |
|---|---|
| `fish` | nothing - reads the YAML definitions |
| `water` | nothing - reads `water_regions.bin` |
| `nexo` | nothing - writes Nexo item YAML |
| `pl3xmap` | nothing - draws map layers |
| `fishing` | `fish` - picks the catch |
| root | all of them - plugin lifecycle and wiring |

Each boundary is an interface owned by the consumer, so the modules that talk to
outside systems never learn what a fish is.
