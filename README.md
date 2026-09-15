# DUKE Service

Offline naslagwerk voor servicemonteurs aan De Jong DUKE-koffiemachines.
Android, Kotlin, Jetpack Compose. Alles zit in de APK — geen netwerk nodig,
want in een kelder of serverruimte heb je dat toch niet.

Achter de app ligt een kennisbank die uit álle servicedocumentatie is
opgebouwd: 185 boeken, 15.380 pagina's, negen talen. Die kennisbank staat los
van de app en is ook door andere programma's te gebruiken — zie
[`kb/README.md`](kb/README.md).

## Wat zit erin

| | |
|---|---|
| 11 machinelijnen, 58 uitvoeringen | merk × brewer × kastmaat, met serienummers en welk servicemenu |
| 48 schermmeldingen | Nederlandse displaytekst, oorzaak en wat je controleert, met de machines waarvoor ze gelden |
| 100 onderhoudskaarten | de kaart van de fabrikant zelf: genummerde stappen met de tekening die erbij hoort |
| 131 procedures | stap voor stap, met waarschuwingen en benodigdheden |
| 197 componenten | hoe watersysteem, boilers, ventielen, brewer en molen werken, per brewer |
| 226 servicemenu-onderwerpen | wat elke functie doet, met het pad erheen |
| 38.427 onderdeelregels | 1.930 unieke nummers uit 60 onderdelenboeken |
| 1.736 explosietekeningen | met de onderdelentabel ernaast; op 75 ervan zijn de ballonnummers aanklikbaar |
| 24 aanzichten | voor-, achter- en binnenkant met genummerde verwijzingen |
| 185 boeken | welke handleiding waar vandaan komt |
| 20 tabellen technische gegevens | maten, aansluitwaarden, waterkwaliteit, geluidsdruk |
| Scanner | leest labels, typeplaatjes en schermmeldingen met de camera |
| 9 talen | de app zelf en de teksten uit de handleidingen: nl, en, de, fr, sv, no, da, fi, cs |

Eén zoekveld gaat overal tegelijk doorheen: schermmeldingen, procedures,
onderdeelnummers, componenten, servicemenu, onderhoudskaarten en machines.

## Stap voor stap

Onderhoudskaarten en procedures zijn ook als stappenloper te openen: één stap
per scherm, groot, met de tekening erbij, doorswipen met een balk die zegt hoe
ver je bent. Voor een open machine is dat de juiste vorm — een pagina waar
twaalf stappen langs scrollen niet.

## Machines

Elke machinelijn wordt in meerdere uitvoeringen verkocht: de driletterige code
zegt welke brewer en welke kastmaat, en dát is wat de documentatie uit elkaar
houdt. Een Avy CND en een Zia CND zijn dezelfde machine in een andere kast, en
de app behandelt ze ook zo: de techniek komt uit hetzelfde boek, de kast en het
scherm niet.

| code | brewer | kast | serie |
|---|---|---|---|
| CEC / CND | CoEx | Medium / Small | 7000-9000 / 6000-8000 |
| XEA / XNA | CoEx XL | Medium / Small | 19000 / 18000 |
| FEC / FND | Filterfresh | Medium / Small | 4000 / 2000 |
| IEA / INB | Instant | Medium / Small | 5000 / 1000 |
| CKA / XKA | CoEx / CoEx XL | Nio | — |

Servicemenu per machine: **oud** (ICeQ2) bij Virtu, Zia, Nio en Edge, **oud of
nieuw** bij Lua, Avy, Blu en Rosa (vanaf software 6.30/6.40 is het nieuwe
verplicht), **alleen nieuw** bij Lina en Nio Next.

## Talen

De handleidingen bestaan in negen talen, dus de app ook: Nederlands, English,
Deutsch, Français, Svenska, Norsk, Dansk, Suomi en Čeština. De app volgt de
taal van de telefoon en is in Instellingen om te zetten; die keuze geldt voor
zowel de knoppen als de tekst uit de handleidingen. Waar de fabrikant een boek
nooit vertaald heeft, staat de tekst in de taal die er het dichtst bij zit, met
een label dat zegt welke dat is.

