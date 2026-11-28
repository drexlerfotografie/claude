// SchichtClever – Entwurfsbilder. Erzeugt je Screen eine HTML-Datei.
const I = {
  shift:'<path d="M12 3a9 9 0 1 0 9 9"/><path d="M12 7v5l3 2"/><path d="M17 3h4v4"/>',
  moon:'<path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5z"/>',
  sun:'<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
  siren:'<path d="M6 18v-6a6 6 0 0 1 12 0v6"/><path d="M4 18h16v3H4z"/><path d="M12 3V1M4.2 6.2 3 5M19.8 6.2 21 5"/>',
  list:'<path d="M9 6h11M9 12h11M9 18h11"/><path d="M4 6h.01M4 12h.01M4 18h.01"/>',
  chart:'<path d="M4 20V10M10 20V4M16 20v-7M21 20H3"/>',
  target:'<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1"/>',
  more:'<circle cx="5" cy="12" r="1.2"/><circle cx="12" cy="12" r="1.2"/><circle cx="19" cy="12" r="1.2"/>',
  plus:'<path d="M12 5v14M5 12h14"/>',
  back:'<path d="M15 5l-7 7 7 7"/>',
  close:'<path d="M6 6l12 12M18 6 6 18"/>',
  chev:'<path d="M9 6l6 6-6 6"/>',
  focus:'<circle cx="12" cy="12" r="9"/><path d="M5.6 5.6l12.8 12.8"/>',
  eye:'<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
  filter:'<path d="M12 3a9 9 0 0 0 0 18z" fill="currentColor"/><circle cx="12" cy="12" r="9"/>',
  bell:'<path d="M6 16V11a6 6 0 0 1 12 0v5l2 2H4z"/><path d="M10 21h4"/>',
  shield:'<path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z"/><path d="M9 12l2 2 4-4"/>',
  lock:'<rect x="5" y="11" width="14" height="10"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
  dnd:'<circle cx="12" cy="12" r="9"/><path d="M7 12h10"/>',
  access:'<circle cx="12" cy="4.5" r="1.6"/><path d="M5 8h14M12 8v6M9 21l3-7 3 7"/>',
  warn:'<path d="M12 3 2 20h20z"/><path d="M12 10v4M12 17h.01"/>',
  info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/>',
  search:'<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>',
  heart:'<path d="M12 20s-8-5-8-11a4.5 4.5 0 0 1 8-2.8A4.5 4.5 0 0 1 20 9c0 6-8 11-8 11z"/>',
  syringe:'<path d="M18 2l4 4M15 5l4 4M20 4l-9.5 9.5M8 12l4 4M14 8l-8.5 8.5L4 20l3.5-1.5L16 10"/>',
  lungs:'<path d="M12 3v8M12 11c-1 2-3 2-4 1M12 11c1 2 3 2 4 1"/><path d="M8 7C5 7 3 13 3 17c0 2 2 3 4 2l3-2V9"/><path d="M16 7c3 0 5 6 5 10 0 2-2 3-4 2l-3-2V9"/>',
  pulse:'<path d="M3 12h4l2-5 4 10 2-5h6"/>',
  ecg:'<rect x="3" y="4" width="18" height="16"/><path d="M3 13h4l2-4 3 7 2-3h7"/>',
  bone:'<path d="M7 17 17 7"/><circle cx="6" cy="18" r="2"/><circle cx="18" cy="6" r="2"/><circle cx="4.5" cy="15.5" r="1.5"/><circle cx="15.5" cy="4.5" r="1.5"/>',
  brain:'<path d="M9 4a3 3 0 0 0-3 3 3 3 0 0 0-2 5 3 3 0 0 0 2 5 3 3 0 0 0 6 1V5a3 3 0 0 0-3-1zM15 4a3 3 0 0 1 3 3 3 3 0 0 1 2 5 3 3 0 0 1-2 5 3 3 0 0 1-6 1"/>',
  child:'<circle cx="12" cy="6" r="3"/><path d="M8 21v-6l-2-3 3-3h6l3 3-2 3v6"/>',
  calendar:'<rect x="3" y="5" width="18" height="16"/><path d="M3 10h18M8 3v4M16 3v4"/>',
  clock:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  download:'<path d="M12 4v11M7 10l5 5 5-5M4 20h16"/>',
  user:'<circle cx="12" cy="8" r="4"/><path d="M4 21c1-4 4-6 8-6s7 2 8 6"/>',
  palette:'<path d="M12 3a9 9 0 1 0 0 18c1.5 0 2-1 2-2s-1-1.5-1-2.5S14 15 15 15h2a4 4 0 0 0 4-4c0-4.4-4-8-9-8z"/><circle cx="7.5" cy="11" r="1"/><circle cx="10" cy="7" r="1"/><circle cx="15" cy="7.5" r="1"/>',
  edit:'<path d="M4 20h4L19 9l-4-4L4 16z"/>',
  phone:'<rect x="7" y="2" width="10" height="20"/><path d="M11 18h2"/>',
  wifi:'<path d="M2 9a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0"/><circle cx="12" cy="19.5" r="1" fill="currentColor"/>',
  bt:'<path d="M7 7l10 10-5 4V3l5 4L7 17"/>',
  torch:'<path d="M7 3h10v4l-3 4v10h-4V11L7 7z"/>',
  plane:'<path d="M2 14l8-3V5a2 2 0 0 1 4 0v6l8 3v2l-8-2v4l3 2v2l-5-1-5 1v-2l3-2v-4l-8 2z"/>',
  rotate:'<path d="M20 12a8 8 0 1 1-3-6.2"/><path d="M20 4v5h-5"/>',
  gear:'<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M2 12h3M19 12h3M4.9 19.1 7 17M17 7l2.1-2.1"/>',
  check:'<path d="M5 12l5 5 9-10"/>',
  star:'<path d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"/>',
  note:'<path d="M5 3h10l4 4v14H5z"/><path d="M9 12h6M9 16h6"/>',
  timer:'<circle cx="12" cy="13" r="8"/><path d="M12 9v4l2 2M9 2h6"/>',
  drop:'<path d="M12 3s7 7 7 12a7 7 0 0 1-14 0c0-5 7-12 7-12z"/>',
};
const ic = (n, cls='ico') => `<svg class="${cls}" viewBox="0 0 24 24">${I[n]}</svg>`;

const cleverle = (dark=true, size=96) => {
  const s = dark ? '#E8ECF0' : '#11202C';
  return `<svg width="${size}" height="${size*56/48}" viewBox="0 0 48 56"><g fill="none" stroke="${s}" stroke-width="2.6" stroke-linecap="round"><path d="M6 4 C1.5 16 4 31 24 36"/><path d="M42 4 C46.5 16 44 31 24 36"/><path d="M24 36 L24 43 C24 53 37 53 37 46"/></g><g fill="${s}"><circle cx="6" cy="4" r="2.6"/><circle cx="42" cy="4" r="2.6"/></g><g stroke="${s}" stroke-width="1.8" stroke-linejoin="round"><path fill="#B9303F" d="M24 8.5 C23.7 7.7 23.4 7.2 23 7 C17 4.5 10.5 7 10.5 12.5 C6.8 14.5 7.2 21 10.8 23 C11.6 28 17 30.5 23 28.5 Q24 28.9 25 28.5 C31 30.5 36.4 28 37.2 23 C40.8 21 41.2 14.5 37.5 12.5 C37.5 7 31 4.5 25 7 C24.6 7.2 24.3 7.7 24 8.5Z"/><path fill="none" stroke-width="1.6" stroke-linecap="round" d="M24 8.4 C23.7 10.4 24.3 12.2 24 14.4"/></g><g fill="none" stroke="#11202C" stroke-width="1.5" stroke-linecap="round"><path d="M12.6 12.4 c1.6-1.9 3.6-2.3 5.2-1.6 M10.4 19.6 c1.3 .2 2.1-.6 2.2-1.8 M14.2 24.2 c1.6 1.5 3.6 1.6 5.2 .3"/><path d="M35.4 12.4 c-1.6-1.9-3.6-2.3-5.2-1.6 M37.6 19.6 c-1.3 .2-2.1-.6-2.2-1.8 M33.8 24.2 c-1.6 1.5-3.6 1.6-5.2 .3"/></g><circle cx="37" cy="44" r="5" fill="${s}"/><circle cx="37" cy="44" r="2.3" fill="#B9303F"/><ellipse cx="18.2" cy="18.2" rx="3.4" ry="3.8" fill="#fff" stroke="#11202C" stroke-width="1.2"/><circle cx="18.2" cy="18.6" r="1.75" fill="#11202C"/><ellipse cx="29.8" cy="18.2" rx="3.4" ry="3.8" fill="#fff" stroke="#11202C" stroke-width="1.2"/><circle cx="29.8" cy="18.6" r="1.75" fill="#11202C"/></svg>`;
};

