CREATE TABLE source (
    id   smallint PRIMARY KEY,
    code text NOT NULL UNIQUE,
    name text NOT NULL
);
INSERT INTO source (id, code, name) VALUES
    (1, 'spotify', 'Spotify Web API'),
    (2, 'ytmusic', 'YouTube Music');

CREATE TABLE raw_track (
    id          bigserial PRIMARY KEY,
    source_id   smallint    NOT NULL REFERENCES source (id),
    external_id text        NOT NULL,
    query       text,
    payload     jsonb       NOT NULL,
    fetched_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (source_id, external_id)
);

CREATE TABLE track (
    id          bigserial PRIMARY KEY,
    source_id   smallint NOT NULL REFERENCES source (id),
    external_id text     NOT NULL,
    raw_id      bigint   REFERENCES raw_track (id) ON DELETE SET NULL,
    title       text     NOT NULL,
    artists     text[]   NOT NULL DEFAULT '{}',
    album       text,
    duration_ms integer,
    isrc        char(12),
    title_norm  text,
    artist_norm text,
    UNIQUE (source_id, external_id)
);
CREATE INDEX track_source_idx ON track (source_id);
CREATE INDEX track_isrc_idx   ON track (isrc) WHERE isrc IS NOT NULL;

CREATE TABLE gt_pair (
    spotify_track_id bigint  NOT NULL REFERENCES track (id) ON DELETE CASCADE,
    ytmusic_track_id bigint  NOT NULL REFERENCES track (id) ON DELETE CASCADE,
    is_match         boolean NOT NULL,
    note             text,
    annotated_at     timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (spotify_track_id, ytmusic_track_id)
);

CREATE TABLE match_run (
    id          bigserial PRIMARY KEY,
    method      text        NOT NULL,
    params      jsonb       NOT NULL DEFAULT '{}',
    dataset_size integer,
    started_at  timestamptz NOT NULL DEFAULT clock_timestamp(),
    finished_at timestamptz,
    duration_ms numeric
);

CREATE TABLE match_result (
    run_id           bigint  NOT NULL REFERENCES match_run (id) ON DELETE CASCADE,
    spotify_track_id bigint  NOT NULL REFERENCES track (id) ON DELETE CASCADE,
    ytmusic_track_id bigint  NOT NULL REFERENCES track (id) ON DELETE CASCADE,
    score            numeric,
    PRIMARY KEY (run_id, spotify_track_id, ytmusic_track_id)
);