Elk scherm bestaat in elke taal uit dezelfde onderwerpen: wat de Finse monteur
ziet is dezelfde lijst als bij de Nederlandse, alleen in zijn eigen woorden.

## Thema

Losjes naar het nieuwe servicemenu: bijna-zwarte panelen met een lichtere
zijbalk, witte tekst voor wat actief is en grijs voor de rest, en één warm goud
accent op de bedieningselementen. Licht en donker, om te zetten met het icoon
rechtsboven (Systeem / Licht / Donker, wordt onthouden).

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

De testen lezen de meegeleverde assets op de JVM, zodat gegevens die niet meer
bij het model passen hier stuklopen en niet pas op het eerste scherm:

```bash
./gradlew testDebugUnitTest
```

## Kennisbank opnieuw opbouwen

`kbtools/` leest de PDF's in `manuals/` en bouwt zowel `kb/` (de kennisbank) als
`app/src/main/assets/` (wat de app meeneemt):

```bash
python3 -m venv .venv && .venv/bin/pip install pymupdf numpy pillow
./kbtools/build_kb.sh
```

| stap | wat het doet |
|---|---|
| `inventory.py` | leest bestandsnamen en PDF-eigenschappen, vindt kopieën en oude drukken |
| `organize.py` | ordent `manuals/` per soort boek en zet verouderde drukken apart |
| `extract.py` | haalt elke pagina op als geordende tekst en snijdt hem op secties |
| `parts.py` | leest de onderdelentabellen terug uit de kolomposities |
| `facts.py` | maakt getypeerde records: melding, menu, component, specificatie … |
| `topics.py` | vouwt gelijke tekst samen en bepaalt voor welke machines hij geldt |
| `media.py` | rendert elke afbeelding één keer, ontdubbeld op inhoud |
| `emit.py` | schrijft `kb/` (JSON, JSONL, SQLite met zoekindex) |
| `schemas.py` | schrijft `kb/schema/` |
| `appdata.py` | maakt de assets voor de app |

Wat met de hand is geschreven blijft staan en wordt alleen aangevuld: de
Nederlandse storingsteksten, de machineteksten en de procedures in `data/`.

In `tools/` staat nog de oude pijplijn voor de ballonnummers op de tekeningen;
die zijn pixels en geen tekst. Zie `tools/README.md`.

## Bronnen

De documentatie zelf staat in `manuals/` en blijft daar: die is auteursrechtelijk
beschermd door de fabrikant en gaat niet mee in git (zie `.gitignore` en
`LICENSE`). `manuals/INDEX.md` zegt welk boek waar staat.

- 70 technische handleidingen (TM), in NL, EN, DE, FR-ca, SV, NO, DA, FI en CZ
- 50 onderhoudskaarten (SMI), één per machine en uitvoering
- 60 onderdelenboeken (Spare Parts Manual), 2022 tot 2026
- 5 snelstartgidsen (QSG)

Alles hier komt uit de servicemap van De Jong DUKE zelf. Documentatie die
eerder van de website was geplukt is er weer uit gehaald, zodat één bron
leidend is. Wat daarmee ook verdween: de enige technische handleiding van de
Nio en van de Rosa, de Nederlandse TM van de Lua Instant, de installatie-
handleiding van de Touchless Interface, vier Engelse gebruikershandleidingen en
de acht productbrochures. Wie die inhoud terug wil zet die PDF's in `manuals/`
en draait `./kbtools/build_kb.sh` opnieuw.

## Let op

Privéwerk, geen officiële uitgave van De Jong DUKE en niet door hen goedgekeurd.
Bij twijfel zijn de handleiding en het typeplaatje in de machine leidend.
Zie `LICENSE`.