const statusbar = (time='02:47', extra='') => `<div class="sb"><span>${time}</span><span class="ic">${extra}<svg viewBox="0 0 24 24"><path d="M2 20h3v-4H2zM7 20h3v-8H7zM12 20h3V8h-3zM17 20h3V4h-3z"/></svg><svg viewBox="0 0 24 24"><path d="M12 19.5 1 8a15.5 15.5 0 0 1 22 0z"/></svg><svg viewBox="0 0 24 24"><rect x="2" y="7" width="18" height="10" fill="none" stroke="currentColor" stroke-width="1.6"/><rect x="4" y="9" width="11" height="6"/><rect x="21" y="10" width="1.5" height="4"/></svg> 64 %</span></div>`;
const dndIcon = `<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2"/><path d="M7 12h10" stroke="currentColor" stroke-width="2.4"/></svg>`;
const gest = `<div class="gest"><i></i></div>`;
const nav = (on) => {
  const items = [['shift','Schicht'],['list','Einsätze'],['chart','Statistik'],['target','Skills'],['more','Mehr']];
  return `<div class="navpad"></div><nav class="nav">${items.map(([i,t],k)=>`<a class="${k===on?'on':''}">${ic(i,'')}<span>${t}</span></a>`).join('')}</nav>`;
};
const appbar = (title, left='', right='') => `<div class="ab"><div class="row" style="gap:6px">${left}<span class="t">${title}</span></div><div class="row" style="gap:0">${right}</div></div>`;
const ib = (n) => `<span class="ib">${ic(n,'')}</span>`;
const page = (body, {light=false, title='SchichtClever'}={}) => `<!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=412"><title>${title}</title><link rel="stylesheet" href="styles.css"></head><body class="${light?'light':''}">${body}</body></html>`;

const S = [];
const add = (slug, title, desc, html, opt) => S.push({slug, title, desc, html: page(html, {...opt, title})});

/* 01 */
add('onboarding-willkommen','Onboarding: Willkommen','Begrüßung mit Cleverle und dem Versprechen der App in einem Satz.', `
${statusbar('21:42')}
<div style="position:absolute;top:36px;left:0;right:0;height:4px;background:var(--red)"></div>
<div style="padding:96px 32px 0;text-align:center">
  <div style="display:flex;justify-content:center">${cleverle(true,132)}</div>
  <div class="word" style="font-size:44px;margin-top:30px">Schicht<b>Clever</b></div>
  <div class="lbl" style="margin-top:6px">ein mediclever-Produkt</div>
  <div class="h" style="font-size:25px;margin-top:44px;line-height:1.3">Deine Schicht.<br>Deine Skills.<br><span class="red">Dein Fokus.</span></div>
  <p class="mut" style="margin-top:18px;font-size:15px">Einsätze mitzählen, Maßnahmen im Blick behalten und in der Nacht weniger abgelenkt sein. Alles bleibt auf deinem Handy.</p>
</div>
<div style="position:absolute;left:24px;right:24px;bottom:56px" class="gap12">
  <div class="btn">Los geht's ${ic('chev','')}</div>
  <div class="mut3" style="text-align:center;font-size:12.5px">Kein Konto nötig · Keine Cloud</div>
</div>
<div style="position:absolute;bottom:40px;left:0;right:0;display:flex;justify-content:center;gap:6px"></div>
${gest}`);

/* 02 */
const roleTile = (icn, t, s, on) => `<div class="card ${on?'acc':''}" style="${on?'border-color:var(--red);background:rgba(185,48,63,.10)':''}"><div class="row"><div class="sq" style="${on?'background:var(--red);color:#fff':''}">${ic(icn,'')}</div><div class="gr" style="flex:1"><div style="font-size:16px;font-weight:500">${t}</div><div class="mut" style="font-size:13px">${s}</div></div>${on?`<div style="width:24px;height:24px;background:var(--red);display:flex;align-items:center;justify-content:center">${ic('check','ico" style="width:16px;height:16px;stroke:#fff;stroke-width:3')}</div>`:'<div style="width:24px;height:24px;border:1.5px solid var(--line)"></div>'}</div></div>`;
add('onboarding-rolle','Onboarding: Rolle wählen','Die Rolle bestimmt den vorgeschlagenen Maßnahmen-Katalog und die Schichtvorlagen.', `
${statusbar('21:43')}
<div style="padding:18px 24px 0">
  <div class="row sp"><span class="lbl">Schritt 2 von 3</span><span class="mut" style="font-size:13px">Überspringen</span></div>
  <div class="row" style="gap:6px;margin-top:10px"><div style="flex:1;height:3px;background:var(--red)"></div><div style="flex:1;height:3px;background:var(--red)"></div><div style="flex:1;height:3px;background:var(--line)"></div></div>
  <div class="h" style="font-size:30px;margin-top:28px">Wo arbeitest du?</div>
  <p class="mut" style="margin-top:8px;font-size:15px">Danach richten sich Maßnahmen und Schichtvorlagen. Du kannst das später jederzeit ändern.</p>
  <div class="gap10" style="margin-top:24px">
    ${roleTile('siren','Rettungsdienst','RS, NotSan, Einsätze mit Alarmzeit',true)}
    ${roleTile('heart','Pflege Klinik','Intensiv, ZNA, Station',false)}
    ${roleTile('pulse','Ärztin · Arzt','Dienste, NEF, Prozeduren-Logbuch',false)}
    ${roleTile('star','Ausbildung & Studium','Praxiseinsätze, Nachweise, Skills',false)}
  </div>
  <div class="card" style="margin-top:14px;padding:12px 16px"><div class="row sp"><span class="mut" style="font-size:13.5px">Qualifikation (optional)</span><span style="font-size:14px">Notfallsanitäter:in ${ic('chev','ico" style="width:16px;height:16px;vertical-align:-3px')}</span></div></div>
</div>
<div style="position:absolute;left:24px;right:24px;bottom:56px" class="btn">Weiter</div>
${gest}`);

/* 03 */
const perm = (icn,t,why,state) => `<div class="card"><div class="row" style="align-items:flex-start"><div class="sq">${ic(icn,'')}</div><div style="flex:1"><div class="row sp"><div style="font-size:15.5px;font-weight:500">${t}</div>${state==='ok'?'<span class="tag g">erteilt</span>':'<span class="tag r">erlauben</span>'}</div><div class="mut" style="font-size:13px;margin-top:4px">${why}</div></div></div></div>`;
add('onboarding-berechtigungen','Onboarding: Berechtigungen','Jede Android-Berechtigung wird mit ihrem konkreten Zweck erklärt, nichts verlässt das Gerät.', `
${statusbar('21:44')}
<div style="padding:18px 24px 0">
  <div class="row sp"><span class="lbl">Schritt 3 von 3</span><span class="mut" style="font-size:13px">Später</span></div>
  <div class="row" style="gap:6px;margin-top:10px"><div style="flex:1;height:3px;background:var(--red)"></div><div style="flex:1;height:3px;background:var(--red)"></div><div style="flex:1;height:3px;background:var(--red)"></div></div>
  <div class="h" style="font-size:30px;margin-top:28px">Damit Fokus wirklich wirkt</div>
  <p class="mut" style="margin-top:8px;font-size:15px">Drei Freigaben brauchen wir. Wofür genau, steht dabei.</p>
  <div class="gap10" style="margin-top:22px">
    ${perm('dnd','Bitte nicht stören','Schaltet „Nicht stören“ automatisch zu Schichtbeginn ein und zum Ende wieder aus.','ok')}
    ${perm('access','Bedienungshilfe','Erkennt, wenn du eine pausierte App öffnest, und legt den Blaufilter über den Bildschirm.','todo')}
    ${perm('bell','Benachrichtigungen','Zeigt die laufende Schicht an und erinnert dich an Skills, die du üben willst.','todo')}
  </div>
  <div class="info" style="margin-top:16px">${ic('shield','')}<span>SchichtClever hat keine Internet-Berechtigung. Deine Daten bleiben auf diesem Gerät.</span></div>
</div>
<div style="position:absolute;left:24px;right:24px;bottom:56px" class="btn">Bedienungshilfe öffnen</div>
${gest}`);

