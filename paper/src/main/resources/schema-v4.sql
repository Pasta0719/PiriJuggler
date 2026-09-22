CREATE TABLE metadata (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);


CREATE TABLE business_periods (
  business_period_id TEXT PRIMARY KEY,
  local_date TEXT NOT NULL,
  started_at INTEGER NOT NULL,
  jvm_start_ms INTEGER NOT NULL,
  profile_name TEXT NOT NULL,
  profile_source TEXT NOT NULL
);


CREATE TABLE machines (
  machine_id INTEGER PRIMARY KEY,
  world_uuid TEXT NOT NULL,
  world_name TEXT NOT NULL,
  x INTEGER NOT NULL,
  y INTEGER NOT NULL,
  z INTEGER NOT NULL,
  facing TEXT NOT NULL,
  setting INTEGER NOT NULL CHECK(setting BETWEEN 1 AND 6),
  enabled INTEGER NOT NULL DEFAULT 1,
  auto_setting INTEGER NOT NULL DEFAULT 1,
  deleted INTEGER NOT NULL DEFAULT 0,
  last_left_stop INTEGER NOT NULL DEFAULT 0 CHECK(last_left_stop BETWEEN 0 AND 20),
  last_center_stop INTEGER NOT NULL DEFAULT 0 CHECK(last_center_stop BETWEEN 0 AND 20),
  last_right_stop INTEGER NOT NULL DEFAULT 0 CHECK(last_right_stop BETWEEN 0 AND 20),
  machine_runtime_json TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX ux_machine_location_active
ON machines(world_uuid,x,y,z) WHERE deleted=0;


CREATE TABLE machine_period_stats (
  machine_id INTEGER NOT NULL,
  business_period_id TEXT NOT NULL,
  total_games INTEGER NOT NULL DEFAULT 0,
  big_count INTEGER NOT NULL DEFAULT 0,
  reg_count INTEGER NOT NULL DEFAULT 0,
  current_games INTEGER NOT NULL DEFAULT 0,
  today_difference INTEGER NOT NULL DEFAULT 0,
  today_max_difference INTEGER NOT NULL DEFAULT 0,
  last_bonus_type TEXT,
  last_bonus_at INTEGER,
  PRIMARY KEY(machine_id,business_period_id),
  FOREIGN KEY(machine_id) REFERENCES machines(machine_id),
  FOREIGN KEY(business_period_id) REFERENCES business_periods(business_period_id)
);


CREATE TABLE bonus_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  machine_id INTEGER NOT NULL,
  business_period_id TEXT NOT NULL,
  bonus_type TEXT NOT NULL CHECK(bonus_type IN ('BIG','REG')),
  games INTEGER NOT NULL,
  occurred_at INTEGER NOT NULL,
  FOREIGN KEY(machine_id) REFERENCES machines(machine_id),
  FOREIGN KEY(business_period_id) REFERENCES business_periods(business_period_id)
);


CREATE TABLE graph_points (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  machine_id INTEGER NOT NULL,
  business_period_id TEXT NOT NULL,
  game INTEGER NOT NULL,
  difference INTEGER NOT NULL,
  occurred_at INTEGER NOT NULL,
  FOREIGN KEY(machine_id) REFERENCES machines(machine_id),
  FOREIGN KEY(business_period_id) REFERENCES business_periods(business_period_id)
);


CREATE TABLE setting_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  machine_id INTEGER NOT NULL,
  business_period_id TEXT,
  changed_at INTEGER NOT NULL,
  old_setting INTEGER NOT NULL,
  new_setting INTEGER NOT NULL,
  reason TEXT NOT NULL,
  actor_uuid TEXT,
  profile_name TEXT,
  FOREIGN KEY(machine_id) REFERENCES machines(machine_id)
);


CREATE TABLE player_wallet (
  player_uuid TEXT PRIMARY KEY,
  pending_medals INTEGER NOT NULL DEFAULT 0 CHECK(pending_medals >= 0),
  updated_at INTEGER NOT NULL
);


CREATE TABLE player_sessions (
  session_id TEXT PRIMARY KEY,
  player_uuid TEXT NOT NULL UNIQUE,
  machine_id INTEGER NOT NULL,
  source_business_period_id TEXT NOT NULL,
  game_state TEXT NOT NULL,
  lifecycle TEXT NOT NULL,
  credit INTEGER NOT NULL CHECK(credit BETWEEN 0 AND 50),
  held_medals INTEGER NOT NULL CHECK(held_medals >= 0),
  spin_id TEXT,
  internal_role TEXT,
  premium_type TEXT,
  notice_state TEXT NOT NULL DEFAULT 'NONE',
  lamp_on INTEGER NOT NULL DEFAULT 0,
  bonus_type TEXT,
  bonus_payout_count INTEGER NOT NULL DEFAULT 0,
  current_bet INTEGER NOT NULL DEFAULT 0,
  pay_display INTEGER NOT NULL DEFAULT 0,
  display_left_stop INTEGER NOT NULL DEFAULT 0,
  display_center_stop INTEGER NOT NULL DEFAULT 0,
  display_right_stop INTEGER NOT NULL DEFAULT 0,
  stopped_mask INTEGER NOT NULL DEFAULT 0,
  phase_left REAL NOT NULL DEFAULT 0,
  phase_center REAL NOT NULL DEFAULT 0,
  phase_right REAL NOT NULL DEFAULT 0,
  motion_profile TEXT,
  machine_state_json TEXT,
  last_client_sequence INTEGER NOT NULL DEFAULT 0,
  last_activity INTEGER NOT NULL,
  lock_expires_at INTEGER,
  FOREIGN KEY(machine_id) REFERENCES machines(machine_id),
  FOREIGN KEY(source_business_period_id) REFERENCES business_periods(business_period_id)
);


CREATE TABLE cashout_transactions (
  transaction_id TEXT PRIMARY KEY,
  player_uuid TEXT NOT NULL,
  amount INTEGER NOT NULL CHECK(amount >= 0),
  delivered_amount INTEGER NOT NULL DEFAULT 0,
  pending_amount INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL CHECK(status IN ('PENDING','COMPLETED')),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);


CREATE TABLE medal_tokens (
  bundle_id TEXT PRIMARY KEY,
  amount INTEGER NOT NULL CHECK(amount BETWEEN 1 AND 500),
  state TEXT NOT NULL CHECK(state IN ('PENDING_DELIVERY','ACTIVE','RETIRED')),
  source_transaction_id TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);


CREATE TABLE medal_inventory_transactions (
  transaction_id TEXT PRIMARY KEY,
  player_uuid TEXT NOT NULL,
  operation TEXT NOT NULL,
  before_bundle_json TEXT NOT NULL,
  after_bundle_json TEXT NOT NULL,
  container_snapshot_json TEXT,
  status TEXT NOT NULL CHECK(status IN ('PREPARED','LEDGER_COMMITTED','APPLIED','ROLLED_BACK','REVIEW_REQUIRED')),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);


CREATE TABLE economy_transactions (
  transaction_id TEXT PRIMARY KEY,
  player_uuid TEXT NOT NULL,
  operation TEXT NOT NULL,
  vault_amount REAL NOT NULL,
  item_snapshot_json TEXT,
  balance_before REAL,
  status TEXT NOT NULL CHECK(status IN ('PREPARED','CALL_STARTED','APPLIED','ROLLED_BACK','REVIEW_REQUIRED')),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
