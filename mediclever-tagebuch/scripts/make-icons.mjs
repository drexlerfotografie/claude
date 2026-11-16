// Erzeugt app/src/icons.gen.ts aus einer kuratierten Auswahl des Icon-Sets Lucide (ISC-Lizenz, siehe docs/lizenzen.md).
// Format je Zeile: Schlüssel[=Lucide-Name] | Beschriftung und Suchwörter
import { readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const LIST = {
  'Bedienung': `plus|Plus;minus|Minus;x|Schließen;check|Haken;chevron-left|Zurück;chevron-right|Weiter;chevron-down|Aufklappen;chevron-up|Zuklappen;search|Suche;calendar|Kalender;
    chart-column|Statistik;list|Liste;ellipsis|Mehr;star|Stern Favorit;trash=trash|Löschen;pencil|Bearbeiten;archive|Archiv;archive-restore=archive-arrow-up|Wiederherstellen;
    grip-vertical|Ziehen;image|Bild Foto;image-plus|Foto hinzufügen;mic|Mikrofon Sprachnotiz Konzert;square|Stopp;play|Abspielen;settings|Einstellungen;lock|Sperre;bell|Erinnerung;
    target|Ziel;flag|Wichtiger Tag;trophy|Erfolg Pokal;rotate-ccw|Rückblick;download|Herunterladen;upload|Hochladen;bot|KI Assistent;shield|Sicherheit;type|Schrift;
    sliders-horizontal|Regler Skala;layout-grid|Raster;file-text|Bericht Dokument;info|Hinweis;alert=triangle-exclamation-point|Warnung;arrow-up|Hoch;arrow-down|Runter;arrow-right|Pfeil;
    log-out|Abmelden;share-2|Teilen;copy|Kopieren;filter=funnel|Filter;tag|Aktivität;tags|Aktivitäten;folder|Gruppe;merge|Zusammenführen;repeat|Wiederholung;history=clock-arrow-left|Verlauf;
    circle-check|Erledigt;circle-plus|Hinzufügen;undo-2|Rückgängig;list-ordered|Nummerierte Liste;monitor|System;eye|Anzeigen;eye-off|Verbergen;link|Link;palette|Farben Malen Kreativ;
    sun-moon|Hell Dunkel;notebook-pen|Notiz Vorlage;external-link|Öffnen;user|Konto Person Allein;camera|Kamera Foto`,
  'Alltag': `circle|Neutral Punkt;home=house|Zuhause Haushalt;briefcase|Arbeit Dienst Büro;shopping-cart|Einkaufen;shopping-bag|Shopping;book-open|Lernen;graduation-cap|Fortbildung Studium Schule;
    clock|Uhr Schicht;timer|Überstunden Zeit;alarm-clock|Wecker;hourglass|Warten;coffee|Kaffee Pause Frei;spray-can|Putzen;shirt|Wäsche;washing-machine|Waschen;sofa|Sofa Ausruhen;lamp|Lampe;
    wallet|Geld;banknote|Geld Gehalt;piggy-bank|Sparen;gift|Geschenk;phone|Telefon;smartphone|Handy;laptop|Computer;code|Programmieren;mail|Post;wrench|Reparieren Werkzeug;hammer|Heimwerken;
    scissors|Friseur Basteln;car|Auto Fahren;train-front|Bahn Zug;bus|Bus;bike|Fahrrad Rad;plane|Reise Flug;ship|Schiff;map-pin|Ort;luggage|Koffer Reise;building|Stadt Büro;school|Schule;store|Laden`,
  'Dienst und Gesundheit': `sunrise|Frühdienst Morgen;sunset|Spätdienst Abend;moon|Nachtdienst Nacht Schlaf;cloud-moon|Mittagsschlaf;siren|Einsatz Rettungsdienst;ambulance|Rettungswagen;hospital|Klinik Krankenhaus;
    stethoscope|Arzt Praxis;flame|Feuerwehr Feuer;heart-pulse|Puls Herz;thermometer|Krank Fieber;pill|Medikament Tablette;syringe|Impfung;bandage|Verletzung Pflaster;brain|Kopf Kopfschmerzen Lernen;
    droplet|Periode Tropfen;activity|Stress Belastung;battery|Energie;battery-low|Müde Erschöpft;bed|Bett Schlaf Ausgeschlafen;glass-water|Wasser Trinken;weight|Gewicht;zap|Schmerz Energie Blitz;
    glasses|Brille;shower-head|Duschen;bath|Baden;cigarette|Rauchen;leaf|Entspannung Yoga Meditation`,
  'Menschen': `users|Familie Gruppe;handshake|Freunde Treffen;heart|Partnerschaft Liebe;user-check|Kollegen;baby|Kind Baby;heart-handshake|Ehrenamt Helfen;message-circle|Gespräch;party-popper|Party Feier;
    cake|Geburtstag;drama|Theater;church|Kirche;thumbs-up|Gut;thumbs-down|Schlecht;heart-crack|Streit Kummer`,
  'Freizeit': `dumbbell|Sport Training Fitness;footprints|Spazieren Laufen Joggen;mountain|Wandern Berg;waves=waves-horizontal|Schwimmen Meer;volleyball|Ballsport;swords|Kampfsport;person-standing|Dehnen Bewegung;
    book|Lesen Buch;music=headphones|Musik hören;guitar|Gitarre Musik;piano|Klavier;disc=disc-3|Tanzen Platte;film|Film Kino;tv|Serien Fernsehen;gamepad-2|Spielen Gaming;puzzle|Puzzle Rätsel;dices|Brettspiel;
    pen-line|Schreiben;paintbrush|Malen;newspaper|Nachrichten;ticket|Veranstaltung;tent|Camping;compass|Ausflug;globe|Welt;trees|Natur Wald Draußen;tree-pine|Wald;palmtree=tree-palm|Urlaub;
    flower-2|Garten Blume;sprout|Pflanzen;dog|Hund;cat|Katze;paw-print|Haustier;bird|Vogel;fish|Angeln Fisch;sparkles|Besonders;crown|Erfolg;medal|Medaille;award|Auszeichnung;gem|Edel`,
  'Essen': `utensils|Essen Restaurant;chef-hat|Kochen;cooking-pot|Kochen Topf;pizza|Pizza Fast Food;salad|Salat Gesund;apple|Obst;sandwich|Brot;soup|Suppe;croissant|Frühstück;cookie|Süßes;ice-cream-cone|Eis;
    candy|Süßigkeiten;wine|Wein Alkohol;beer|Bier`,
  'Wetter und Stimmung': `sun|Sonne Sonnig;cloud-sun|Heiter Wetter;cloud|Bewölkt;cloud-drizzle|Niesel;cloud-rain|Regen;cloud-lightning|Gewitter;snowflake|Schnee Kalt;wind|Wind;rainbow|Regenbogen;umbrella|Schirm;
    tornado|Chaos;gauge|Messwert Skala;face-grinning|Strahlend;face-slightly-smiling|Zufrieden;face-neutral|Neutral;face-slightly-frowning|Bedrückt;face-angry|Wütend`,
};

const base = resolve('node_modules/lucide/dist/esm/icons');
const nodes = {};
const meta = [];
for (const [cat, block] of Object.entries(LIST)) {
  for (const raw of block.split(';')) {
    const item = raw.trim();
    if (!item) continue;
    const [names, label] = item.split('|');
    const [key, lucide = key] = names.split('=');
    const mod = await import(pathToFileURL(resolve(base, lucide + '.mjs')).href).catch(() => null);
    if (!mod) throw new Error('Symbol fehlt in Lucide: ' + lucide);
    nodes[key] = mod.default;
    meta.push({ key, label: label.trim(), cat });
  }
}
const version = JSON.parse(readFileSync('node_modules/lucide/package.json', 'utf8')).version;
const out = `// Automatisch erzeugt von scripts/make-icons.mjs – nicht von Hand ändern.
// Symbole aus Lucide ${version} (ISC-Lizenz, https://lucide.dev). Siehe docs/lizenzen.md.
export type IconNode = [string, Record<string, string>][];
export const ICONS: Record<string, IconNode> = ${JSON.stringify(nodes)};
export const ICON_META: { key: string; label: string; cat: string }[] = ${JSON.stringify(meta)};
`;
writeFileSync('app/src/icons.gen.ts', out);
console.log(`${meta.length} Symbole geschrieben (${Math.round(out.length / 1024)} kB)`);