/* 04 */
const shiftCard = (icn,t,time,on) => `<div class="card ${on?'acct':''}" style="padding:14px;${on?'border-color:var(--red)':''}"><div style="color:${on?'var(--redt)':'var(--tx2)'}">${ic(icn)}</div><div class="h" style="font-size:19px;margin-top:10px">${t}</div><div class="mut" style="font-size:13px">${time}</div></div>`;
add('schicht-start','Schicht: Start','Ohne aktive Schicht: Vorlage wählen und mit einem Tipp starten.', `
${statusbar('21:52')}
${appbar('<span class="word">Schicht<b>Clever</b></span>','',ib('bell'))}
<div class="content">
  <div class="lbl" style="margin-top:6px">Freitag, 27. November</div>
  <div class="h" style="font-size:30px;margin-top:6px">Guten Abend.<br><span class="mut">Welche Schicht steht an?</span></div>
  <div class="grid3" style="margin-top:22px">
    ${shiftCard('sun','Früh','06 – 14 Uhr',false)}
    ${shiftCard('clock','Spät','14 – 22 Uhr',false)}
    ${shiftCard('moon','Nacht','22 – 06 Uhr',true)}
    ${shiftCard('timer','12 h','07 – 19 Uhr',false)}
    ${shiftCard('shift','24 h','07 – 07 Uhr',false)}
    <div class="card" style="padding:14px;border-style:dashed;display:flex;flex-direction:column;justify-content:center;align-items:center;color:var(--tx2)">${ic('plus')}<div style="font-size:13px;margin-top:6px">Eigene</div></div>
  </div>
  <div class="btn" style="margin-top:20px;height:60px;font-size:18px">${ic('moon','')} Nachtschicht starten</div>
  <div class="row" style="margin-top:12px;gap:16px;justify-content:center;font-size:13px" class="mut">
    <span class="row mut" style="gap:6px">${ic('focus','ico" style="width:16px;height:16px')}Fokus startet mit</span>
    <span class="row mut" style="gap:6px">${ic('filter','ico" style="width:16px;height:16px')}Blaufilter ab 22 Uhr</span>
  </div>
  <div class="card acc" style="margin-top:22px"><div class="row sp"><div><div class="lbl">Letzte Schicht</div><div style="font-size:15px;margin-top:4px">Nacht · Mi 25.11. · 7 Einsätze</div></div><span class="mut">${ic('chev')}</span></div></div>
</div>
${nav(0)}${gest}`);

/* 05 */
add('schichtwahl-sheet','Schicht anpassen (Bottom-Sheet)','Vor dem Start lassen sich Typ, Beginn, Fahrzeug oder Station und die Automatik einstellen.', `
${statusbar('21:53')}
${appbar('<span class="word">Schicht<b>Clever</b></span>','',ib('bell'))}
<div class="content"><div class="h" style="font-size:30px;margin-top:22px">Guten Abend.</div></div>
<div class="scrim"></div>
<div class="sheet">
  <div class="handle"></div>
  <div class="row sp"><div class="h" style="font-size:22px">Nachtschicht starten</div><span class="mut">${ic('close')}</span></div>
  <div class="lbl" style="margin-top:18px">Schichttyp</div>
  <div class="seg" style="grid-template-columns:repeat(5,1fr);margin-top:8px"><div>Früh</div><div>Spät</div><div class="on">Nacht</div><div>12 h</div><div>24 h</div></div>
  <div class="grid2" style="margin-top:14px">
    <div class="field"><div class="k">Beginn</div><div class="v">Heute, 22:00</div></div>
    <div class="field"><div class="k">Ende geplant</div><div class="v">Sa, 06:00</div></div>
  </div>
  <div class="field" style="margin-top:8px"><div class="k">Fahrzeug / Station (optional)</div><div class="v">RTW 1/83-1</div></div>
  <div class="chips" style="margin-top:10px"><span class="chip soft">RTW 1/83-1</span><span class="chip soft">NEF 1/82-1</span><span class="chip soft">Wache Nord</span></div>
  <div class="lbl" style="margin-top:20px">Automatisch mitstarten</div>
  <div style="margin-top:4px">
    <div class="li"><div class="sq">${ic('focus','')}</div><div class="gr"><div class="ti">Fokus-Modus</div><div class="su">Nicht stören + 4 Apps pausiert</div></div><div class="sw on"></div></div>
    <div class="li"><div class="sq">${ic('filter','')}</div><div class="gr"><div class="ti">Blaufilter</div><div class="su">ab 22:00 · 40 % · warm</div></div><div class="sw on"></div></div>
    <div class="li"><div class="sq">${ic('bell','')}</div><div class="gr"><div class="ti">Pausen-Erinnerung</div><div class="su">nach 6 h ohne Pause</div></div><div class="sw"></div></div>
  </div>
  <div class="btn" style="margin-top:16px">Schicht starten</div>
</div>
${gest}`);

/* 06 */
const tl = (time,cat,meas,dot='var(--red)',last=false) => `<div class="row" style="align-items:stretch;gap:14px"><div style="width:44px;text-align:right" class="num"><span style="font-size:17px">${time}</span></div><div style="display:flex;flex-direction:column;align-items:center"><div class="dot" style="background:${dot};margin-top:5px"></div>${last?'':'<div style="flex:1;width:1px;background:var(--line);margin-top:4px"></div>'}</div><div style="flex:1;padding-bottom:14px"><div style="font-size:15px;font-weight:500">${cat}</div><div class="mut" style="font-size:12.5px">${meas}</div></div></div>`;
const kpi = (n,l,sub='') => `<div class="card" style="padding:12px 14px"><div class="num" style="font-size:30px">${n}</div><div class="mut" style="font-size:12.5px;margin-top:4px">${l}</div>${sub}</div>`;
add('schicht-aktiv-nacht','Schicht aktiv: Nacht','Laufende Nachtschicht mit Fortschritt, Kennzahlen, Status der Automatik und Einsatz-Zeitleiste.', `
${statusbar('02:47', dndIcon)}
${appbar('<span class="word">Schicht<b>Clever</b></span>','',ib('bell'))}
<div class="content">
  <div class="card acct" style="padding:18px">
    <div class="row sp"><span class="lbl" style="color:var(--redt)">● Nachtschicht läuft</span><span class="mut" style="font-size:13px">RTW 1/83-1</span></div>
    <div class="row sp" style="align-items:flex-end;margin-top:10px"><div><span class="num" style="font-size:54px">04:47</span><span class="mut" style="font-size:16px;margin-left:6px">h</span></div><div style="text-align:right"><div class="mut" style="font-size:12.5px">noch</div><div class="num" style="font-size:22px">3:13 h</div></div></div>
    <div class="bar" style="margin-top:14px"><i style="width:60%"></i></div>
    <div class="row sp mut" style="font-size:12px;margin-top:6px"><span>22:00</span><span>06:00</span></div>
  </div>
  <div class="grid2" style="margin-top:8px">
    ${kpi('4','Einsätze')}
    ${kpi('11','Maßnahmen')}
    <div class="card" style="padding:12px 14px"><div class="row sp"><span class="red">${ic('focus')}</span><span class="tag r">aktiv</span></div><div style="font-size:14px;font-weight:500;margin-top:8px">Fokus</div><div class="mut" style="font-size:12px">4 Apps pausiert</div></div>
    <div class="card" style="padding:12px 14px"><div class="row sp"><span style="color:#E0A040">${ic('filter')}</span><span class="tag y">40 %</span></div><div style="font-size:14px;font-weight:500;margin-top:8px">Blaufilter</div><div class="mut" style="font-size:12px">warm · bis 06:00</div></div>
  </div>
  <div class="row sp" style="margin-top:18px"><span class="lbl">Heute Nacht</span><span class="mut" style="font-size:13px">Alle</span></div>
  <div style="margin-top:10px">
    ${tl('02:31','Internistisch · ACS-Verdacht','12-Kanal-EKG · i.v.-Zugang · Monitoring')}
    ${tl('00:58','Neuro · Krampfanfall','BZ-Messung · i.v.-Zugang')}
    ${tl('23:40','Trauma · Sturz','Immobilisation · Wundversorgung',undefined,true)}
  </div>
</div>
<div class="fab" style="bottom:110px">${ic('plus','')}Einsatz</div>
${nav(0)}${gest}`);

