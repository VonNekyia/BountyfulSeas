# Nessy: Modelle für ModelEngine

Die Skills in `content/mythicmobs/Nessy/skills/Nessy.yml` rufen diese Modelle und
Animationen beim Namen auf. Ein Name, der hier fehlt, wird im Spiel einfach nicht
abgespielt - also genau so benennen.

## nessy

Eine Seeschlange mit langem Hals, alt und eher scheu als böse. Etwa 3 Blöcke breit,
Hals aufgerichtet 7 Blöcke hoch, dazu zwei Rückenbuckel hinter dem Hals.
Moosgrün mit dunklen Flecken, Bauch heller (Schilfgelb). Am Hals hängen Algen
und Seerosenblätter, auf dem Rücken wachsen ein paar kleine Muscheln. Kleiner,
freundlich-runder Kopf mit großen, leuchtend grünen Augen. Die Augen sind dasselbe
Grün wie das Blinzeln in der echten Welle (`#b8ff6a`), so lernt man den Hinweis.

Bones: `root` > `body` > `neck_1` > `neck_2` > `neck_3` > `neck_4` > `head` >
`jaw`, `eye_l`, `eye_r`; `body` > `hump_1`, `hump_2`, `flipper_fl`, `flipper_fr`,
`flipper_bl`, `flipper_br`, `tail_1` > `tail_2` > `tail_3`.
`hitbox` als Hitbox-Bone, um Hals und Kopf (die Angel muss ihn treffen können).

Wasserlinie, Vorderseite und Loop-Modi: wie in KRAKEN_MODELL.md, „Für alle Modelle“
(Wasserlinie = Ursprung).

| Animation | Dauer | Wofür |
|---|---|---|
| `hidden` | Schleife | alles unter der Wasserlinie, nichts zu sehen. Der Skill macht sie im Nebel zur Grund-Animation (`defaultstate`), aufgetaucht ist es wieder `idle` |
| `peek` | 2 s | Kopf und ein Stück Hals tauchen kurz auf, blinzeln, weg (Sichtung) - spielt über `hidden` |
| `emerge` | 2,5 s | richtet sich ganz auf, Wasser läuft vom Hals |
| `idle` | Schleife | aufgerichtet, Hals wiegt sich, Buckel heben und senken sich |
| `submerge` | 1,5 s | gleitet zurück unter Wasser |
| `sweep` | 3,5 s | Hals weit zur Seite, 3 s Anzeige (holt langsam weiter aus, bäumt sich auf, zittert, ab 2,2 s Maul auf), dann bei 3,0 s ein Schwung im Kreis auf Kopfhöhe. Der Skill trifft bei Tick 60 (Boote brechen, wer nicht abgetaucht ist, stirbt) |
| `sing` | 3 s | Kopf in den Nacken, Maul auf, Hals vibriert |
| `slam` | 1,2 s | Hals hoch, dann mit Kopf und Brust aufs Wasser |
| `tired` | 6 s | Kopf hängt, Hals sinkt aufs Wasser, schweres Atmen |
| `fight_line` | Schleife | zerrt wild hin und her wie ein großer Fisch an der Angel |
| `stranded` | 15 s, hält | liegt seitlich, Hals flach, zappelt schwach |
| `death` | 3 s | legt den Kopf ab und sinkt langsam |

## nessy_baby

Ein Junges, etwa 1 Block lang: großer Kopf, kurzer Hals, Flossen zu groß für den
Körper. Gleiche Farben wie Nessy, nur heller.

Bones: `root` > `body` > `neck` > `head`, `flipper_l`, `flipper_r`, `tail`;
`hitbox` als Hitbox-Bone.

| Animation | Dauer | Wofür |
|---|---|---|
| `idle` | Schleife | paddelt |
| `spawn` | 0,8 s | springt aus dem Wasser |
| `lunge` | 0,8 s | duckt sich (0,5 s Anzeige), dann Sprung nach vorn mit offenem Maul |
| `death` | 1 s | dreht sich auf den Rücken |

## Ohne eigenes Modell

Die Wellen, die Flutwelle und die Wasserhosen sind reine Partikel.
