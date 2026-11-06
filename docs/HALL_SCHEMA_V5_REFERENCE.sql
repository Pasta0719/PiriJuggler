-- PiriJuggler autonomous hall reference migration.
-- AUTHORITATIVE DDL for v5 additions; apply once in one SQLite transaction.
-- Existing schema-v4.sql and rows MUST NOT be dropped/rewritten.
-- PRAGMA foreign_keys=ON; BEGIN IMMEDIATE before executing.
-- Caller inserts metadata.schema_version='5' at the end of same transaction.
-- All timestamp fields: UTC Unix epoch milliseconds; all dates: YYYY-MM-DD JST hallDay.
-- Amount text: signed decimal with exactly two decimal places; strict BigDecimal validation in Java.
-- NOTE: empty-DB path first initializes schema-v4.sql, then applies this script.
CREATE TABLE hall_days (
  day TEXT PRIMARY KEY,
  status TEXT NOT NULL CHECK(status IN ('PLANNING','OPEN','CLOSED','NO_ACTIVITY','FAILED')),
  opened_at INTEGER NOT NULL,
  closed_at INTEGER,
  seed INTEGER NOT NULL,
  policy_revision INTEGER NOT NULL DEFAULT 0,
  snapshot_revision INTEGER NOT NULL DEFAULT 0,
  config_hash TEXT NOT NULL,
  target_margin REAL NOT NULL CHECK(target_margin BETWEEN 0.0 AND 1.0),
  monthly_target TEXT,
  daily_target TEXT,
  predicted_cash_in TEXT,
  predicted_cash_out TEXT,
  predicted_gross TEXT,
  real_cash_in TEXT NOT NULL DEFAULT '0.00',
  real_cash_out TEXT NOT NULL DEFAULT '0.00',
  realized_gross TEXT NOT NULL DEFAULT '0.00',
  data_quality TEXT NOT NULL DEFAULT 'UNKNOWN' CHECK(data_quality IN ('REAL_VERIFIED','FORECAST_ESTIMATED','LEGACY_UNVERIFIED','UNMODELLED','UNKNOWN')),
  model_version TEXT NOT NULL
);

CREATE TABLE hall_machine_days (
  day TEXT NOT NULL,
  machine_id INTEGER NOT NULL,
  machine_type TEXT NOT NULL,
  planned_setting INTEGER NOT NULL CHECK(planned_setting BETWEEN 1 AND 6),
  applied_setting INTEGER CHECK(applied_setting BETWEEN 1 AND 6),
  planned_at INTEGER NOT NULL,
  applied_at INTEGER,
  apply_status TEXT NOT NULL CHECK(apply_status IN ('PENDING','APPLIED','SKIPPED','FAILED')),
  real_games INTEGER NOT NULL DEFAULT 0 CHECK(real_games >= 0),
  real_bet INTEGER NOT NULL DEFAULT 0 CHECK(real_bet >= 0),
  real_payout INTEGER NOT NULL DEFAULT 0 CHECK(real_payout >= 0),
  sim_games INTEGER NOT NULL DEFAULT 0 CHECK(sim_games >= 0),
  sim_bet INTEGER NOT NULL DEFAULT 0 CHECK(sim_bet >= 0),
  sim_payout INTEGER NOT NULL DEFAULT 0 CHECK(sim_payout >= 0),
  position_json TEXT NOT NULL,
  manual_override_id TEXT,
  PRIMARY KEY(day,machine_id),
  FOREIGN KEY(day) REFERENCES hall_days(day),
  FOREIGN KEY(machine_id) REFERENCES machines(machine_id)
);

CREATE TABLE hall_policies (
  policy_id TEXT PRIMARY KEY,
  kind TEXT NOT NULL,
  params_json TEXT NOT NULL,
  strength REAL NOT NULL CHECK(strength >= 0 AND strength <= 1),
  start_day TEXT NOT NULL,
  min_end_day TEXT NOT NULL,
  max_end_day TEXT NOT NULL,
  ended_at INTEGER,
  creation_reason_json TEXT NOT NULL,
  revision INTEGER NOT NULL,
  statistics_json TEXT NOT NULL
);
CREATE INDEX ix_hall_policies_active ON hall_policies(start_day,min_end_day,max_end_day);

