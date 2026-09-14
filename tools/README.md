# Ballonnummers (oude pijplijn)

Wat hier nog staat, hoort bij één ding: de nummers die op de
explosietekeningen gedrukt staan. Die zijn pixels, geen tekst, en worden in
twee stappen gelezen — de tekstherkenning die de app zelf meedraagt leest de
tweecijferige ballonnen (`DrawingIndexer`, alleen in een debug-build,
Instellingen → Ontwikkelen), en `index_balloons.py` gebruikt die als voorbeeld
om de rest te herkennen.

Het resultaat staat in `data/hotspots.json`, met de tekeningen waar het bij
hoort in `data/tek_ballon/` en `data/drawings_ballon.json`. Die drie worden
door `kbtools/appdata.py` overgenomen, zodat de ballonnen blijven werken op de
89 tekeningen waarvoor ze gelezen zijn.

De rest van de oude pijplijn is vervangen door `kbtools/`, dat alle 207 boeken
leest in plaats van tien. Deze scripts gaan nog uit van de oude mappenindeling
van `manuals/` en van de Nederlandse veldnamen in `data/`; wie ze weer wil
draaien, past die eerst aan.
