# Demo — 5 minuten

Voor technical support en product management. De app draait volledig offline;
zet gerust vliegtuigmodus aan voordat je begint, dat maakt indruk en het werkt
gewoon door.

## 1. Waar het om draait (30 sec)

Open de app. Het startscherm laat zien wat erin zit: **48 storingen, 197
componenten, 226 servicemenu-onderwerpen, 38.427 onderdeelregels, 100
onderhoudskaarten, 185 handleidingen.**

> "Dit is alles wat in de service­documentatie staat — 185 boeken, 15.380
> pagina's — doorzoekbaar op de telefoon, zonder internet."

Alle merken en alle uitvoeringen: Virtu, Zia, Nio, Lua, Avy, Rosa, Lina, Blu en
Edge, elk met CoEx, CoEx XL, Filterfresh of Instant, in Small en Medium.

## 2. De onderhoudskaart (60 sec) — nieuw

**Onderhoud** → kies een machine → *Dagelijks onderhoud*.

Dit is de vouwkaart van de fabrikant zelf: genummerde stappen met precies de
tekening die erbij hoort. Tik **Stap voor stap**: één stap per scherm, groot,
doorswipen, met een balk die zegt hoe ver je bent.

> "Met een open machine voor je wil je geen pagina waar twaalf stappen langs
> scrollen. Je wilt de stap waar je bent, en de tekening ernaast."

## 3. De scanner (90 sec)

Tik op **Scan** rechtsonder.

**a. Richt op het display van een machine** met een storing erop.
De app leest de melding en toont meteen oorzaak en stappen. Dit werkt ook als
de OCR er een letter naast zit: "Koffiem**n**olen blokkeert" wordt nog steeds
herkend. Tekst die alleen toevállig een woord deelt — een label met "brewer"
erop — geeft géén melding: de hele zin moet er staan.

Wat gevonden is blijft een paar tellen staan, ook als je de camera wegdraait.

**b. Richt op een onderdeellabel.** `5KAF058` → *Suction filter*, met de
tekening en de machines waar het op zit. Eén tik zet het nummer op het
klembord.

**c. Richt op het typeplaatje.** Modelcode en serienummer worden gelezen; de
app weet welke machine voor je staat.

Geen camera bij de hand? **Uit foto** doet hetzelfde met een foto uit de
galerij — handig als een klant je een foto stuurt.

## 4. Een storing oplossen (60 sec)

Zoek op `lekbak` of tik een storing uit *Vaak nodig*.

Laat zien: de Nederlandse schermtekst, de **technische** oorzaak uit de
technische handleiding, de stappen, en de doorverwijzing naar de bijbehorende
procedure. Onder de melding staat voor welke machines hij geldt — dat zijn er
nu acht of negen per melding, omdat de techniek achter de deur gedeeld is.

Onderaan: **Vastzetten** en **Delen** — de storing als tekst naar een collega
of de klant.

## 5. Onderdelen: de tekening werkt mee (60 sec)

**Onderdelen** → kies merk *Virtu* → uitvoering *CoEx Medium* → *1014 Cabinet
9CEC*.

De uitvoering is een eigen keuze geworden: elke brewer en kastmaat heeft zijn
eigen onderdelenboek, en een Small-kast hoort geen Medium-nummers te tonen.

De explosietekening staat erbij, en op de tekeningen waarvan de ballonnummers
gelezen zijn, zijn die aanklikbaar: tik een nummer aan en het onderdeel
verschijnt eronder. Andersom werkt ook.

## 6. Techniek en servicemenu (45 sec)

**Techniek**: 197 componenten — inlaatventiel, drukregelaar,
waterstroommeter, clixon, besturingsprint — elk met de tekeningen uit de
technische handleiding. Filter op machine en de lijst wordt die van die
machine.

**Servicemenu**: 226 onderwerpen met het pad erheen (`Login > Hardware >
Calibrations > Ingredient Canisters`) en de stappen.

## 7. Machines (45 sec)

**Machines**: per merk de uitvoeringen met hun codes en serienummers, de
aanzichten met genummerde verwijzingen, en — wat bij storingzoeken als eerste
telt — **welk servicemenu erop draait**: oud (ICeQ2), nieuw, of allebei vanaf
software 6.30/6.40.

**Waar komt dit vandaan** (onderaan Instellingen): welke 185 boeken erachter
zitten en hoe ze zijn samengevoegd. De PDF's zelf zitten er niet in — die zijn
van de fabrikant; bij elke melding, elk component en elke procedure staat wel
onderaan uit welk boek hij komt.

## 8. Instellingen (20 sec)

Het tandwiel rechtsboven. Bovenaan de **taal**: negen, dezelfde negen waarin de
handleidingen bestaan. Zet hem op Suomi en de hele app staat in het Fins — de
knoppen én de teksten uit de boeken, voor zover de fabrikant ze vertaald heeft.
Daaronder licht/donker, en de **taal van de meldingen**: staat de machine op
Engels, dan wil je de Engelse schermtekst bovenaan zien; staat hij op
Nederlands, andersom.

---

## Vragen die gaan komen

**"Waar komt de data vandaan?"**
Uit alle 185 service­boeken: technische handleidingen, onderhoudskaarten,
gebruikers­handleidingen, snelstartgidsen, onderdelenboeken en brochures. Elke
melding, elk component en elke procedure noemt onderaan uit welk boek hij komt.

**"Hoe kan één handleiding voor negen merken gelden?"**
Omdat de machine achter de deur gedeeld is. De driletterige code (CND, XEA …)
zegt welke brewer en welke kastmaat; een Avy CND en een Zia CND zijn dezelfde
machine in een andere kast. De app zegt er expliciet bij welke machines een
tekst dekt, en de hoofdstukken die wél over de kast of het scherm gaan blijven
bij hun eigen merk.

**"Werkt het echt offline?"**
Ja, inclusief de tekstherkenning. Er zit geen netwerkcode in de app.

**"Kan ik die kennisbank ook ergens anders voor gebruiken?"**
Ja. Naast de app staat er een kennisbank in SQLite en JSON, met een
zoekindex en een beschreven model — te lezen vanuit elk programma. Zie
`kb/README.md`.

**"Van wie is dit?"**
Privé gemaakt, in eigen tijd, op eigen apparatuur — zie `LICENSE`. De inhoud
is van de fabrikant en wordt niet gepubliceerd.