/* 07 */
add('einsatz-erfassen','Einsatz erfassen','Schnelle Erfassung mit vorbelegter Alarmzeit, Kategorie, Dringlichkeit und Ausgang, ohne Patientendaten.', `
${statusbar('02:48', dndIcon)}
${appbar('Neuer Einsatz', ib('close'), '<span class="red h" style="font-size:16px;letter-spacing:.06em;padding-right:8px">SPEICHERN</span>')}
<div class="content">
  <div class="note">${ic('warn','')}<span><b>Keine Patientendaten eintragen.</b> Keine Namen, Geburtsdaten oder Adressen, nur was du gemacht hast.</span></div>
  <div class="lbl" style="margin-top:18px">Alarmierung</div>
  <div class="row" style="margin-top:8px;gap:8px">
    <div class="field" style="flex:1"><div class="k">Uhrzeit</div><div class="v num" style="font-size:24px;font-weight:500">02:31</div></div>
    <div class="chip on" style="height:58px;padding:0 18px">${ic('clock','')}Jetzt</div>
  </div>
  <div class="lbl" style="margin-top:18px">Kategorie</div>
  <div class="chips" style="margin-top:8px">
    <span class="chip on">${ic('heart','')}Internistisch</span><span class="chip">${ic('bone','')}Trauma</span><span class="chip">${ic('brain','')}Neuro</span><span class="chip">${ic('child','')}Pädiatrie</span><span class="chip">Psych</span><span class="chip">${ic('pulse','')}Reanimation</span><span class="chip">Sonstiges</span>
  </div>
  <div class="field" style="margin-top:10px"><div class="k">Stichwort / Verdacht (optional)</div><div class="v">ACS-Verdacht</div></div>
  <div class="lbl" style="margin-top:18px">Dringlichkeit</div>
  <div class="seg" style="grid-template-columns:repeat(3,1fr);margin-top:8px"><div>Ohne SoSi</div><div class="on">Mit SoSi</div><div>NA-Indikation</div></div>
  <div class="lbl" style="margin-top:18px">Ausgang</div>
  <div class="seg" style="grid-template-columns:repeat(4,1fr);margin-top:8px"><div class="on">Transport</div><div>Ambulant</div><div>Exitus</div><div>Fehleinsatz</div></div>
  <div class="card row sp" style="margin-top:18px;padding:14px 16px"><div class="row">${ic('syringe','ico" style="color:var(--redt)')}<div><div style="font-weight:500">Maßnahmen</div><div class="mut" style="font-size:12.5px">12-Kanal-EKG, i.v.-Zugang, Monitoring</div></div></div><span class="tag r">3</span></div>
</div>
${gest}`);

/* 08 */
const grp = (t, chips) => `<div class="lbl" style="margin-top:16px">${t}</div><div class="chips" style="margin-top:8px">${chips.map(c=>{const on=c.startsWith('*');const n=on?c.slice(1):c;return `<span class="chip ${on?'on':''}">${on?ic('check',''):''}${n}</span>`}).join('')}</div>`;
add('massnahmen-waehlen','Maßnahmen wählen','Gruppierter Maßnahmen-Katalog mit Suche, Mehrfachauswahl und Rolle selbst oder assistiert.', `
${statusbar('02:49', dndIcon)}
${appbar('Maßnahmen', ib('back'), '<span class="tag r" style="margin-right:8px">5 gewählt</span>')}
<div class="content">
  <div class="field row" style="gap:10px;padding:12px 14px">${ic('search','ico" style="color:var(--tx2)')}<span class="mut" style="font-size:15px">Maßnahme suchen …</span></div>
  <div class="seg" style="grid-template-columns:1fr 1fr;margin-top:12px"><div class="on">Selbst durchgeführt</div><div>Assistiert</div></div>
  ${grp('Zugang',['*i.v.-Zugang','i.o.-Zugang','Blutentnahme'])}
  ${grp('Atemweg & Beatmung',['O₂-Gabe','Larynxtubus','Intubation','NIV','Beutel-Masken-Beatmung'])}
  ${grp('Kreislauf',['*Monitoring','Defibrillation','Kardioversion','Herzdruckmassage','*Medikamentengabe'])}
  ${grp('Diagnostik',['*12-Kanal-EKG','*BZ-Messung','Blutgasanalyse','Sono (POCUS)'])}
  ${grp('Trauma',['Beckenschlinge','Tourniquet','Thoraxentlastung','Immobilisation'])}
</div>
<div style="position:absolute;left:0;right:0;bottom:22px;padding:12px 20px;background:var(--card);border-top:1px solid var(--line)"><div class="btn">5 Maßnahmen übernehmen</div></div>
${gest}`);

/* 09 */
add('schicht-aktiv-tag-hell','Schicht aktiv: Tagschicht (hell)','Frühdienst auf Station im hellen Design: dieselbe Struktur, für Tageslicht angepasst.', `
${statusbar('10:12')}
${appbar('<span class="word">Schicht<b>Clever</b></span>','',ib('bell'))}
<div class="content">
  <div class="card acct" style="padding:18px">
    <div class="row sp"><span class="lbl" style="color:var(--red)">● Frühdienst läuft</span><span class="mut" style="font-size:13px">Intensiv 3B</span></div>
    <div class="row sp" style="align-items:flex-end;margin-top:10px"><div><span class="num" style="font-size:54px">04:12</span><span class="mut" style="font-size:16px;margin-left:6px">h</span></div><div style="text-align:right"><div class="mut" style="font-size:12.5px">noch</div><div class="num" style="font-size:22px">3:48 h</div></div></div>
    <div class="bar" style="margin-top:14px"><i style="width:52%"></i></div>
    <div class="row sp mut" style="font-size:12px;margin-top:6px"><span>06:00</span><span>14:00</span></div>
  </div>
  <div class="grid2" style="margin-top:8px">
    ${kpi('2','Notfälle / Aufnahmen')}
    ${kpi('9','Maßnahmen')}
    <div class="card" style="padding:12px 14px"><div class="row sp"><span class="red">${ic('focus')}</span><span class="tag r">aktiv</span></div><div style="font-size:14px;font-weight:500;margin-top:8px">Fokus</div><div class="mut" style="font-size:12px">2 Apps pausiert</div></div>
    <div class="card" style="padding:12px 14px"><div class="row sp"><span class="mut">${ic('filter')}</span><span class="tag">aus</span></div><div style="font-size:14px;font-weight:500;margin-top:8px">Blaufilter</div><div class="mut" style="font-size:12px">startet ab 22 Uhr</div></div>
  </div>
  <div class="row sp" style="margin-top:18px"><span class="lbl">Heute</span><span class="mut" style="font-size:13px">Alle</span></div>
  <div style="margin-top:10px">
    ${tl('09:20','Aufnahme · Sepsis-Verdacht','Arterie assistiert · BGA · ZVK-Pflege')}
    ${tl('07:05','Notfall · Atemnot','NIV · BGA · Lagerung',undefined,true)}
  </div>
</div>
<div class="fab" style="bottom:110px;box-shadow:0 10px 24px rgba(185,48,63,.25)">${ic('plus','')}Eintrag</div>
${nav(0)}${gest}`, {light:true});

