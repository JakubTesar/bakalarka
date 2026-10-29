# Entity resolution hudebních záznamů v PostgreSQL

Praktická část bakalářské práce na Unicorn University. Párování skladeb ze Spotify a YouTube Music pomocí rozšíření `pg_trgm`, `fuzzystrmatch` a `unaccent`.

## Spuštění

```
cp .env.example .env
docker compose up -d
./gradlew run --args="check"
```

## Sběr dat

```
./gradlew run --args="spotify <odkaz na playlist>..."
./gradlew run --args="ytmusic"
```

Data se ukládají do `data/raw` ve formátu JSON Lines.

## Struktura

- `db/init` – rozšíření a datový model
- `src/main/kotlin` – sběr dat
