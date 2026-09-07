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
- Picks a fish by spawn weight from those that accept the water type, terrain,
  climate, depth, modifiers and current world conditions.
- Generates a Nexo item definition for every fish, so a new fish only needs a
  texture drawn for it.
- Draws the fishable water on [Pl3xMap](https://modrinth.com/plugin/pl3xmap) as
  traced polygons, one per region.

## Fish definitions

One file per category in `plugins/BountyfulSeas/fishes/`. The file name is the
category, and `_schema.yaml` documents every field.

```yaml
herring:
  fish_name: "<gray>Hering</gray>"
  fish_item: "nexo:herring"
  lore:
    - "Travels in silver walls that turn as one."

  min_weight: 0.2
  max_weight: 1.1
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
A fish without an `on_eat` block cannot be eaten at all.

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

`gradle deployToTestServer` copies the jar into a sibling Interconnect checkout's
plugin folder; override with `-PtestServerPluginFolder=<path>`.

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