/* 10 */
add('schicht-beenden','Schicht beenden: Zusammenfassung','Bilanz der Schicht mit Kennzahlen, häufigsten Maßnahmen, Stimmung und Notiz; Fokus und Filter enden.', `
${statusbar('06:04')}
${appbar('Schicht beendet', ib('close'))}
<div class="content">
  <div class="lbl">Nacht · 27./28.11. · RTW 1/83-1</div>
  <div class="grid3" style="margin-top:12px">
    <div class="card acct" style="padding:12px"><div class="num" style="font-size:30px">8:04</div><div class="mut" style="font-size:12px;margin-top:4px">Stunden</div></div>
    <div class="card" style="padding:12px"><div class="num" style="font-size:30px">6</div><div class="mut" style="font-size:12px;margin-top:4px">Einsätze</div></div>
    <div class="card" style="padding:12px"><div class="num" style="font-size:30px">17</div><div class="mut" style="font-size:12px;margin-top:4px">Maßnahmen</div></div>
  </div>
  <div class="card" style="margin-top:8px"><div class="row sp"><span class="lbl">Meiste Einsätze</span><span class="num" style="font-size:18px">02 – 04 Uhr</span></div>
    <div class="row" style="align-items:flex-end;gap:5px;height:44px;margin-top:12px">${[1,2,0,1,3,2,0,0].map((v,i)=>`<div style="flex:1;height:${8+v*12}px;background:${i===4||i===5?'var(--red)':'var(--line)'}"></div>`).join('')}</div>
    <div class="row sp mut" style="font-size:11px;margin-top:4px"><span>22</span><span>00</span><span>02</span><span>04</span><span>06</span></div></div>
  <div class="card" style="margin-top:8px"><div class="lbl">Top-Maßnahmen</div>
    <div class="gap8" style="margin-top:10px;font-size:14px">
      <div class="row sp"><span>i.v.-Zugang</span><span class="num" style="font-size:17px">5×</span></div>
      <div class="row sp"><span>12-Kanal-EKG</span><span class="num" style="font-size:17px">4×</span></div>
      <div class="row sp"><span>BZ-Messung</span><span class="num" style="font-size:17px">3×</span></div>
    </div></div>
  <div class="lbl" style="margin-top:16px">Wie war die Schicht?</div>
  <div class="grid3" style="grid-template-columns:repeat(5,1fr);margin-top:8px">${['Hart','Zäh','Okay','Gut','Top'].map((t,i)=>`<div class="card" style="padding:9px 0;text-align:center;${i===3?'background:var(--red);border-color:var(--red);color:#fff':''}"><div class="num" style="font-size:20px">${i+1}</div><div style="font-size:11px;opacity:.8">${t}</div></div>`).join('')}</div>
  <div class="field" style="margin-top:8px;height:52px"><div class="k">Notiz für dich (optional)</div><div class="v mut" style="font-size:14px">z. B. was du nachlesen willst …</div></div>
  <div class="info" style="margin-top:10px">${ic('check','')}<span>Fokus-Modus und Blaufilter wurden beendet. Gute Erholung!</span></div>
  <div class="btn" style="margin-top:12px">Speichern & schlafen</div>
</div>
${gest}`);

/* 11 */
const hours=[2,1,3,5,4,1,1,2,3,4,5,4,6,5,4,5,6,5,4,3,3,4,3,2]; // index = Stunde
const hrs=[3,2,7,9,8,3,1,1,2,3,4,4,5,5,4,5,6,5,4,4,3,3,4,3];
add('statistik-uebersicht','Statistik: Übersicht','Einsätze, Stunden und Maßnahmen im Zeitraum plus Verteilung nach Uhrzeit mit markiertem Peak.', `
${statusbar('14:20')}
${appbar('Statistik','',ib('download'))}
<div class="content">
  <div class="seg" style="grid-template-columns:repeat(3,1fr)"><div>Woche</div><div class="on">Monat</div><div>Jahr</div></div>
  <div class="row sp mut" style="margin-top:12px;font-size:13.5px"><span>${ic('back','ico" style="width:16px;height:16px;vertical-align:-3px')}</span><span style="color:var(--tx);font-weight:500">November 2026</span><span style="opacity:.4">${ic('chev','ico" style="width:16px;height:16px;vertical-align:-3px')}</span></div>
  <div class="card acct" style="margin-top:12px;padding:18px"><div class="row sp" style="align-items:flex-end"><div><div class="lbl">Einsätze</div><div class="num" style="font-size:60px;margin-top:6px">87</div></div><div style="text-align:right"><span class="tag g">+12 % zum Oktober</span><div class="mut" style="font-size:12.5px;margin-top:8px">in 14 Schichten</div></div></div></div>
  <div class="grid3" style="margin-top:8px">
    <div class="card" style="padding:12px"><div class="num" style="font-size:26px">142</div><div class="mut" style="font-size:12px;margin-top:4px">Stunden</div></div>
    <div class="card" style="padding:12px"><div class="num" style="font-size:26px">6,2</div><div class="mut" style="font-size:12px;margin-top:4px">Ø pro Schicht</div></div>
    <div class="card" style="padding:12px"><div class="num" style="font-size:26px">231</div><div class="mut" style="font-size:12px;margin-top:4px">Maßnahmen</div></div>
  </div>
  <div class="card" style="margin-top:8px">
    <div class="row sp"><span class="lbl">Einsätze nach Uhrzeit</span><span class="tag r">Peak 2 – 4 Uhr</span></div>
    <div style="position:relative;margin-top:16px">
      <div style="position:absolute;left:${2/24*100}%;width:${2/24*100}%;top:-6px;bottom:0;background:rgba(185,48,63,.12)"></div>
      <div class="row" style="align-items:flex-end;gap:3px;height:150px;position:relative">${hrs.map((v,i)=>`<div style="flex:1;height:${v/9*140+4}px;background:${i===2||i===3?'var(--red)':'var(--line)'}"></div>`).join('')}</div>
    </div>
    <div class="row sp mut" style="font-size:11px;margin-top:6px"><span>0</span><span>6</span><span>12</span><span>18</span><span>23 h</span></div>
    <div class="mut" style="font-size:12.5px;margin-top:10px">Zwischen 2 und 4 Uhr kommen 18 % deiner Einsätze, obwohl das nur 8 % der Zeit sind.</div>
  </div>
</div>
${nav(2)}${gest}`);

/* 12 */
const hb=(t,p,n,c='var(--red)')=>`<div><div class="row sp" style="font-size:13px"><span>${t}</span><span class="mut">${n} · ${p} %</span></div><div class="bar" style="margin-top:3px;height:6px"><i style="width:${p*2}%;background:${c}"></i></div></div>`;
add('statistik-arten-schichten','Statistik: Einsatzarten & Schichten','Verteilung der Kategorien, Durchschnitt je Schichttyp und Serie der Nachtschichten.', `
${statusbar('14:21')}
${appbar('Statistik','',ib('download'))}
<div class="content">
  <div class="seg" style="grid-template-columns:repeat(3,1fr)"><div>Übersicht</div><div class="on">Einsatzarten</div><div>Schichten</div></div>
  <div class="card" style="margin-top:12px"><div class="lbl">Kategorien · November</div>
    <div class="gap8" style="margin-top:10px">
      ${hb('Internistisch',41,36)}${hb('Trauma',20,17,'var(--rusty)')}${hb('Neuro',14,12,'#9C4A57')}${hb('Pädiatrie',8,7,'#7A5560')}${hb('Psych',7,6,'#5E6470')}${hb('Reanimation',3,3,'var(--redt)')}${hb('Sonstiges',7,6,'#4A5866')}
    </div></div>
  <div class="card" style="margin-top:8px"><div class="lbl">Ø Einsätze pro Schicht</div>
    <div class="row" style="align-items:flex-end;gap:14px;height:84px;margin-top:6px">
      ${[['Früh',5.1],['Spät',6.4],['Nacht',7.3],['12 h',8.0],['24 h',13.2]].map(([t,v])=>`<div style="flex:1;text-align:center"><div class="num" style="font-size:15px;margin-bottom:5px">${String(v).replace('.',',')}</div><div style="height:${v/13.2*56}px;background:${t==='Nacht'?'var(--red)':'var(--line)'}"></div><div class="mut" style="font-size:11.5px;margin-top:5px">${t}</div></div>`).join('')}
    </div></div>
  <div class="card acc" style="margin-top:8px"><div class="row sp"><div><div class="lbl">Nachtschicht-Serie</div><div class="num" style="font-size:34px;margin-top:6px">3 <span class="mut" style="font-size:15px;font-family:Roboto;font-weight:400">Nächte am Stück</span></div></div><span class="red">${ic('moon','ico" style="width:34px;height:34px')}</span></div>
    <div class="row" style="gap:4px;margin-top:12px">${['Mo','Di','Mi','Do','Fr','Sa','So'].map((d,i)=>`<div style="flex:1;text-align:center"><div style="height:24px;background:${i>=2&&i<=4?'var(--red)':'var(--card2)'}"></div><div class="mut" style="font-size:11px;margin-top:3px">${d}</div></div>`).join('')}</div>
    <div class="mut" style="font-size:12.5px;margin-top:10px">Nach der Serie: zwei freie Tage zum Umstellen eingeplant?</div></div>
</div>
${nav(2)}${gest}`);

