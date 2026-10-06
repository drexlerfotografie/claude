# Drexler Claude Marketplace

Persoenlicher Claude Code Plugin-Marktplatz.

## Marketplace hinzufuegen

In Claude Code:

```text
/plugin marketplace add drexlerfotografie/claude
```

Alternativ kann die Repository-URL verwendet werden:

```text
https://github.com/drexlerfotografie/claude.git
```

## Starter-Plugin installieren

```text
/plugin install drexler-starter@drexler-claude
```

Danach steht der Testbefehl zur Verfuegung:

```text
/drexler-starter:status
```

## Struktur

```text
.claude-plugin/
  marketplace.json

plugins/
  drexler-starter/
    .claude-plugin/
      plugin.json
    commands/
      status.md
```

Weitere Plugins koennen unter `plugins/<plugin-name>/` angelegt und anschliessend in
`.claude-plugin/marketplace.json` registriert werden.
