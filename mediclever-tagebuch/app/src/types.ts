export interface Mood { id: number; level: number; name: string; icon: string; position: number; archived: boolean }
export interface Group { id: number; name: string; color: string; position: number; collapsed: boolean; archived: boolean }
export interface Tag { id: number; group_id: number | null; name: string; icon: string; color: string; position: number; archived: boolean }
export interface ScalePoint { id: number; position: number; label: string }
export interface Scale {
  id: number; type: 'num' | 'text' | 'time'; name: string; color: string; icon: string; position: number;
  chart_mode: 'avg' | 'sum'; trend: 'up' | 'down'; min: number | null; max: number | null; archived: boolean; points: ScalePoint[];
}
export interface Template { id: number; title: string; body: string; position: number }
export interface Reminder { id: number; time: string; text: string; active: boolean }
export interface Setup { moods: Mood[]; groups: Group[]; tags: Tag[]; scales: Scale[]; templates: Template[]; reminders: Reminder[] }

export interface ScaleValue { scale_id: number; point_id: number | null; num: number | null; minutes: number | null }
export interface Entry {
  id: number; date: string; time: string; ts_utc: string; tz_offset: number; mood_id: number; level: number; title: string; note: string;
  favorite: boolean; source: string; tags: number[]; scales: ScaleValue[]; assets: { id: number; type: 'photo' | 'audio' }[];
}

export interface Goal {
  id: number; name: string; tag_id: number | null; freq_type: 'daily' | 'weekly' | 'days'; freq_count: number; freq_days: number[];
  reminder_time: string | null; reminder_text: string; start_date: string; end_date: string | null; note: string; position: number; archived: boolean;
  total: number; streak: number; best_streak: number; rate7: number; rate30: number; done_today: boolean; due_today: boolean;
  level: number; next_level_at: number | null; per_month: Record<string, number>; month_checks?: string[];
}
export interface Milestone {
  id: number; name: string; date: string; category: string; note: string; asset_id: number | null; yearly: boolean;
  remind_same_day: boolean; remind_days_before: number; days_since: number; next_date: string | null; days_until: number | null; next_years: number | null;
}

export interface Settings {
  week_start: number; midnight_rule: 'ask' | 'prev' | 'current'; midnight_until: number; quotes: boolean; lookback: boolean; report_notify: boolean;
  color_scheme: string; custom_colors: string[]; font: string; color_mode: 'system' | 'light' | 'dark'; timezone: string;
  consent_at: string | null; ai_consent_at: string | null; adult: boolean; onboarded: boolean;
}
export interface Pack { key: string; label: string; groups: { name: string; tags: string[] }[]; scales: string[] }
export interface Boot {
  user: { name: string }; settings: Settings; features: Record<string, boolean>; setup: Setup; months: { month: string; n: string }[];
  today: string; goals: Goal[]; milestones: Milestone[]; packs: Pack[]; push_key: string | null; mcp_url: string; version: string;
}
export interface Streaks { current: number; longest: number; days_total: number; last5: { date: string; done: boolean }[] }
export interface Home {
  month: string; today: string; entries: Entry[]; months: { month: string; n: string }[]; goals: Goal[]; milestones: Milestone[];
  lookback: { years_ago: number; count: number; years: number } | null; streak: Streaks;
}
export interface DayStat { date: string; avg: number; n: number; tags: number[]; moods: number[] }
export interface ScaleStat { scale_id: number; series: { date: string; value: number }[]; avg: number | null; n: number; prev_avg: number | null; change: number | null }
export interface Stats {
  period: 'month' | 'year'; from: string; to: string; count: number; enough: boolean; min_entries: number; avg: number | null; prev_avg: number | null;
  days: DayStat[]; months: { month: string; avg: number; n: number }[]; levels: Record<string, number>; moods: { mood_id: number; n: number }[];
  tag_counts: { tag_id: number; n: number }[]; group_counts: { group_id: number; n: number }[];
  weekday_avg: { weekday: number; avg: number | null; n: number }[]; influence: { tag_id: number; delta: number; n: number; avg: number }[];
  stability: number | null; prev_stability: number | null; often_together: { level: number; tags: { tag_id: number; n: number }[] }[];
  good_streak: { length: number; from: string | null; to: string | null; tags: number[] }; scales: ScaleStat[]; streaks: Streaks; words: number;
  hints: string[]; goals: Goal[];
}
export interface CalendarData {
  month: string; filtered: boolean; matches: number; count: number; days: { date: string; n: number; level: number; match: boolean }[];
  levels: Record<string, number>; hints: string[];
}
export interface ReportSum { entries: number; activities: number; avg: number | null; words: number }
export interface Report {
  type: 'week' | 'month'; from: string; to: string; current: ReportSum; previous: ReportSum;
  change: { entries: number; activities: number; avg: number | null; words: number }; days: DayStat[];
  top_tags: { tag_id: number; n: number }[]; goals: { goal_id: number; n: number }[]; photos: number[]; enough: boolean;
}
export interface Achievement { key: string; group: string; group_label: string; name: string; target: number; value: number; reached: boolean }
export interface Grant { id: number; client: string; scopes: string[]; granted_at: string; last_used_at: string | null; revoked: boolean }
export interface ImportResult { created: number; skipped: number; errors: number; warnings: string[] }