/* 13 */
const fresh = {g:['var(--ok)','frisch'],y:['var(--warn)','bald üben'],r:['var(--redt)','lange her']};
const skill=(icn,t,n,last,f)=>`<div class="li"><div class="sq">${ic(icn,'')}</div><div class="gr"><div class="ti">${t}</div><div class="su">${n}× gesamt · zuletzt ${last}</div></div><div class="row" style="gap:6px"><span class="dot" style="background:${fresh[f][0]}"></span><span style="font-size:12px;color:${fresh[f][0]}">${fresh[f][1]}</span></div></div>`;
add('skills-uebersicht','Skills: Übersicht','Alle Maßnahmen mit Häufigkeit, letztem Mal und einer Ampel, wie frisch der Skill ist.', `
${statusbar('14:25')}
${appbar('Skills','',ib('search'))}
<div class="content">
  <div class="grid3">
    <div class="card" style="padding:10px 12px"><div class="row" style="gap:6px"><span class="dot" style="background:var(--ok)"></span><span class="num" style="font-size:24px">14</span></div><div class="mut" style="font-size:12px">frisch</div></div>
    <div class="card" style="padding:10px 12px"><div class="row" style="gap:6px"><span class="dot" style="background:var(--warn)"></span><span class="num" style="font-size:24px">5</span></div><div class="mut" style="font-size:12px">bald üben</div></div>
    <div class="card" style="padding:10px 12px;border-color:rgba(185,48,63,.5)"><div class="row" style="gap:6px"><span class="dot" style="background:var(--redt)"></span><span class="num" style="font-size:24px">3</span></div><div class="mut" style="font-size:12px">lange her</div></div>
  </div>
  <div class="chips" style="margin-top:12px"><span class="chip on">Alle</span><span class="chip">Zugang</span><span class="chip">Atemweg</span><span class="chip">Kreislauf</span><span class="chip">Trauma</span></div>
  <div style="margin-top:6px">
    ${skill('syringe','i.v.-Zugang',148,'heute','g')}
    ${skill('ecg','12-Kanal-EKG',121,'heute','g')}
    ${skill('lungs','NIV',9,'vor 23 Tagen','g')}
    ${skill('lungs','Larynxtubus',6,'vor 41 Tagen','y')}
    ${skill('pulse','Defibrillation',4,'vor 52 Tagen','y')}
    ${skill('syringe','i.o.-Zugang',3,'vor 94 Tagen','r')}
    ${skill('lungs','Intubation (assistiert)',2,'vor 130 Tagen','r')}
    ${skill('bone','Thoraxentlastung',1,'vor 5 Monaten','r')}
  </div>
</div>
${nav(3)}${gest}`);

/* 14 */
const months=['D','J','F','M','A','M','J','J','A','S','O','N'], mv=[0,1,0,0,0,1,0,0,1,0,0,0];
add('skill-detail-io','Skill-Detail: i.o.-Zugang','Verlauf eines einzelnen Skills mit Ziel-Intervall, Übungsliste und manuellen Einträgen für Training.', `
${statusbar('14:26')}
${appbar('i.o.-Zugang', ib('back'), ib('edit'))}
<div class="content">
  <div class="row" style="gap:8px"><span class="tag">Zugang</span><span class="tag r">● lange her</span></div>
  <div class="grid2" style="margin-top:14px">
    <div class="card acct"><div class="num" style="font-size:48px">3×</div><div class="mut" style="font-size:12.5px;margin-top:6px">gesamt (2 Einsatz, 1 Übung)</div></div>
    <div class="card" style="border-color:rgba(185,48,63,.5)"><div class="num red" style="font-size:48px">94</div><div class="mut" style="font-size:12.5px;margin-top:6px">Tage seit dem letzten Mal</div></div>
  </div>
  <div class="card" style="margin-top:8px"><div class="row sp"><span class="lbl">Letzte 12 Monate</span><span class="mut" style="font-size:12.5px">Ziel: alle 60 Tage</span></div>
    <div class="row" style="align-items:flex-end;gap:6px;height:70px;margin-top:14px">${mv.map((v,i)=>`<div style="flex:1;height:${v?54:6}px;background:${v?'var(--red)':'var(--line)'}"></div>`).join('')}</div>
    <div class="row mut" style="font-size:11px;margin-top:5px">${months.map(m=>`<span style="flex:1;text-align:center">${m}</span>`).join('')}</div>
    <div class="bar" style="margin-top:14px;height:8px"><i style="width:100%;background:linear-gradient(90deg,var(--ok) 0 64%,var(--warn) 64% 80%,var(--red) 80%)"></i></div>
    <div class="row sp mut" style="font-size:11.5px;margin-top:4px"><span>0</span><span>60 Tage</span><span style="color:var(--redt)">94 ▲</span></div></div>
  <div class="lbl" style="margin-top:16px">Verlauf</div>
  <div>
    <div class="li"><div class="sq">${ic('star','')}</div><div class="gr"><div class="ti">Übung · Skillstraining Wache</div><div class="su">26.08.2026 · Humerus proximal</div></div></div>
    <div class="li"><div class="sq">${ic('siren','')}</div><div class="gr"><div class="ti">Einsatz · Reanimation</div><div class="su">14.05.2026 · Tibia · selbst</div></div></div>
    <div class="li"><div class="sq">${ic('siren','')}</div><div class="gr"><div class="ti">Einsatz · Pädiatrie</div><div class="su">03.01.2026 · assistiert</div></div></div>
  </div>
  <div class="btn" style="margin-top:14px">${ic('target','')} Auf Übungsliste</div>
  <div class="btn ghost" style="margin-top:8px;height:46px">${ic('plus','')} Manuell eintragen (Übung/Simulation)</div>
</div>
${gest}`);

/* 15 */
const prac=(t,goal,sub,f,done=false)=>`<div class="card ${f==='r'?'acc':''}" style="padding:13px 16px"><div class="row"><div style="width:22px;height:22px;border:1.5px solid ${done?'var(--red)':'var(--line)'};background:${done?'var(--red)':'transparent'};display:flex;align-items:center;justify-content:center;flex:none">${done?ic('check','ico" style="width:14px;height:14px;stroke:#fff;stroke-width:3'):''}</div><div style="flex:1"><div style="font-size:15px;font-weight:500;${done?'text-decoration:line-through;opacity:.55':''}">${t}</div><div class="mut" style="font-size:12.5px">${sub}</div></div><div style="text-align:right"><div class="lbl" style="font-size:10px">Ziel</div><div style="font-size:13px;${f==='r'?'color:var(--redt)':''}">${goal}</div></div></div></div>`;
add('uebungsliste','Übungsliste: Will ich wieder üben','Persönliche Liste mit Zieldaten, Erinnerung und einem dezenten Hinweis von Cleverle.', `
${statusbar('14:30')}
${appbar('Will ich üben', ib('back'), ib('plus'))}
<div class="content">
  <div class="card row" style="align-items:flex-start;gap:14px;background:var(--card2);border-color:transparent">
    <div style="flex:none">${cleverle(true,46)}</div>
    <div><div style="font-size:14.5px"><b>Thoraxentlastung</b> ist 5 Monate her. Skillstraining nächste Woche?</div>
    <div class="row" style="gap:16px;margin-top:10px"><span class="red h" style="font-size:14px;letter-spacing:.06em">ERINNERN</span><span class="mut h" style="font-size:14px;letter-spacing:.06em">SPÄTER</span></div></div>
  </div>
  <div class="row sp" style="margin-top:20px"><span class="lbl">Offen · 4</span><span class="mut" style="font-size:13px">nach Zieldatum</span></div>
  <div class="gap8" style="margin-top:10px">
    ${prac('Thoraxentlastung','04.12.','Skillstraining · Puppe','r')}
    ${prac('i.o.-Zugang','10.12.','Humerus-Zugang wiederholen','r')}
    ${prac('Larynxtubus','20.12.','Größenwahl & Cuffdruck','y')}
    ${prac('Kinder-Reanimation','Jan.','PALS-Algorithmus durchgehen','y')}
  </div>
  <div class="lbl" style="margin-top:20px">Erledigt</div>
  <div class="gap8" style="margin-top:10px">${prac('Beckenschlinge anlegen','✓','geübt am 12.11.','g',true)}</div>
  <div class="card" style="margin-top:16px"><div class="row sp"><div class="row">${ic('bell','ico" style="color:var(--redt)')}<div><div style="font-weight:500">Wöchentliche Erinnerung</div><div class="mut" style="font-size:12.5px">Sonntag, 18:00 · nicht in der Schicht</div></div></div><div class="sw on"></div></div></div>
</div>
${nav(3)}${gest}`);

