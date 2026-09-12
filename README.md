# DUKE Service

Offline naslagwerk voor servicemonteurs aan De Jong DUKE-koffiemachines.
Android, Kotlin, Jetpack Compose. Alles zit in de APK — geen netwerk nodig,
want in een kelder of serverruimte heb je dat toch niet.

## Wat zit erin

| | |
|---|---|
| 47 schermmeldingen | Nederlandse displaytekst, technische oorzaak en wat je controleert |
| 40 procedures | 274 stappen, met waarschuwingen en benodigdheden |
| 8 onderhoudschecklists | dag / week / maand / halfjaar, per brewer, afvinkbaar |
| 32 componenten | hoe watersysteem, boilers, ventielen, brewer en molen werken |
| 49 servicemenu-onderwerpen | wat elke functie doet, met wachtwoordniveaus |
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
- Technische handleidingen Avy (CND, XEA), Lua Instant, Nio CoEx XL, Rosa
- Gebruikershandleidingen Virtu `5DUCEK20I`, Lua `5DUXES20I`, Avy, Rosa
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
python3 tools/parse_parts.py        # onderdelenboeken -> parts.json
python3 tools/parse_components.py   # hoofdstuk 4-5 -> components.json + paginabeelden
python3 tools/parse_servicemenu.py  # hoofdstuk 6-7 -> servicemenu.json + paginabeelden
python3 tools/parse_drawings.py     # explosietekeningen -> tek/ + drawings.json
python3 tools/parse_photos.py       # machinefoto's uit de brochurecovers
python3 tools/render_hires.py       # tekeningen op 200 dpi, voor de herkenning
python3 tools/index_balloons.py     # ballonnummers -> hotspots.json
```

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
