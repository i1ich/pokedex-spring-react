-- Users: the secondary collection for user management (auth).
CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Local replica of PokeAPI data plus proprietary fields (US03, US04).
-- The primary key is the PokeAPI id, so syncing the same Pokémon twice is an idempotent upsert.
CREATE TABLE local_pokemon (
    id          INTEGER      PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    category    VARCHAR(100),
    weight_hg   INTEGER      NOT NULL CHECK (weight_hg >= 0),
    height_dm   INTEGER      NOT NULL CHECK (height_dm >= 0),
    sprite_url  VARCHAR(500),
    artwork_url VARCHAR(500),
    -- proprietary fields
    local_name  VARCHAR(100),
    region      VARCHAR(100),
    notes       VARCHAR(1000),
    synced_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE local_pokemon_ability (
    pokemon_id INTEGER      NOT NULL REFERENCES local_pokemon (id) ON DELETE CASCADE,
    position   INTEGER      NOT NULL,
    ability    VARCHAR(100) NOT NULL,
    PRIMARY KEY (pokemon_id, position)
);

CREATE TABLE local_pokemon_tag (
    pokemon_id INTEGER     NOT NULL REFERENCES local_pokemon (id) ON DELETE CASCADE,
    tag        VARCHAR(50) NOT NULL,
    PRIMARY KEY (pokemon_id, tag)
);
