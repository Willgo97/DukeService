# Demo — 5 minuten

Voor technical support en product management. De app draait volledig offline;
zet gerust vliegtuigmodus aan voordat je begint, dat maakt indruk en het werkt
gewoon door.

## 1. Waar het om draait (30 sec)

Open de app. Het startscherm laat zien wat erin zit: **44 storingen,
32 componenten, 49 servicemenu-onderwerpen, 4.602 onderdeelregels.**

> "Dit is alles wat in de handleidingen en onderdelenboeken staat, doorzoekbaar
> op de telefoon, zonder internet."

## 2. De scanner (90 sec) — het sterkste stuk

Tik op **Scan** rechtsonder.

**a. Richt op het display van een machine** met een storing erop.
De app leest de melding en toont meteen oorzaak en stappen. Dit werkt ook als
de OCR er een letter naast zit: "Koffiem**n**olen blokkeert" wordt nog steeds
herkend als *Grinder blocked*.

**b. Richt op een onderdeellabel.** `5KAF119` → *Waste bucket CEC, Virtu,
tekening 1014 Cabinet 9CEC*. Eén tik zet het nummer op het klembord.

**c. Richt op het typeplaatje.** Modelcode en serienummer worden gelezen; de
app weet welke machine voor je staat.

Geen camera bij de hand? **Uit foto** doet hetzelfde met een foto uit de
galerij — handig als een klant je een foto stuurt.

## 3. Een storing oplossen (60 sec)

Zoek op `lekbak` of tik een storing uit *Vaak nodig*.

Laat zien: de Nederlandse schermtekst, de **technische** oorzaak uit de
technische handleiding ("de niveausensor in de boiler heeft geen waterniveau
gedetecteerd"), de stappen, en de doorverwijzing naar de bijbehorende
procedure — apart voor CoEx en CoEx XL.

Onderaan: **Vastzetten** en **Delen** — de storing als tekst naar een collega
of de klant.

## 4. Onderdelen: de tekening werkt mee (60 sec)

Onderdelen → *1014 Cabinet 9CEC*.

De explosietekening uit het onderdelenboek staat erbij, en de ballonnummers zijn
aanklikbaar: tik nummer 1 aan en het onderdeel verschijnt eronder —
`5MAF101-RAL9005`, back bracket. Andersom werkt ook: tik een onderdeel in de
lijst en de ballon licht op. Eén tik op het nummer zet het op het klembord.

> "De nummers stonden als pixels in de tekening. Die zijn er één keer uit
> gelezen en getoetst aan de onderdelentabel, dus wat je aantikt klopt."

1.990 van de 2.765 posities zijn zo aanklikbaar.

## 5. Techniek (45 sec)

Machines → **Hoe de machine werkt** → *Schematisch diagram van het
watersysteem*.

Het complete schema met legenda, inzoombaar. 32 componenten: inlaatventiel,
drukregelaar, waterstroommeter, clixon, besturingsprint. Elk met de
bijbehorende pagina uit de technische handleiding.

## 6. Onderhoud en servicemenu (45 sec)

**Onderhoud**: de checklists per brewer, afvinkbaar. Vinkjes gelden voor
vandaag; morgen staat de lijst weer open.

**Servicemenu**: wat elke functie doet, met wachtwoordniveaus. Zoek op
`ontkalken` — 46 stappen plus de vijf originele handleidingpagina's.

## 7. Machines (30 sec)

Foto's van het hele assortiment. Per machine: uitvoeringen (Avy heeft er drie),
typecodes, en — wat bij storingzoeken als eerste telt — **welk servicemenu
erop draait**: oud (ICeQ2), nieuw, of allebei vanaf software 6.30/6.40.

Onderaan elke machine een notitieveld voor serienummer en locatie.

## 8. Instellingen (20 sec)

Het tandwiel rechtsboven: licht/donker, en — nuttiger — **taal van de
meldingen**. Staat de machine op Engels, dan wil je de Engelse schermtekst
bovenaan zien; staat hij op Nederlands, andersom. De andere taal blijft eronder
staan.

---

## Vragen die gaan komen

**"Waar komt de data vandaan?"**
Uit de handleidingen, technische handleidingen en onderdelenboeken. Zoek →
*Waar komt dit vandaan?* toont de bronnen per onderdeel; elke storing noemt
onderaan het documentnummer.

**"Werkt het echt offline?"**
Ja, inclusief de tekstherkenning. Er zit geen netwerkcode in de app.

**"Kan dit ook voor Lina / Nio Next / Blu?"**
De structuur staat er; alleen de documentatie van die modellen ontbreekt nog.
Aanleveren en het staat erin.

**"Van wie is dit?"**
Privé gemaakt, in eigen tijd, op eigen apparatuur — zie `LICENSE`. De inhoud
van de handleidingen blijft van De Jong DUKE en zit niet in de repo.

## Wat er nog niet in zit

- Alleen de CoEx-familie is technisch uitgewerkt (Avy-handleiding als bron)
- Lina, Nio Next, Blu, Edge en Vareo: alleen brochuregegevens
- Geen koppeling met ConnectMe of het onderdelenbestelsysteem
- De scanner leest tekst, geen barcodes of QR
- Geen chatbot: elke regel in de app is herleidbaar tot een handleiding, en dat
  is precies wat een taalmodel van telefoonformaat niet kan garanderen
- 3D-modellen zouden STEP-bestanden vragen; de explosietekeningen doen nu het werk
