package store

import (
	"database/sql"
	"fmt"
)

const schemaDDL = `
CREATE TABLE IF NOT EXISTS devices (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	device_name TEXT NOT NULL,
	mac_address TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS login_logs (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	ip TEXT NOT NULL,
	username TEXT NOT NULL,
	browser_info TEXT NOT NULL DEFAULT '',
	attempted_at TEXT NOT NULL,
	success INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_login_logs_attempted_at ON login_logs (attempted_at DESC);

CREATE TABLE IF NOT EXISTS stored_images (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	device_id INTEGER NOT NULL REFERENCES devices(id),
	path TEXT NOT NULL,
	captured_at TEXT NOT NULL,
	received_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_stored_images_device_captured_at
	ON stored_images (device_id, captured_at DESC);

CREATE TABLE IF NOT EXISTS phone_actions (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	device_id INTEGER NOT NULL REFERENCES devices(id),
	action_type TEXT NOT NULL,
	payload TEXT NOT NULL DEFAULT '{}',
	sent_at TEXT NOT NULL,
	status TEXT NOT NULL DEFAULT 'pending'
);

CREATE INDEX IF NOT EXISTS idx_phone_actions_device_status
	ON phone_actions (device_id, status, sent_at DESC);

CREATE TABLE IF NOT EXISTS webrtc_connections (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	device_id INTEGER NOT NULL REFERENCES devices(id),
	room TEXT NOT NULL,
	identity TEXT NOT NULL,
	role TEXT NOT NULL,
	connected_at TEXT NOT NULL,
	disconnected_at TEXT,
	status TEXT NOT NULL DEFAULT 'active'
);

CREATE INDEX IF NOT EXISTS idx_webrtc_connections_device_connected_at
	ON webrtc_connections (device_id, connected_at DESC);

CREATE TABLE IF NOT EXISTS app_settings (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	platform TEXT NOT NULL,
	key TEXT NOT NULL,
	value TEXT NOT NULL,
	updated_at TEXT NOT NULL,
	UNIQUE (platform, key)
);

CREATE TABLE IF NOT EXISTS android_telemetry (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	device_id INTEGER NOT NULL REFERENCES devices(id),
	battery_level REAL,
	signal_strength INTEGER,
	network_type TEXT,
	temperature_c REAL,
	latitude REAL,
	longitude REAL,
	cabin_noise_rms REAL,
	gps_signal_quality TEXT,
	speed_kmh REAL,
	ambient_light_lux REAL,
	server_latency_ms INTEGER,
	device_ip_address TEXT,
	jolt REAL,
	cpu_usage_percent REAL,
	ram_usage_percent REAL,
	recorded_at TEXT NOT NULL,
	received_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_android_telemetry_device_recorded_at
	ON android_telemetry (device_id, recorded_at DESC);

CREATE TABLE IF NOT EXISTS diagnostic_audio (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	device_id INTEGER NOT NULL REFERENCES devices(id),
	segment_id TEXT NOT NULL,
	path TEXT NOT NULL,
	start_ms INTEGER NOT NULL DEFAULT 0,
	end_ms INTEGER NOT NULL DEFAULT 0,
	rms_peak REAL,
	linked_alert_id TEXT,
	mode TEXT,
	received_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_diagnostic_audio_device_received_at
	ON diagnostic_audio (device_id, received_at DESC);

CREATE TABLE IF NOT EXISTS vision_analyses (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	device_id INTEGER NOT NULL REFERENCES devices(id),
	image_id INTEGER REFERENCES stored_images(id) ON DELETE SET NULL,
	model TEXT NOT NULL,
	prompt TEXT NOT NULL DEFAULT '',
	analysis TEXT NOT NULL,
	analyzed_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_vision_analyses_device_analyzed_at
	ON vision_analyses (device_id, analyzed_at DESC);
`

func migrateSchema(database *sql.DB) error {
	if _, err := database.Exec(`DROP TABLE IF EXISTS ai_model_paths`); err != nil {
		return fmt.Errorf("drop ai_model_paths: %w", err)
	}

	telemetryColumns := []struct {
		name string
		ddl  string
	}{
		{"cabin_noise_rms", "REAL"},
		{"gps_signal_quality", "TEXT"},
		{"speed_kmh", "REAL"},
		{"ambient_light_lux", "REAL"},
		{"server_latency_ms", "INTEGER"},
		{"device_ip_address", "TEXT"},
		{"jolt", "REAL"},
		{"cpu_usage_percent", "REAL"},
		{"ram_usage_percent", "REAL"},
	}
	for _, column := range telemetryColumns {
		if err := addColumnIfMissing(database, "android_telemetry", column.name, column.ddl); err != nil {
			return err
		}
	}

	if _, err := database.Exec(`
CREATE TABLE IF NOT EXISTS vision_analyses (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	device_id INTEGER NOT NULL REFERENCES devices(id),
	image_id INTEGER REFERENCES stored_images(id) ON DELETE SET NULL,
	model TEXT NOT NULL,
	prompt TEXT NOT NULL DEFAULT '',
	analysis TEXT NOT NULL,
	analyzed_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_vision_analyses_device_analyzed_at
	ON vision_analyses (device_id, analyzed_at DESC);
`); err != nil {
		return fmt.Errorf("ensure vision_analyses: %w", err)
	}

	return nil
}

func addColumnIfMissing(database *sql.DB, table, column, columnType string) error {
	rows, err := database.Query(`PRAGMA table_info(` + table + `)`)
	if err != nil {
		return fmt.Errorf("pragma table_info %s: %w", table, err)
	}
	defer rows.Close()

	for rows.Next() {
		var cid int
		var name, ctype string
		var notnull, pk int
		var dflt sql.NullString
		if err := rows.Scan(&cid, &name, &ctype, &notnull, &dflt, &pk); err != nil {
			return fmt.Errorf("scan table_info %s: %w", table, err)
		}
		if name == column {
			return nil
		}
	}
	if err := rows.Err(); err != nil {
		return fmt.Errorf("iterate table_info %s: %w", table, err)
	}

	ddl := fmt.Sprintf(`ALTER TABLE %s ADD COLUMN %s %s`, table, column, columnType)
	if _, err := database.Exec(ddl); err != nil {
		return fmt.Errorf("add column %s.%s: %w", table, column, err)
	}
	return nil
}