/* 16 */
const appRow=(name,col,letter,on,sub)=>`<div class="li" style="padding:10px 0"><div style="width:38px;height:38px;background:${col};color:#fff;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:16px;border-radius:9px;flex:none">${letter}</div><div class="gr"><div class="ti" style="font-size:14.5px">${name}</div><div class="su">${sub}</div></div><div class="sw ${on?'on':''}"></div></div>`;
add('fokus-einstellungen','Fokus-Modus: Einstellungen','Automatischer Fokus mit „Nicht stören“, Ausnahmen für Anrufe und einer Liste pausierter Apps.', `
${statusbar('14:34')}
${appbar('Fokus-Modus', ib('back'))}
<div class="content">
  <div class="card acct row sp" style="padding:18px"><div><div class="h" style="font-size:20px">Fokus während der Schicht</div><div class="mut" style="font-size:13px;margin-top:2px">Startet und endet mit der Schicht</div></div><div class="sw on" style="transform:scale(1.15)"></div></div>
  <div class="lbl" style="margin-top:18px">Nicht stören</div>
  <div class="card" style="padding:0 16px;margin-top:8px">
    <div class="li"><div class="gr"><div class="ti">Automatisch bei Schichtbeginn</div></div><div class="sw on"></div></div>
    <div class="li"><div class="gr"><div class="ti">Anrufe von Favoriten</div><div class="su">Familie, Wache, Leitstelle</div></div><div class="sw on"></div></div>
    <div class="li"><div class="gr"><div class="ti">Wiederholte Anrufe</div><div class="su">Zweiter Anruf innerhalb von 15 Min. kommt durch</div></div><div class="sw on"></div></div>
  </div>
  <div class="row sp" style="margin-top:18px"><span class="lbl">Apps pausieren</span><span class="tag r">4 pausiert</span></div>
  <div class="card" style="padding:0 16px;margin-top:8px">
    ${appRow('Instagram','#C13584','I',true,'pausiert')}
    ${appRow('TikTok','#111','T',true,'pausiert')}
    ${appRow('YouTube','#E62117','Y',true,'pausiert')}
    ${appRow('Netflix','#B1060F','N',true,'pausiert')}
    ${appRow('WhatsApp','#25A35A','W',false,'erlaubt')}
  </div>
  <div class="mut" style="font-size:12.5px;margin-top:10px">Apps für Alarmierung, Navigation und Telefon pausiert SchichtClever nie.</div>
</div>
${gest}`);

/* 17 */
add('sperrbildschirm','Sperrbildschirm: App pausiert','Overlay, wenn eine pausierte App geöffnet wird, mit Restzeit und bewusster Kurzfreigabe.', `
<div style="position:absolute;inset:0;background:#070F16"></div>
<div style="position:relative">${statusbar('03:12', dndIcon)}</div>
<div style="position:relative;padding:90px 32px 0;text-align:center">
  <div style="display:inline-flex;opacity:.92">${cleverle(true,76)}</div>
  <div class="lbl" style="margin-top:34px;color:var(--redt)">Fokus · Nachtschicht</div>
  <div class="h" style="font-size:32px;margin-top:12px;line-height:1.2">Instagram ist während deiner Nachtschicht pausiert.</div>
  <p class="mut" style="margin-top:16px;font-size:15px">Du hast das selbst so eingestellt, und dein Kopf wird es dir danken.</p>
  <div class="card" style="margin-top:34px;padding:18px;background:#0E1B26"><div class="lbl">Wieder verfügbar in</div><div class="num" style="font-size:52px;margin-top:8px">2:48 <span style="font-size:18px" class="mut">h</span></div><div class="bar" style="margin-top:14px"><i style="width:65%"></i></div><div class="mut" style="font-size:12.5px;margin-top:8px">Schichtende um 06:00</div></div>
</div>
<div style="position:absolute;left:24px;right:24px;bottom:56px" class="gap10">
  <div class="btn">Zurück</div>
  <div class="btn ghost" style="height:46px;color:var(--tx2)">5 Minuten freigeben</div>
  <div class="mut3" style="text-align:center;font-size:12px">Heute schon 1× freigegeben</div>
</div>
${gest}`);

/* 18 */
add('blaufilter','Blaufilter & Nacht','Intensität, Farbtemperatur, Zeitplan und Dimmung mit Live-Vorschau.', `
${statusbar('14:38')}
${appbar('Blaufilter & Nacht', ib('back'))}
<div class="content">
  <div style="height:120px;position:relative;border:1px solid var(--line);overflow:hidden;display:grid;grid-template-columns:1fr 1fr">
    <div style="background:#E9EEF3;padding:14px;color:#11202C"><div class="lbl" style="color:#5A606B">Ohne</div><div class="h" style="font-size:18px;margin-top:8px">02:31 · Einsatz</div><div style="font-size:12px;color:#5A606B">Internistisch</div></div>
    <div style="background:#E9EEF3;padding:14px;color:#11202C;position:relative"><div style="position:absolute;inset:0;background:rgba(255,140,30,.38);mix-blend-mode:multiply"></div><div style="position:absolute;inset:0;background:rgba(0,0,0,.18)"></div><div style="position:relative"><div class="lbl" style="color:#5A606B">Mit Filter</div><div class="h" style="font-size:18px;margin-top:8px">02:31 · Einsatz</div><div style="font-size:12px;color:#5A606B">Internistisch</div></div></div>
  </div>
  <div class="card acct row sp" style="margin-top:12px"><div><div class="h" style="font-size:19px">Blaufilter</div><div class="mut" style="font-size:13px">gerade aktiv bis 06:00</div></div><div class="sw on"></div></div>
  <div class="card" style="margin-top:8px">
    <div class="row sp"><span class="lbl">Intensität</span><span class="num" style="font-size:20px">40 %</span></div>
    <div style="position:relative;height:28px;margin-top:6px"><div class="bar" style="position:absolute;top:11px;left:0;right:0"><i style="width:40%"></i></div><div style="position:absolute;left:calc(40% - 11px);top:3px;width:22px;height:22px;background:#fff;border:3px solid var(--red);border-radius:11px"></div></div>
    <div class="row sp" style="margin-top:14px"><span class="lbl">Farbtemperatur</span><span style="font-size:13.5px">Warm · 2700 K</span></div>
    <div style="height:10px;margin-top:10px;background:linear-gradient(90deg,#FF8A3C,#FFC27A,#FFE9C9,#F2F4FF)"></div>
    <div style="position:relative;height:0"><div style="position:absolute;left:22%;top:-14px;width:4px;height:18px;background:#fff;border:1px solid #11202C"></div></div>
    <div class="row sp" style="margin-top:16px"><span class="lbl">Zusätzlich dimmen</span><span style="font-size:13.5px">15 %</span></div>
    <div class="bar" style="margin-top:10px"><i style="width:15%;background:var(--tx2)"></i></div>
  </div>
  <div class="lbl" style="margin-top:16px">Zeitplan</div>
  <div class="card" style="padding:0 16px;margin-top:8px">
    <div class="li"><div style="width:20px;height:20px;border:6px solid var(--red);border-radius:10px;flex:none"></div><div class="gr"><div class="ti">Mit der Nachtschicht</div><div class="su">startet und endet mit der Schicht</div></div></div>
    <div class="li"><div style="width:20px;height:20px;border:1.5px solid var(--line);border-radius:10px;flex:none"></div><div class="gr"><div class="ti">Feste Zeit</div><div class="su">22:00 – 06:00</div></div></div>
  </div>
  <div class="info" style="margin-top:10px">${ic('info','')}<span>Das Android-Nachtlicht kann zusätzlich laufen. Für einen gleichmäßigen Ton empfehlen wir nur einen Filter.</span></div>
</div>
${gest}`);

