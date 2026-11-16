import { render, type JSX } from 'preact';
import { useEffect, useState } from 'preact/hooks';
import '@fontsource/roboto/latin-400.css';
import '@fontsource/roboto/latin-ext-400.css';
import '@fontsource/roboto/latin-500.css';
import '@fontsource/roboto/latin-ext-500.css';
import '@fontsource/roboto/latin-700.css';
import '@fontsource/roboto/latin-ext-700.css';
import '@fontsource/roboto/latin-900.css';
import '@fontsource/roboto/latin-ext-900.css';
import './style.css';
import { cfg, post } from './api';
import { state, useStore, useRoute, loadBoot, changed, fail, go, feature, type Route } from './store';
import { Icon } from './icons';
import { DialogHost, Field, Loading, Sheet, Toasts } from './ui';
import { addDays, levelColors } from './format';
import { HomeScreen, SearchScreen } from './home';
import { EditorScreen } from './editor';
import { CalendarScreen, StatsScreen } from './stats';
import { AccountScreen, AchievementsScreen, AiScreen, AppearanceScreen, DayScreen, GoalsScreen, LookbackScreen, MilestonesScreen, MoreScreen, PhotosScreen, RemindersScreen, ReportsScreen, SecurityScreen, SettingsScreen } from './more';
import { MoodsScreen, OrderScreen, ScalesScreen, SetupScreen, TagsScreen, TemplatesScreen } from './setup';
import { DataScreen } from './data';
import { checkPin, isLocked, lock } from './pin';

function useColorMode(): 'light' | 'dark' {
  const s = useStore();
  const pref = s.boot?.settings.color_mode || 'system';
  const [sys, setSys] = useState(window.matchMedia('(prefers-color-scheme: dark)').matches);
  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const on = () => setSys(mq.matches);
    mq.addEventListener('change', on);
    return () => mq.removeEventListener('change', on);
  }, []);
  return pref === 'system' ? (sys ? 'dark' : 'light') : pref;
}