CREATE TABLE hall_setting_decisions (
  day TEXT NOT NULL,
  machine_id INTEGER NOT NULL,
  old_setting INTEGER NOT NULL CHECK(old_setting BETWEEN 1 AND 6),
  proposed_setting INTEGER NOT NULL CHECK(proposed_setting BETWEEN 1 AND 6),
  score_json TEXT NOT NULL,
  reasons_json TEXT NOT NULL,
  rejected_reasons_json TEXT NOT NULL,
  forecast_json TEXT NOT NULL,
  seed INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  superseded_by_manual_at INTEGER,
  PRIMARY KEY(day,machine_id),
  FOREIGN KEY(day,machine_id) REFERENCES hall_machine_days(day,machine_id)
);

CREATE TABLE hall_manual_overrides (
  override_id TEXT PRIMARY KEY,
  scope TEXT NOT NULL CHECK(scope IN ('MACHINE','TYPE','ISLAND','DAY','MARGIN','PROFILE')),
  scope_value TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  start_day TEXT NOT NULL,
  end_day TEXT,
  priority INTEGER NOT NULL,
  actor_uuid TEXT,
  created_at INTEGER NOT NULL,
  revoked_at INTEGER,
  CHECK(end_day IS NULL OR end_day >= start_day)
);
CREATE INDEX ix_hall_manual_active ON hall_manual_overrides(start_day,end_day,revoked_at);

CREATE TABLE hall_economy_events (
  transaction_id TEXT PRIMARY KEY,
  day TEXT NOT NULL,
  occurred_at INTEGER NOT NULL,
  operation TEXT NOT NULL CHECK(operation IN ('LOAN','PACHINKO_LOAN','PRIZE_TO_VAULT')),
  amount_decimal TEXT NOT NULL,
  player_uuid TEXT NOT NULL,
  machine_id INTEGER,
  origin TEXT NOT NULL DEFAULT 'REAL' CHECK(origin='REAL'),
  status TEXT NOT NULL DEFAULT 'APPLIED' CHECK(status='APPLIED'),
  FOREIGN KEY(day) REFERENCES hall_days(day),
  FOREIGN KEY(machine_id) REFERENCES machines(machine_id)
);
CREATE INDEX ix_hall_economy_day ON hall_economy_events(day,operation);

CREATE TABLE hall_game_events (
  transaction_id TEXT PRIMARY KEY,
  hall_day TEXT NOT NULL,
  machine_id INTEGER NOT NULL,
  machine_type TEXT NOT NULL,
  setting INTEGER NOT NULL CHECK(setting BETWEEN 1 AND 6),
  bet INTEGER NOT NULL CHECK(bet>=0),
  payout INTEGER NOT NULL CHECK(payout>=0),
  game_delta INTEGER NOT NULL CHECK(game_delta>=0),
  origin TEXT NOT NULL CHECK(origin IN ('REAL','SIMULATED')),
  committed_at INTEGER NOT NULL,
  FOREIGN KEY(hall_day) REFERENCES hall_days(day),
  FOREIGN KEY(machine_id) REFERENCES machines(machine_id)
);
CREATE INDEX ix_hall_game_day_machine ON hall_game_events(hall_day,machine_id,origin);

CREATE TABLE hall_performance_cache (
  machine_type TEXT NOT NULL,
  setting INTEGER NOT NULL CHECK(setting BETWEEN 1 AND 6),
  config_hash TEXT NOT NULL,
  model_version TEXT NOT NULL,
  state_hash TEXT NOT NULL DEFAULT 'BASE',
  sample_games INTEGER NOT NULL CHECK(sample_games>=0),
  expected_bet REAL NOT NULL,
  expected_payout REAL NOT NULL,
  variance REAL NOT NULL,
  sample_distribution_json TEXT NOT NULL,
  confidence_json TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(machine_type,setting,config_hash,model_version,state_hash)
);

CREATE TABLE hall_audit_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  day TEXT,
  machine_id INTEGER,
  kind TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX ix_hall_audit_day_created ON hall_audit_log(day,created_at);

CREATE TABLE hall_layout_overrides (
  machine_id INTEGER NOT NULL,
  island_id TEXT NOT NULL,
  position INTEGER NOT NULL CHECK(position BETWEEN 1 AND 9999),
  starts_day TEXT NOT NULL,
  ended_day TEXT,
  PRIMARY KEY(machine_id,starts_day),
  FOREIGN KEY(machine_id) REFERENCES machines(machine_id),
  CHECK(ended_day IS NULL OR ended_day >= starts_day)
);
CREATE UNIQUE INDEX ux_hall_layout_active_position
  ON hall_layout_overrides(island_id,position)
  WHERE ended_day IS NULL;

-- Final step inside transaction (execute only after statements above succeeded):
-- UPDATE metadata SET value='5' WHERE key='schema_version' AND value='4';
-- Verify one row updated. COMMIT. All future startups must accept exactly 5.