/* 19 */
const qs=(icn,t,s,on,brand=false)=>`<div style="height:72px;border-radius:22px;padding:0 14px;display:flex;align-items:center;gap:12px;background:${on?(brand?'#B9303F':'#C9D7E6'):'#26313B'};color:${on?(brand?'#fff':'#0E1A24'):'#DDE3E8'}"><div style="width:40px;height:40px;border-radius:20px;display:flex;align-items:center;justify-content:center;background:${on?(brand?'rgba(255,255,255,.18)':'rgba(14,26,36,.1)'):'#34414C'}">${ic(icn,'ico" style="width:20px;height:20px')}</div><div style="min-width:0"><div style="font-size:14px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${t}</div><div style="font-size:12px;opacity:.75;white-space:nowrap">${s}</div></div></div>`;
add('schnelleinstellungen','Android-Schnelleinstellungen','Drei SchichtClever-Kacheln im System-Panel: Schicht, neuer Einsatz und Fokus.', `
<div style="position:absolute;inset:0;background:#05090D"></div>
<div style="position:absolute;inset:0;background:#141C23;bottom:110px;border-radius:0 0 28px 28px"></div>
<div style="position:relative;color:#E8ECF0">
  <div class="sb" style="height:44px"><span style="font-size:15px">02:47</span><span class="ic">${dndIcon}<svg viewBox="0 0 24 24"><path d="M2 20h3v-4H2zM7 20h3v-8H7zM12 20h3V8h-3zM17 20h3V4h-3z"/></svg> 64 %</span></div>
  <div style="padding:2px 22px 0"><div style="font-size:42px;font-weight:400;letter-spacing:-.01em">02:47</div><div style="font-size:14px;opacity:.7">Sa., 28. Nov.</div></div>
  <div style="padding:20px 16px 0">
    <div style="height:44px;border-radius:22px;background:#26313B;position:relative;overflow:hidden"><div style="position:absolute;left:0;top:0;bottom:0;width:38%;background:#C9D7E6;border-radius:22px"></div><div style="position:absolute;left:14px;top:12px;color:#0E1A24">${ic('sun','ico" style="width:20px;height:20px')}</div></div>
    <div class="grid2" style="margin-top:14px;gap:10px">
      ${qs('wifi','Internet','Telekom 5G',true)}
      ${qs('bt','Bluetooth','Aus',false)}
      ${qs('dnd','Nicht stören','Bis 06:00',true)}
      ${qs('torch','Taschenlampe','Aus',false)}
    </div>
    <div class="row sp" style="margin:20px 6px 10px"><span style="font-size:12.5px;opacity:.7;letter-spacing:.04em">SCHICHTCLEVER</span><span style="font-size:12px;opacity:.5">Kacheln bearbeiten</span></div>
    <div class="grid2" style="gap:10px">
      ${qs('moon','Schicht · Nacht','aktiv seit 4:47 h',true,true)}
      ${qs('plus','+ Einsatz','Alarmzeit jetzt',false)}
      ${qs('focus','Fokus an','4 Apps pausiert',true,true)}
      ${qs('rotate','Auto-Drehen','Aus',false)}
    </div>
    <div class="row" style="justify-content:center;gap:6px;margin-top:20px"><i style="width:7px;height:7px;border-radius:4px;background:#E8ECF0"></i><i style="width:7px;height:7px;border-radius:4px;background:#5A6670"></i></div>
    <div class="row sp" style="margin:22px 6px 0;opacity:.75;font-size:13px"><span>RTW 1/83-1 · Nachtschicht</span><span class="row" style="gap:18px">${ic('edit','ico" style="width:19px;height:19px')}${ic('gear','ico" style="width:19px;height:19px')}</span></div>
  </div>
</div>
<div style="position:absolute;left:16px;right:16px;bottom:30px;background:#141C23;border-radius:22px;padding:12px 16px;color:#E8ECF0;display:flex;gap:12px;align-items:center"><div style="width:34px;height:34px;background:#B9303F;display:flex;align-items:center;justify-content:center;border-radius:17px">${ic('moon','ico" style="width:18px;height:18px;stroke:#fff')}</div><div style="flex:1"><div style="font-size:13px;font-weight:500">SchichtClever · Nachtschicht läuft</div><div style="font-size:12px;opacity:.7">4 Einsätze · noch 3:13 h</div></div></div>
${gest}`);

/* 20 */
const set=(icn,t,s,right=ic('chev','ico" style="color:var(--tx3)'))=>`<div class="li"><div class="sq">${ic(icn,'')}</div><div class="gr"><div class="ti">${t}</div>${s?`<div class="su">${s}</div>`:''}</div>${right}</div>`;
add('mehr-einstellungen','Mehr: Einstellungen & Datenschutz','Profil, Design-Automatik, Maßnahmen-Katalog, Export und das Versprechen „nur auf diesem Gerät“.', `
${statusbar('14:40')}
${appbar('Mehr')}
<div class="content">
  <div class="card acc row" style="padding:14px 16px"><div class="sq" style="background:var(--red);color:#fff;font-family:Oswald;font-size:18px">NS</div><div style="flex:1"><div style="font-size:16px;font-weight:500">Rettungsdienst</div><div class="mut" style="font-size:13px">Notfallsanitäter:in · RTW / NEF</div></div><span class="red h" style="font-size:13.5px;letter-spacing:.06em">ÄNDERN</span></div>
  <div class="lbl" style="margin-top:16px">Design</div>
  <div class="seg" style="grid-template-columns:repeat(3,1fr);margin-top:8px"><div>Hell</div><div>Dunkel</div><div class="on">Nach Schicht</div></div>
  <div class="mut" style="font-size:12px;margin-top:6px">Tagschichten hell, Nacht- und 24-h-Schichten dunkel.</div>
  <div class="card" style="padding:0 16px;margin-top:14px">
    ${set('focus','Fokus-Modus','4 Apps · Nicht stören')}
    ${set('filter','Blaufilter & Nacht','mit Nachtschicht · 40 %')}
    ${set('shift','Schichtvorlagen','5 Vorlagen')}
    ${set('list','Maßnahmen-Katalog bearbeiten','62 Maßnahmen · 6 Gruppen')}
    ${set('download','Exportieren','CSV oder PDF für Nachweis & Logbuch')}
  </div>
  <div class="card" style="margin-top:8px;padding:14px 16px;background:rgba(63,169,122,.08);border-color:rgba(63,169,122,.35)"><div class="row" style="align-items:flex-start">${ic('shield','ico" style="color:var(--ok);flex:none')}<div><div style="font-weight:500">Alle Daten nur auf diesem Gerät</div><div class="mut" style="font-size:12.5px;margin-top:2px">Keine Cloud, kein Konto, keine Internet-Berechtigung. Bei Deinstallation ist alles weg, also vorher exportieren.</div></div></div></div>
  <div class="row sp" style="margin-top:16px;padding:0 2px"><div class="row" style="gap:10px">${cleverle(true,22)}<span class="mut" style="font-size:12.5px">Teil von <span class="word" style="color:var(--tx)">Medi<b>Clever</b></span></span></div><span class="mut3" style="font-size:12px">Version 0.1.0</span></div>
</div>
${nav(4)}${gest}`);

module.exports = S;