/** Erster Start: Einwilligung, dann ein Startpaket als Vorschlag, leer oder Import. */
function Onboarding(): JSX.Element {
  const boot = useStore().boot!;
  const [adult, setAdult] = useState(false);
  const [consent, setConsent] = useState(false);
  const [pack, setPack] = useState('allgemein');
  const [busy, setBusy] = useState(false);
  const needConsent = !boot.settings.consent_at;
  const agree = async () => { setBusy(true); try { await post('/consent', { consent: true, adult }); await changed(); } catch (e) { fail(e); } setBusy(false); };
  const start = async (p: string, then?: string) => {
    setBusy(true);
    try {
      await post('/settings', { timezone: Intl.DateTimeFormat().resolvedOptions().timeZone });
      await post('/onboarding', { pack: p });
      await changed();
      if (then) go(then);
    } catch (e) { fail(e); }
    setBusy(false);
  };
  const chosen = boot.packs.find((p) => p.key === pack);
  return (
    <div class="mct-onboard">
      <div class="mct-brand"><img src={cfg.assets + 'logo-72.png'} alt="" width="44" height="44" /><div><span class="mct-kicker">MediClever</span><h1>Tagebuch</h1></div></div>
      {needConsent ? (
        <section class="mct-card">
          <p class="mct-steps">Schritt 1 von 2</p>
          <h2>Bevor es losgeht</h2>
          <p>Hier hältst du fest, wie es dir geht und was dich beschäftigt. Stimmung, Schlaf, Schmerzen, Krankheitstage und Privates sind besonders geschützte Angaben. Deshalb fragen wir ausdrücklich nach.</p>
          <ul class="mct-hints">
            <li>Dein Tagebuch ist nur für dein Konto sichtbar.</li>
            <li>Du kannst alles exportieren und vollständig löschen.</li>
            <li>Bitte trag keine Patienten- und Einsatzdaten ein.</li>
            <li>Ein KI-Zugriff ist ausgeschaltet und braucht eine eigene Einwilligung.</li>
          </ul>
          <label class="mct-checkline"><input type="checkbox" checked={adult} onChange={(e) => setAdult((e.target as HTMLInputElement).checked)} /><span>Ich bin mindestens 18 Jahre alt.</span></label>
          <label class="mct-checkline"><input type="checkbox" checked={consent} onChange={(e) => setConsent((e.target as HTMLInputElement).checked)} /><span>Ich willige ein, dass MediClever meine Tagebuchdaten einschließlich Gesundheitsangaben speichert, um mir das Tagebuch bereitzustellen. Ich kann die Einwilligung jederzeit widerrufen.</span></label>
          <button type="button" class="mct-btn primary block" disabled={!adult || !consent || busy} onClick={agree}>Einwilligen und weiter</button>
          <p class="mct-muted small">Keine medizinische Beratung. Im Notfall wähle 112.</p>
        </section>
      ) : (
        <section class="mct-card">
          <p class="mct-steps">Schritt 2 von 2</p>
          <h2>Womit möchtest du starten?</h2>
          <p>Ein Startpaket ist nur ein Vorschlag. Du kannst danach alles umbenennen, löschen und ergänzen.</p>
          <div class="mct-packs" role="radiogroup" aria-label="Startpaket">
            {boot.packs.map((p) => (
              <button type="button" key={p.key} role="radio" aria-checked={pack === p.key} class={pack === p.key ? 'on' : ''} onClick={() => setPack(p.key)}>
                <strong>{p.label}</strong>
                <small>{p.groups.length ? `${p.groups.length} Gruppen, ${p.groups.reduce((n, g) => n + g.tags.length, 0)} Aktivitäten, ${p.scales.length} Skalen` : 'Nur die fünf Stimmungsstufen'}</small>
              </button>
            ))}
          </div>
          {chosen && chosen.groups.length > 0 && (
            <div class="mct-packpreview" aria-live="polite">
              {chosen.groups.map((g) => <p key={g.name}><strong>{g.name}:</strong> {g.tags.join(', ')}</p>)}
              <p><strong>Skalen:</strong> {chosen.scales.join(', ')}</p>
            </div>
          )}
          <button type="button" class="mct-btn primary block" disabled={busy} onClick={() => start(pack)}>{pack === 'leer' ? 'Leer starten' : 'Mit diesem Paket starten'}</button>
          {feature('import') && <button type="button" class="mct-btn ghost block" disabled={busy} onClick={() => start('leer', '/mehr/daten')}><Icon name="upload" size={18} />Stattdessen eine Sicherung importieren</button>}
        </section>
      )}
    </div>
  );
}

function PinLock({ onOpen }: { onOpen: () => void }): JSX.Element {
  const [pin, setPin] = useState('');
  const [wrong, setWrong] = useState(false);
  const submit = async () => { if (await checkPin(pin)) onOpen(); else { setWrong(true); setPin(''); } };
  return (
    <div class="mct-onboard">
      <div class="mct-brand"><img src={cfg.assets + 'logo-72.png'} alt="" width="44" height="44" /><div><span class="mct-kicker">MediClever</span><h1>Tagebuch</h1></div></div>
      <form class="mct-card" onSubmit={(e) => { e.preventDefault(); submit(); }}>
        <h2>Gesperrt</h2>
        <Field label="PIN eingeben"><input type="password" inputMode="numeric" autoComplete="off" autoFocus value={pin} maxLength={8} onInput={(e) => { setPin((e.target as HTMLInputElement).value); setWrong(false); }} /></Field>
        {wrong && <p class="mct-error" role="alert">Die PIN stimmt nicht.</p>}
        <button type="submit" class="mct-btn primary block">Entsperren</button>
        <a class="mct-link" href={cfg.logout}>PIN vergessen? Abmelden</a>
      </form>
    </div>
  );
}

