import { ICONS, ICON_META } from './icons.gen';
import type { JSX } from 'preact';

export { ICON_META };

/**
 * Eigene Stimmungssymbole (m1 bis m5) in eigener Formensprache: ein offener Kreisbogen mit einer Linie,
 * die von „hoch“ nach „tief“ wandert. Jede Stufe ist an der Form erkennbar, nicht nur an der Farbe.
 */
const MOOD_PATHS: Record<string, JSX.Element> = {
  m1: <><circle cx="12" cy="12" r="9" /><path d="M7.5 12.5c1.2 3 7.8 3 9 0" /><path d="M12 3v2.2M5.6 5.6l1.5 1.5M18.4 5.6l-1.5 1.5" /></>,
  m2: <><circle cx="12" cy="12" r="9" /><path d="M8 13.2c1.3 1.7 6.7 1.7 8 0" /><path d="M9 9.2h.01M15 9.2h.01" /></>,
  m3: <><circle cx="12" cy="12" r="9" /><path d="M8.2 14h7.6" /><path d="M9 9.5h.01M15 9.5h.01" /></>,
  m4: <><circle cx="12" cy="12" r="9" /><path d="M8 15.2c1.3-1.7 6.7-1.7 8 0" /><path d="M9 9.8h.01M15 9.8h.01" /></>,
  m5: <><circle cx="12" cy="12" r="9" /><path d="M7.5 16c1.2-3 7.8-3 9 0" /><path d="M8 8.2l2.2 1.3M16 8.2l-2.2 1.3" /></>,
};

export const MOOD_ICONS = ['m1', 'm2', 'm3', 'm4', 'm5', 'sun', 'cloud-sun', 'cloud', 'cloud-rain', 'cloud-lightning', 'heart', 'star', 'sparkles', 'leaf', 'zap',
  'battery', 'battery-low', 'moon', 'flame', 'heart-crack', 'face-grinning', 'face-slightly-smiling', 'face-neutral', 'face-slightly-frowning', 'face-angry', 'thumbs-up', 'thumbs-down'];

interface Props { name: string; size?: number; color?: string; class?: string; label?: string }

export function Icon({ name, size = 22, color, class: cls, label }: Props): JSX.Element {
  const common = {
    width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: color || 'currentColor', 'stroke-width': 2,
    'stroke-linecap': 'round' as const, 'stroke-linejoin': 'round' as const, class: 'mct-icon ' + (cls || ''),
    ...(label ? { role: 'img' as const, 'aria-label': label } : { 'aria-hidden': 'true' as const }),
  };
  if (MOOD_PATHS[name]) return <svg {...common}>{MOOD_PATHS[name]}</svg>;
  const node = ICONS[name] || ICONS.circle;
  return (
    <svg {...common}>
      {node.map(([tag, attrs], i) => {
        const T = tag as 'path';
        return <T key={i} {...(attrs as Record<string, string>)} />;
      })}
    </svg>
  );
}
