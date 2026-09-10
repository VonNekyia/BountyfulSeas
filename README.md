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
5. **How big?** Length is rolled from the fish's maximum along the size curve.

The tier is drawn **before** the entry on purpose. Rolling in one pass would make
a legendary likelier in water that happens to hold three of them, which is the
opposite of what a rarity is for. Water holding one legendary and water holding
ten give it the same odds.

### Angling level

Every fish carries a `level` and does not bite until the angler has reached it -
it is not merely rarer below that, it is out of the draw entirely, so it never
takes a share of the odds it cannot pay out. The guide shows what is still locked
and at what level it opens.

Levels come from milestones. Each one a fish passes is worth

```
experience = experience-per-step  x  which milestone it is (1 to 10)
                                  x  that fish's level
```

so a fish's tenth milestone pays ten times its first - it took a thousand catches
rather than one - and a hard fish pays better than an easy one, which is the
reason to go after what has just opened up instead of staying in the shallows.

Reaching level *n* costs `experience-base x (n - 1) ^ steepness` in total. With
the shipped numbers:

| Level | total xp | | one fish, all ten milestones | xp |
|---|---|---|---|---|
| 2 | 100 | | a level 1 fish | 1 375 |
| 5 | 1 213 | | a level 5 fish | 6 875 |
| 10 | 5 220 | | a level 12 fish | 16 500 |
| 25 | 30 506 | | a level 25 fish | 34 375 |
| 50 | 110 243 | | | |

**Nothing about the level is stored.** Experience is worked out from the catch
counts that are already kept, so there is no second table, no extra write, and no
way for the two to drift apart. The cost is that working it out reads a player's
whole row set - a database call, and a bite has to be answered on the server
thread - so the answer is held in memory, refreshed when a player joins and again
after any catch that crosses a milestone.

Without a database there are no counts, so there is no level either. The gate
then stands open rather than locking every fish away forever, and the server log
says so at startup.

### How big it is

A fish definition gives one number, `max_length`. Everything below it comes from
one curve, the shape fisheries science uses for the length composition of a real
stock ([Beverton and Holt](https://www.fao.org/4/T0535E/T0535E03.htm)): fish die
off steadily while their growth slows towards a ceiling, so most of what is
swimming is middling and only a few live long enough to get big.

```
P(longer than l) = ((max - l) / (max - smallest)) ^ shape
```

`shape` is Z/K, the mortality-to-growth ratio - how many fish die for every bit
of growing the survivors do. Real stocks sit around 1.5 to 3. The same model,
read backwards, is the classic Beverton-Holt mean-length estimator
`Z/K = (L∞ - L̄) / (L̄ - Lc)`, which is a good check that the curve is the real
one rather than something that merely looks skewed.

Two things are pinned rather than left to the curve:

- **`max_length` is the record, not the ceiling.** It comes up once in a million
  catches. The values shipped sit just above the largest specimen each species is
  known to reach, so a record fish is a hair beyond anything ever landed.
- **Nothing rolls below `smallest`**, a fraction of the maximum. A hook does not
  bring up fry; that is gear selectivity, the same reason the formula starts
  there rather than at zero. There is no `min_length` in a fish file.

With the defaults, for a fish that reaches 100 cm:

| | length |
|---|---|
| shortest possible | 25.00 cm |
| half of all catches under | 40.63 cm |
| 4 in 100 reach | 75 cm |
| 3 in 1000 reach | 90 cm |
| 1 in 1 000 000 reaches | 100 cm |

Lengths are measured to two decimals, so a catch reads `40.63 cm`. Only the
longest is remembered - nobody sets out to land the smallest herring anyone has
ever seen.

### Rarity tiers

| Tier | Default chance | Notes |
|---|---|---|
| `uncommon` | 75 | the buffer: enchantment bonuses come out of here |
| `rare` | 15 | |
| `misc` | 5 | junk, not a fish, no length |
| `epic` | 4 | |
| `legendary` | 0.5 | |
| `treasure` | 0.5 | enchanted books and the like, not a fish, no length |
| `mythic` | 0.05 | outside the hundred on purpose, and never lifted |
| `signature` | 0 | never rolled; for hand-placed fish |

Chances are **weights**. The base set adds up to 100 so the numbers can be read
as percentages, but only the tiers actually present at a spot take part in the
draw, so a tier nobody has written an entry for costs nothing.

### Rod enchantments

Two enchantments, two jobs, and neither touches the other's tiers.

- **Luck of the Fish** (levels I to V) lifts `rare`, `epic` and `legendary`.
  The plugin's own enchantment, registered with the server at startup and
  obtainable from an enchanting table like any other - no datapack to install.
- **Luck of the Sea** lifts `treasure`, and only `treasure` - that is where the
  enchanted books are.
- **Lure** is off by default, keeping its vanilla job of making fish bite sooner.
  Set its rate above 0 to have it lift the same tiers as Luck of the Fish; the
  two then multiply.

Each level adds a share of the tier's **base** weight, and everything added is
taken back out of `uncommon`. That is what keeps the two independent: a rod
carrying both applies each in full, and the total still comes to what it was.
`misc` and `mythic` are lifted by nothing at all.

| | `uncommon` | `rare` | `epic` | `legendary` | `treasure` |
|---|---|---|---|---|---|
| base | 75 | 15 | 4 | 0.5 | 0.5 |
| Luck of the Fish I | 73.05 | 16.5 | 4.4 | 0.55 | 0.5 |
| Luck of the Fish V | 65.25 | 22.5 | 6 | 0.75 | 0.5 |
| Luck of the Sea V | 74.75 | 15 | 4 | 0.5 | 0.75 |

Both rates are set in `config.yml`.

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

  max_length: 46

  water_type: [ocean]
  vegetation: [cold, temperate]
  depth: [shallow]

  spawn_weight: 140
  rarity: uncommon
  level: 1

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
| `level` | nothing - the experience curve and what a milestone pays |
| `enchantment` | nothing - defines and registers the plugin's enchantments |
| `fishing` | `fish` - picks the catch |
| root | all of them - plugin lifecycle and wiring |

Each boundary is an interface owned by the consumer, so the modules that talk to
outside systems never learn what a fish is.

This is a Paper plugin (`paper-plugin.yml`), which is what makes the enchantment
possible: registries are only open during bootstrap, a stage a Bukkit plugin
never sees. The two costs that come with it are in the root package -
`BountyfulSeasLibraries` fetches the runtime libraries a `libraries` block used
to, and `/bs` is registered through the command lifecycle event.