/** Schnellwahl der Plus-Schaltfläche. */
function PlusSheet({ onClose }: { onClose: () => void }): JSX.Element {
  const boot = state.boot!;
  const [other, setOther] = useState(false);
  const [date, setDate] = useState(addDays(boot.today, -2));
  const open = (path: string) => { onClose(); go(path); };
  return (
    <Sheet title="Neu anlegen" onClose={onClose}>
      <div class="mct-rows">
        <button type="button" class="mct-row" onClick={() => open(`/eintrag/neu?date=${boot.today}`)}><span class="mct-row-icon"><Icon name="notebook-pen" /></span><span class="mct-row-text">Heute</span></button>
        <button type="button" class="mct-row" onClick={() => open(`/eintrag/neu?date=${addDays(boot.today, -1)}&fix=1`)}><span class="mct-row-icon"><Icon name="history" /></span><span class="mct-row-text">Gestern</span></button>
        <button type="button" class="mct-row" aria-expanded={other} onClick={() => setOther(!other)}><span class="mct-row-icon"><Icon name="calendar" /></span><span class="mct-row-text">Anderer Tag</span></button>
        {other && (
          <div class="mct-otherday">
            <Field label="Datum"><input type="date" value={date} max={boot.today} onInput={(e) => setDate((e.target as HTMLInputElement).value)} /></Field>
            <button type="button" class="mct-btn primary" disabled={!date} onClick={() => open(`/eintrag/neu?date=${date}&fix=1`)}>Eintrag schreiben</button>
          </div>
        )}
        {feature('goals') && <button type="button" class="mct-row" onClick={() => open('/mehr/ziele?neu=1')}><span class="mct-row-icon"><Icon name="target" /></span><span class="mct-row-text">Neues Ziel</span></button>}
        {feature('milestones') && <button type="button" class="mct-row" onClick={() => open('/mehr/wichtige-tage?neu=1')}><span class="mct-row-icon"><Icon name="flag" /></span><span class="mct-row-text">Neuer wichtiger Tag</span></button>}
      </div>
    </Sheet>
  );
}

function Screen({ route }: { route: Route }): JSX.Element {
  const [a, b] = route.parts;
  if (a === 'eintrag') return <EditorScreen route={route} key={route.path} />;
  if (a === 'suche') return <SearchScreen />;
  if (a === 'tag' && b) return <DayScreen route={route} />;
  if (a === 'statistik' && feature('stats')) return <StatsScreen />;
  if (a === 'kalender' && feature('calendar')) return <CalendarScreen />;
  if (a === 'mehr') {
    switch (b) {
      case undefined: return <MoreScreen />;
      case 'ziele': return <GoalsScreen route={route} />;
      case 'berichte': return <ReportsScreen route={route} />;
      case 'rueckblick': return <LookbackScreen />;
      case 'wichtige-tage': return <MilestonesScreen route={route} />;
      case 'favoriten': return <SearchScreen favorites />;
      case 'fotos': return <PhotosScreen />;
      case 'erfolge': return <AchievementsScreen />;
      case 'erinnerungen': return <RemindersScreen />;
      case 'einrichtung': return <SetupScreen />;
      case 'stimmungen': return <MoodsScreen />;
      case 'aktivitaeten': return <TagsScreen />;
      case 'skalen': return <ScalesScreen />;
      case 'reihenfolge': return <OrderScreen />;
      case 'vorlagen': return <TemplatesScreen />;
      case 'aussehen': return <AppearanceScreen />;
      case 'sicherheit': return <SecurityScreen />;
      case 'einstellungen': return <SettingsScreen />;
      case 'daten': return <DataScreen />;
      case 'ki': return <AiScreen />;
      case 'konto': return <AccountScreen />;
    }
    return <MoreScreen />;
  }
  return <HomeScreen />;
}

