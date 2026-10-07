CREATE TABLE completed_race (
    id uuid PRIMARY KEY,
    room_code varchar(6) NOT NULL,
    start_article text NOT NULL,
    target_article text NOT NULL,
    unlimited boolean NOT NULL,
    time_limit_seconds integer,
    started_at timestamptz NOT NULL,
    sudden_death_started_at timestamptz,
    finished_at timestamptz NOT NULL,
    winner_player_id uuid NOT NULL,
    CHECK (start_article <> target_article),
    CHECK ((unlimited AND time_limit_seconds IS NULL) OR (NOT unlimited AND time_limit_seconds IS NOT NULL AND time_limit_seconds >= 180)),
    CHECK (finished_at >= started_at)
);

CREATE TABLE player_result (
    race_id uuid NOT NULL REFERENCES completed_race(id) ON DELETE CASCADE,
    player_id uuid NOT NULL,
    player_index integer NOT NULL CHECK (player_index >= 0),
    display_name text NOT NULL,
    click_count integer NOT NULL CHECK (click_count >= 0),
    final_article text NOT NULL,
    winner boolean NOT NULL,
    PRIMARY KEY (race_id, player_id),
    UNIQUE (race_id, player_index)
);
CREATE UNIQUE INDEX one_winner_per_race ON player_result(race_id) WHERE winner;
ALTER TABLE completed_race ADD CONSTRAINT winner_is_participant
    FOREIGN KEY (id, winner_player_id) REFERENCES player_result(race_id, player_id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE visit_step (
    race_id uuid NOT NULL,
    player_id uuid NOT NULL,
    step_index integer NOT NULL CHECK (step_index >= 0),
    article_title text NOT NULL,
    PRIMARY KEY (race_id, player_id, step_index),
    FOREIGN KEY (race_id, player_id) REFERENCES player_result(race_id, player_id) ON DELETE CASCADE
);
