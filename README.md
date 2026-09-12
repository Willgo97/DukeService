# DUKE Service

Offline naslagwerk voor servicemonteurs aan De Jong DUKE-koffiemachines.
Android, Kotlin, Jetpack Compose. Alles zit in de APK — geen netwerk nodig,
want in een kelder of serverruimte heb je dat toch niet.

## Wat zit erin

| | |
|---|---|
| 50 schermmeldingen | Nederlandse displaytekst, technische oorzaak en wat je controleert |
| 70 procedures | 502 stappen, met waarschuwingen en benodigdheden |
| 11 onderhoudschecklists | dag / week / maand / halfjaar, per brewer, afvinkbaar |
| 130 componenten | hoe watersysteem, boilers, ventielen, brewer en molen werken, per brewer |
| 148 servicemenu-onderwerpen | wat elke functie doet, met wachtwoordniveaus |
| 4.602 onderdeelregels | 1.547 unieke nummers uit zes onderdelenboeken |
| 11 machines | foto, afmetingen, typecodes, uitvoeringen, welk servicemenu |
| 89 explosietekeningen | aanklikbare ballonnen: 1990 van 2765 posities in 149 secties |
| Scanner | leest labels, typeplaatjes en schermmeldingen met de camera |

Eén zoekveld gaat overal tegelijk doorheen: schermmeldingen, procedures,
onderdeelnummers, componenten, servicemenu en machines.

## Servicemenu per machine

Wat er bij de machine als eerste toe doet, staat op elke machinekaart:

- **Oud servicemenu** — ICeQ2-besturing: Virtu, Zia, Nio, Edge, Vareo
- **Oud of nieuw** — Lua, Avy en (mogelijk) Rosa hebben op allebei gedraaid; vanaf software 6.30/6.40 is het nieuwe verplicht
- **Alleen nieuw** — Lina, Nio Next

## Thema

Losjes naar het nieuwe servicemenu: bijna-zwarte panelen met een lichtere
zijbalk, witte tekst voor wat actief is en grijs voor de rest, en één warm goud
accent op de bedieningselementen. Licht en donker, om te zetten met het icoon
rechtsboven (Systeem / Licht / Donker, wordt onthouden).

## Bronnen

De documentatie zelf staat in `manuals/` en blijft daar: die is auteursrechtelijk
beschermd door de fabrikant en gaat niet mee in git (zie `.gitignore` en `LICENSE`).

- Technische handleiding Avy CoEx Medium — `5DTCET10M` NL V1.0 — de Nederlandse
  brontekst voor storingen, componenten en servicemenu
- Technische handleidingen Avy CoEx Small `5DTCNT20M`, Avy CoEx XL `5DTXET20M`,
  Nio CoEx XL `5DTXKA20M`, Rosa Filterfresh `5DTFNV20M`, Lua Instant `5DTINS10M`
  — hier komen de CoEx XL-, Uni-Brewer- en Instant-onderdelen vandaan, en de
  storingen die alleen op die machines voorkomen
- Gebruikershandleidingen Virtu `5DUCEK20I`, Lua `5DUXES20I`, Avy `5DUXET20M`,
  Rosa `5DUFNV20M`
- Installatiehandleiding Touchless Interface `5DIAXA820`
- Onderdelenboeken Virtu (9CECK), Zia (9CECP), Nio (9CKA), Lua (9XEAS),
  Avy (9XEAT), Rosa (9FNDV)
- Productbrochures van dejongduke.com

## Bouwen

```bash
./build.sh          # release  -> ../DukeService.apk (+ kopie naar ../DukeService-share/)
./build.sh debug    # debug
./build.sh install  # release + adb install
```

Verwacht `JAVA_HOME=~/.local/opt/jdk17` en `ANDROID_HOME=~/Android/Sdk`, of zet
ze zelf. Zonder keystore wordt met de debug-sleutel ondertekend; met een eigen
keystore via `DUKE_STORE_FILE` / `DUKE_STORE_PASSWORD` / `DUKE_KEY_ALIAS` /
`DUKE_KEY_PASSWORD` wordt het een release-build.

## Data opnieuw opbouwen

De scripts in `tools/` lezen de PDF's uit `manuals/` en schrijven zowel `data/`
(leesbaar) als `app/src/main/assets/` (compact):

```bash
./tools/build_data.sh               # alles in de juiste volgorde
```

Of los, waarbij `parse_components.py` de map `assets/img` leegmaakt en dus vóór
de andere paginarenderaars moet draaien:

```bash
python3 tools/parse_parts.py        # onderdelenboeken -> parts.json
python3 tools/parse_components.py   # hoofdstuk 4-5 -> components.json + paginabeelden
python3 tools/parse_servicemenu.py  # hoofdstuk 6-7 -> servicemenu.json + paginabeelden
python3 tools/parse_procedures.py   # Rosa-procedures + onderhoudsschema's
python3 tools/parse_faults.py       # storingen uit tien boeken -> faults_raw.json
python3 tools/merge_faults.py       # vouwt die in faults.json
python3 tools/parse_drawings.py     # explosietekeningen -> tek/ + drawings.json
python3 tools/parse_photos.py       # machinefoto's uit de brochurecovers
python3 tools/render_hires.py       # tekeningen op 200 dpi, voor de herkenning
python3 tools/index_balloons.py     # ballonnummers -> hotspots.json
```

Drie bestanden in `data/` zijn met de hand geschreven en worden niet
gegenereerd: `procedures_base.json` (de Nederlandse procedures uit de Virtu- en
Lua-handleiding), `maintenance_base.json` en `procedures_touchless.json`. De
parsers lezen die en schrijven het resultaat naar `procedures.json` en
`maintenance.json`.

Niet verwerkt: `W100 User manual English.pdf`. Dat is een andere machine met een
eigen documentatiefamilie (`T0642EN00`, twee kolommen, geen hoofdstukindeling
zoals de DUKE-boeken) en de W100 staat ook niet in de machinelijst van de app.

De ballonnummers op de tekeningen zijn pixels, geen tekst. Ze worden in twee
stappen gelezen: de tekstherkenning die de app zelf al meedraagt leest de
tweecijferige ballonnen (`DrawingIndexer`, alleen in een debug-build,
Instellingen → Ontwikkelen), en `index_balloons.py` gebruikt die als voorbeeld
om de rest te herkennen — alle ballonnen in een boek komen uit dezelfde
CAD-export, dus de cijfers zijn identiek. Wat eruit komt wordt getoetst aan de
posities in de onderdelentabel, dus een verkeerde lezing valt af.

`faults.json`, `machines.json`, `procedures.json`, `maintenance.json` en
`specs.json` zijn met de hand samengesteld; `procedures.json` komt uit
`procedures_a.json` (CoEx) plus `procedures_b.json` (koudwater, scherm, CoEx XL).
Na een handmatige wijziging de compacte kopie bijwerken:

```bash
for f in machines faults procedures maintenance specs; do
  python3 -c "
import json
d = json.load(open('data/$f.json'))
json.dump(d, open('app/src/main/assets/$f.json','w'), ensure_ascii=False, separators=(',',':'))
"
done
```

## Delen met een telefoon

`../DukeService-share/serve.sh` zet de APK met een QR-pagina op het lokale
netwerk. Alleen die map wordt gedeeld.

## Let op

Privéwerk, geen officiële uitgave van De Jong DUKE en niet door hen goedgekeurd.
Bij twijfel zijn de handleiding en het typeplaatje in de machine leidend.
Zie `LICENSE`.