function App(): JSX.Element {
  const s = useStore();
  const route = useRoute();
  const mode = useColorMode();
  const [plus, setPlus] = useState(false);
  const [locked, setLocked] = useState(isLocked());

  useEffect(() => { loadBoot(); }, []);
  // Beim Seitenwechsel (auch per Zurück-Taste) schließt die Schnellwahl.
  useEffect(() => { setPlus(false); }, [route.path]);
  useEffect(() => {
    // Nach längerer Abwesenheit wieder sperren.
    let hiddenAt = 0;
    const on = () => {
      if (document.hidden) hiddenAt = Date.now();
      else if (hiddenAt && Date.now() - hiddenAt > 120000) { lock(); setLocked(isLocked()); }
    };
    document.addEventListener('visibilitychange', on);
    return () => document.removeEventListener('visibilitychange', on);
  }, []);

  const set = s.boot?.settings;
  const colors = levelColors(set?.color_scheme || 'klar', set?.custom_colors || []);
  const style = Object.fromEntries(colors.map((c, i) => [`--l${i + 1}`, c]));
  const wrap = (child: JSX.Element, bare = false) => (
    <div class={'mct' + (bare ? ' bare' : '')} lang="de" data-mode={mode} data-font={set?.font || 'roboto'} style={style}>
      {child}<DialogHost /><Toasts />
    </div>
  );
  if (!s.boot) return wrap(<div class="mct-content"><Loading error={s.error} reload={loadBoot} /></div>, true);
  if (locked) return wrap(<PinLock onOpen={() => setLocked(false)} />, true);
  if (!set!.consent_at && route.parts[0] !== 'mehr') return wrap(<Onboarding />, true);
  if (!set!.onboarded) return wrap(<Onboarding />, true);

  const tab = route.parts[0] === 'eintrag' || route.parts[0] === 'suche' ? 'eintraege' : route.parts[0] === 'tag' ? 'kalender' : route.parts[0] || 'eintraege';
  const nav = [
    { key: 'eintraege', label: 'Einträge', icon: 'list', show: true },
    { key: 'statistik', label: 'Statistiken', icon: 'chart-column', show: feature('stats') },
    { key: 'plus', label: '', icon: 'plus', show: true },
    { key: 'kalender', label: 'Kalender', icon: 'calendar', show: feature('calendar') },
    { key: 'mehr', label: 'Mehr', icon: 'ellipsis', show: true },
  ].filter((n) => n.show);
  const editing = route.parts[0] === 'eintrag';
  return wrap(
    <>
      <a class="mct-skip" href="#mct-main" onClick={(e) => { e.preventDefault(); document.getElementById('mct-main')?.focus(); }}>Zum Inhalt springen</a>
      <nav class={'mct-nav' + (editing ? ' hidden-mobile' : '')} aria-label="Hauptbereiche">
        <div class="mct-nav-brand"><img src={cfg.assets + 'logo-72.png'} alt="" width="36" height="36" /><span><span class="mct-kicker">MediClever</span><strong>Tagebuch</strong></span></div>
        {nav.map((n) => n.key === 'plus'
          ? <button type="button" key="plus" class="mct-plus" aria-label="Neu anlegen" aria-haspopup="dialog" onClick={() => setPlus(true)}><Icon name="plus" size={28} /><span class="mct-plus-label">Neu anlegen</span></button>
          : <a key={n.key} href={'#/' + n.key} class={tab === n.key ? 'on' : ''} aria-current={tab === n.key ? 'page' : undefined}><Icon name={n.icon} size={24} /><span>{n.label}</span></a>)}
      </nav>
      <main id="mct-main" tabIndex={-1}><Screen route={route} /></main>
      {plus && <PlusSheet onClose={() => setPlus(false)} />}
    </>,
  );
}

const root = document.getElementById('mct-app');
if (root) {
  root.innerHTML = '';
  render(<App />, root);
}
