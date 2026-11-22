# Sondersignal – Entwurf NEF & RTW (Low Poly, three.js)

Prozedurale Entwürfe, zum Übertragen ins Projekt MediClever (Low-Poly-Zivilfahrzeuge).

- `src/core.js` – Geometrie-Helfer, Beklebungs-Atlas, Materialien, Fahrzeug-Basis (Türen, Lichter, Wrack, Innenraum-Gruppe)
- `src/nef.js` – NEF (Vito, Bayern-Karo, Schiebetür nur rechts)
- `src/rtw.js` – RTW (Sprinter, Strobel-Koffer, DRK-Rot/Gelb)
- `src/main.js`, `index.html` – Szene; `render.cjs` + `shots.json` rendern die Ansichten (`npm i three@0.160.0`, Playwright)

Stand: Außenhülle NEF ca. 5.560 / RTW ca. 5.010 Dreiecke, Innenraum separat (1.284 / 1.984).
Offen: Außenhülle auf je 7.000 bringen, Personen und Trage entfallen (kommen aus MediClever),
Wrack als eigene Version, Dateiaufteilung (Gesamt / Variante / Nicht-Variante) klären, Export als GLB.
