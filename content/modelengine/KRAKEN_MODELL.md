# Kraken: Modelle für ModelEngine

Die Skills in `content/mythicmobs/Kraken/skills/Kraken.yml` rufen diese Modelle und
Animationen beim Namen auf. Ein Name, der hier fehlt, wird im Spiel einfach nicht
abgespielt - also genau so benennen.

## Für alle Modelle

- **Wasserlinie = Modell-Ursprung.** BountyfulSeas setzt Kopf, Tentakel, Nessy
  und die Wellen genau auf die Wasseroberfläche (Boden des obersten Wasserblocks
  + 14 px, NoAI, keine Schwerkraft). Alles unter dem Ursprung ist unter Wasser.
  Von Hand gespawnt (`/mm mobs spawn`) also mit y = Wasserblock + 0.875.
- **Vorderseite ist −Z.** Vor gezielten Angriffen dreht der Skill den Mob zum
  Spieler (`look`), die Animation schlägt bzw. schießt also nach −Z.
- **Loop-Modi:** `idle` und die mit „Schleife“ markierten loopen; mit „hält“
  markierte stehen auf *hold* (letztes Bild bleibt, bis der Skill den Zustand
  entfernt); alle anderen *once* - danach fällt das Modell auf `idle` zurück.
- Angriffe, die direkt an eine vorherige Pose anschließen, startet der Skill
  ohne Überblendung (`li=0`): Anfangsbild = Endbild der Pose davor.

## kraken_head

Ein riesiger Kopf, der fast immer bis knapp unter die Augen im Wasser liegt.
Etwa 5 x 4 x 5 Blöcke, Mantel nach hinten gezogen, zwei große Augen, Schnabel unten,
vier kurze Stummel-Tentakel um den Schnabel. Dunkles Rot-Violett, hellere Unterseite,
Saugnäpfe an den Stummeln.

Bones: `root` > `mantle`, `head` > `eye_l`, `eye_r`, `beak`, `stub_1` bis `stub_4`;
`hitbox` als Hitbox-Bone.

| Animation | Dauer | Wofür |
|---|---|---|
| `idle` | Schleife | untergetaucht, nur Augen und Mantelspitze über Wasser, langsames Wogen |
| `idle_up` | Schleife | aufgetaucht: die Pose am Ende von `emerge`, atmet und wogt. Der Skill macht sie beim Auftauchen zur Grund-Animation (`defaultstate`) und beim Abtauchen wieder `idle` |
| `spawn` | 2 s | steigt das erste Mal auf, Wasser bricht |
| `emerge` | 2 s, hält | taucht für den Angriff ganz auf und bleibt oben, bis `submerge` kommt |
| `submerge` | 1,5 s | taucht wieder ab |
| `ultra` | 3 s | Mahlstrom: reißt den Schnabel auf, dreht sich, Stummel peitschen |
| `compress` | 1,25 s | zieht sich zusammen, Mantel bläht sich (Anzeige vor Welle/Kanone) |
| `shockwave` | 1 s | entlädt sich nach außen - beginnt in der Endpose von `compress` |
| `cannon` | 1,5 s | Schnabel nach vorn (−Z), Rückstoß beim Schuss - beginnt in der Endpose von `compress` |
| `death` | 3 s | sinkt seitlich ab |

## kraken_tentacle

Ein Tentakel, der aus dem Wasser ragt, etwa 6 Blöcke hoch. Sechs Segmente, die sich
nach oben verjüngen, Saugnäpfe auf der Innenseite, Spitze gekrümmt.

Bones: `root` > `seg_1` > `seg_2` > `seg_3` > `seg_4` > `seg_5` > `seg_6` > `tip`;
`hitbox` als Hitbox-Bone (für den Angelhaken wichtig: nicht zu schmal).

| Animation | Dauer | Wofür |
|---|---|---|
| `idle` | Schleife | wiegt sich, Spitze kräuselt sich |
| `spawn` | 1,5 s | schießt aus dem Wasser |
| `smash` | 1,4 s | hebt sich weit nach hinten (1,2 s Anzeige), dann Schlag nach vorn (−Z), Reichweite etwa 6 Blöcke |
| `grab` | 0,8 s | Spitze schnellt vor und rollt sich ein |
| `hold` | 4 s | hält gerollt, zuckt |
| `hurt` | 0,4 s | zuckt zurück |
| `death` | 2 s | sinkt schlaff ins Wasser |

## vfx_kraken_splash

Wasserfontäne als Effekt, 1 s, Animation `spawn` (der Skill startet sie ausdrücklich; `idle` darf dieselbe sein). Weiße und hellblaue Würfel, die
hochspritzen und auseinanderfallen.
