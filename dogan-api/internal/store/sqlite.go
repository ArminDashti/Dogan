package store

import (
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/dogan/dogan-server/internal/models"
	_ "modernc.org/sqlite"
)

type Store struct {
	db              *sql.DB
	retentionPeriod time.Duration
}

func Open(databaseURL string, retentionPeriod time.Duration) (*Store, error) {
	if err := ensureSQLiteParentDir(databaseURL); err != nil {
		return nil, err
	}

	database, err := sql.Open("sqlite", databaseURL)
	if err != nil {
		return nil, fmt.Errorf("open sqlite database: %w", err)
	}

	database.SetMaxOpenConns(1)

	if err := database.Ping(); err != nil {
		_ = database.Close()
		return nil, fmt.Errorf("ping sqlite database: %w", err)
	}

	pragmas := []string{
		`PRAGMA foreign_keys = ON`,
		`PRAGMA busy_timeout = 5000`,
		`PRAGMA journal_mode = WAL`,
	}
	for _, pragma := range pragmas {
		if _, err := database.Exec(pragma); err != nil {
			_ = database.Close()
			return nil, fmt.Errorf("apply sqlite pragma: %w", err)
		}
	}

	if _, err := database.Exec(schemaDDL); err != nil {
		_ = database.Close()
		return nil, fmt.Errorf("apply sqlite schema: %w", err)
	}

	if err := migrateSchema(database); err != nil {
		_ = database.Close()
		return nil, fmt.Errorf("apply sqlite schema migrations: %w", err)
	}

	return &Store{db: database, retentionPeriod: retentionPeriod}, nil
}

func ensureSQLiteParentDir(databaseURL string) error {
	path := databaseURL
	if strings.HasPrefix(path, "file:") {
		path = strings.TrimPrefix(path, "file:")
		if query := strings.Index(path, "?"); query >= 0 {
			path = path[:query]
		}
	}
	path = strings.TrimPrefix(path, "//")
	if path == "" || path == ":memory:" || strings.HasPrefix(path, ":memory:") {
		return nil
	}
	dir := filepath.Dir(filepath.FromSlash(path))
	if dir == "." || dir == "" {
		return nil
	}
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return fmt.Errorf("create sqlite directory %s: %w", dir, err)
	}
	return nil
}

func (store *Store) Close() error {
	return store.db.Close()
}

func (store *Store) retentionCutoff() time.Time {
	return time.Now().UTC().Add(-store.retentionPeriod)
}

func (store *Store) SaveLoginLog(ip, username, browserInfo string, success bool) error {
	_, err := store.db.Exec(
		`INSERT INTO login_logs (ip, username, browser_info, attempted_at, success)
		 VALUES (?, ?, ?, ?, ?)`,
		ip,
		username,
		browserInfo,
		formatSQLiteTime(time.Now().UTC()),
		success,
	)
	if err != nil {
		return fmt.Errorf("insert login log: %w", err)
	}
	return nil
}

func (store *Store) ListLoginLogs(limit int) ([]models.LoginLogRecord, error) {
	if limit <= 0 {
		limit = 50
	}
	if limit > 200 {
		limit = 200
	}

	rows, err := store.db.Query(
		`SELECT id, ip, username, browser_info, attempted_at, success
		 FROM login_logs
		 ORDER BY attempted_at DESC, id DESC
		 LIMIT ?`,
		limit,
	)
	if err != nil {
		return nil, fmt.Errorf("list login logs: %w", err)
	}
	defer rows.Close()

	var records []models.LoginLogRecord
	for rows.Next() {
		var record models.LoginLogRecord
		var attemptedAtRaw string
		var successInt int
		if err := rows.Scan(
			&record.ID,
			&record.IP,
			&record.Username,
			&record.BrowserInfo,
			&attemptedAtRaw,
			&successInt,
		); err != nil {
			return nil, fmt.Errorf("scan login log: %w", err)
		}
		if err := scanSQLiteTime(attemptedAtRaw, &record.AttemptedAt); err != nil {
			return nil, fmt.Errorf("parse login log attempted_at: %w", err)
		}
		record.Success = successInt != 0
		records = append(records, record)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate login logs: %w", err)
	}
	if records == nil {
		records = []models.LoginLogRecord{}
	}
	return records, nil
}

func (store *Store) SaveFrame(frame models.FrameRecord) error {
	deviceRowID, err := store.resolveDeviceRowID(frame.DeviceID)
	if err != nil {
		return err
	}

	capturedAt := NormalizeCapturedAt(frame.CapturedAt)
	receivedAt := frame.ReceivedAt
	if receivedAt.IsZero() {
		receivedAt = time.Now().UTC()
	}

	_, err = store.db.Exec(
		`INSERT INTO stored_images (device_id, path, captured_at, received_at)
		 VALUES (?, ?, ?, ?)`,
		deviceRowID,
		frame.Path,
		formatSQLiteTime(capturedAt),
		formatSQLiteTime(receivedAt),
	)
	if err != nil {
		return fmt.Errorf("insert stored image: %w", err)
	}

	return nil
}

func (store *Store) GetLastFrame(deviceID string) (models.FrameRecord, error) {
	deviceRowID, err := store.resolveDeviceRowID(deviceID)
	if err != nil {
		if errors.Is(err, ErrDeviceNotFound) {
			return models.FrameRecord{}, ErrFrameNotFound
		}
		return models.FrameRecord{}, err
	}

	var frame models.FrameRecord
	cutoff := store.retentionCutoff()

	var capturedAtRaw string
	var receivedAtRaw string
	err = store.db.QueryRow(
		`SELECT id, path, captured_at, received_at
		 FROM stored_images
		 WHERE device_id = ? AND captured_at >= ?
		 ORDER BY captured_at DESC, id DESC
		 LIMIT 1`,
		deviceRowID,
		formatSQLiteTime(cutoff),
	).Scan(&frame.ID, &frame.Path, &capturedAtRaw, &receivedAtRaw)
	if err == nil {
		if scanErr := scanSQLiteTime(capturedAtRaw, &frame.CapturedAt); scanErr != nil {
			err = fmt.Errorf("scan frame captured_at: %w", scanErr)
		} else if scanErr := scanSQLiteTime(receivedAtRaw, &frame.ReceivedAt); scanErr != nil {
			err = fmt.Errorf("scan frame received_at: %w", scanErr)
		}
	}
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return models.FrameRecord{}, ErrFrameNotFound
		}
		return models.FrameRecord{}, fmt.Errorf("query last frame: %w", err)
	}

	frame.DeviceID = deviceID
	return frame, nil
}

func (store *Store) SaveMetrics(metrics models.DeviceMetricsRecord) error {
	deviceRowID, err := store.resolveDeviceRowID(metrics.DeviceID)
	if err != nil {
		return err
	}

	recordedAt := NormalizeRecordedAt(metrics.RecordedAt)
	receivedAt := metrics.ReceivedAt
	if receivedAt.IsZero() {
		receivedAt = time.Now().UTC()
	}

	_, err = store.db.Exec(
		`INSERT INTO android_telemetry (
			device_id, battery_level, signal_strength, network_type,
			temperature_c, latitude, longitude,
			cabin_noise_rms, gps_signal_quality, speed_kmh, ambient_light_lux,
			server_latency_ms, device_ip_address, jolt,
			cpu_usage_percent, ram_usage_percent,
			recorded_at, received_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		deviceRowID,
		nullableFloat(metrics.BatteryLevel),
		nullableInt(metrics.SignalStrength),
		nullIfEmpty(metrics.NetworkType),
		nullableFloat(metrics.TemperatureC),
		nullableFloat(metrics.Latitude),
		nullableFloat(metrics.Longitude),
		nullableFloat(metrics.CabinNoiseRMS),
		nullIfEmpty(metrics.GPSSignalQuality),
		nullableFloat(metrics.SpeedKmh),
		nullableFloat(metrics.AmbientLightLux),
		nullableInt(metrics.ServerLatencyMs),
		nullIfEmpty(metrics.DeviceIPAddress),
		nullableFloat(metrics.Jolt),
		nullableFloat(metrics.CPUUsagePercent),
		nullableFloat(metrics.RAMUsagePercent),
		formatSQLiteTime(recordedAt),
		formatSQLiteTime(receivedAt),
	)
	if err != nil {
		return fmt.Errorf("insert android telemetry: %w", err)
	}

	return nil
}

func (store *Store) GetLatestMetrics(deviceID string) (models.DeviceMetricsRecord, error) {
	deviceRowID, err := store.resolveDeviceRowID(deviceID)
	if err != nil {
		if errors.Is(err, ErrDeviceNotFound) {
			return models.DeviceMetricsRecord{}, ErrMetricsNotFound
		}
		return models.DeviceMetricsRecord{}, err
	}

	var metrics models.DeviceMetricsRecord
	var batteryLevel sql.NullFloat64
	var temperatureC sql.NullFloat64
	var latitude sql.NullFloat64
	var longitude sql.NullFloat64
	var signalStrength sql.NullInt64
	var networkType sql.NullString
	var cabinNoiseRMS sql.NullFloat64
	var gpsSignalQuality sql.NullString
	var speedKmh sql.NullFloat64
	var ambientLightLux sql.NullFloat64
	var serverLatencyMs sql.NullInt64
	var deviceIPAddress sql.NullString
	var jolt sql.NullFloat64
	var cpuUsagePercent sql.NullFloat64
	var ramUsagePercent sql.NullFloat64
	var recordedAtRaw string
	var receivedAtRaw string
	cutoff := store.retentionCutoff()

	err = store.db.QueryRow(
		`SELECT battery_level, signal_strength, network_type,
		        temperature_c, latitude, longitude,
		        cabin_noise_rms, gps_signal_quality, speed_kmh, ambient_light_lux,
		        server_latency_ms, device_ip_address, jolt,
		        cpu_usage_percent, ram_usage_percent,
		        recorded_at, received_at
		 FROM android_telemetry
		 WHERE device_id = ? AND recorded_at >= ?
		 ORDER BY recorded_at DESC, id DESC
		 LIMIT 1`,
		deviceRowID,
		formatSQLiteTime(cutoff),
	).Scan(
		&batteryLevel,
		&signalStrength,
		&networkType,
		&temperatureC,
		&latitude,
		&longitude,
		&cabinNoiseRMS,
		&gpsSignalQuality,
		&speedKmh,
		&ambientLightLux,
		&serverLatencyMs,
		&deviceIPAddress,
		&jolt,
		&cpuUsagePercent,
		&ramUsagePercent,
		&recordedAtRaw,
		&receivedAtRaw,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return models.DeviceMetricsRecord{}, ErrMetricsNotFound
		}
		return models.DeviceMetricsRecord{}, fmt.Errorf("query latest metrics: %w", err)
	}
	if err := scanSQLiteTime(recordedAtRaw, &metrics.RecordedAt); err != nil {
		return models.DeviceMetricsRecord{}, fmt.Errorf("scan latest recorded_at: %w", err)
	}
	if err := scanSQLiteTime(receivedAtRaw, &metrics.ReceivedAt); err != nil {
		return models.DeviceMetricsRecord{}, fmt.Errorf("scan latest received_at: %w", err)
	}

	metrics.DeviceID = deviceID
	metrics.BatteryLevel = floatPointer(batteryLevel)
	metrics.SignalStrength = intPointer(signalStrength)
	metrics.NetworkType = stringPointer(networkType)
	metrics.TemperatureC = floatPointer(temperatureC)
	metrics.Latitude = floatPointer(latitude)
	metrics.Longitude = floatPointer(longitude)
	metrics.CabinNoiseRMS = floatPointer(cabinNoiseRMS)
	metrics.GPSSignalQuality = stringValue(gpsSignalQuality)
	metrics.SpeedKmh = floatPointer(speedKmh)
	metrics.AmbientLightLux = floatPointer(ambientLightLux)
	metrics.ServerLatencyMs = intPointer(serverLatencyMs)
	metrics.DeviceIPAddress = stringValue(deviceIPAddress)
	metrics.Jolt = floatPointer(jolt)
	metrics.CPUUsagePercent = floatPointer(cpuUsagePercent)
	metrics.RAMUsagePercent = floatPointer(ramUsagePercent)

	return metrics, nil
}

func (store *Store) ListMetricsHistory(deviceID string, from, to time.Time, limit int) ([]models.DeviceMetricsRecord, error) {
	if limit <= 0 {
		limit = 50
	}
	if limit > 500 {
		limit = 500
	}

	deviceRowID, err := store.resolveDeviceRowID(deviceID)
	if err != nil {
		if errors.Is(err, ErrDeviceNotFound) {
			return nil, ErrMetricsNotFound
		}
		return nil, err
	}

	cutoff := store.retentionCutoff()
	if from.Before(cutoff) {
		from = cutoff
	}
	if to.IsZero() || to.Before(from) {
		to = time.Now().UTC().Add(time.Second)
	}

	rows, err := store.db.Query(
		`SELECT battery_level, signal_strength, network_type,
		        temperature_c, latitude, longitude,
		        cabin_noise_rms, gps_signal_quality, speed_kmh, ambient_light_lux,
		        server_latency_ms, device_ip_address, jolt,
		        cpu_usage_percent, ram_usage_percent,
		        recorded_at, received_at
		 FROM android_telemetry
		 WHERE device_id = ? AND recorded_at >= ? AND recorded_at < ?
		 ORDER BY recorded_at DESC, id DESC
		 LIMIT ?`,
		deviceRowID,
		formatSQLiteTime(from),
		formatSQLiteTime(to),
		limit,
	)
	if err != nil {
		return nil, fmt.Errorf("query metrics history: %w", err)
	}
	defer rows.Close()

	history := make([]models.DeviceMetricsRecord, 0)
	for rows.Next() {
		var metrics models.DeviceMetricsRecord
		var batteryLevel sql.NullFloat64
		var temperatureC sql.NullFloat64
		var latitude sql.NullFloat64
		var longitude sql.NullFloat64
		var signalStrength sql.NullInt64
		var networkType sql.NullString
		var cabinNoiseRMS sql.NullFloat64
		var gpsSignalQuality sql.NullString
		var speedKmh sql.NullFloat64
		var ambientLightLux sql.NullFloat64
		var serverLatencyMs sql.NullInt64
		var deviceIPAddress sql.NullString
		var jolt sql.NullFloat64
		var cpuUsagePercent sql.NullFloat64
		var ramUsagePercent sql.NullFloat64
		var recordedAtRaw string
		var receivedAtRaw string

		if err := rows.Scan(
			&batteryLevel,
			&signalStrength,
			&networkType,
			&temperatureC,
			&latitude,
			&longitude,
			&cabinNoiseRMS,
			&gpsSignalQuality,
			&speedKmh,
			&ambientLightLux,
			&serverLatencyMs,
			&deviceIPAddress,
			&jolt,
			&cpuUsagePercent,
			&ramUsagePercent,
			&recordedAtRaw,
			&receivedAtRaw,
		); err != nil {
			return nil, fmt.Errorf("scan metrics history: %w", err)
		}
		if err := scanSQLiteTime(recordedAtRaw, &metrics.RecordedAt); err != nil {
			return nil, fmt.Errorf("scan metrics recorded_at: %w", err)
		}
		if err := scanSQLiteTime(receivedAtRaw, &metrics.ReceivedAt); err != nil {
			return nil, fmt.Errorf("scan metrics received_at: %w", err)
		}

		metrics.DeviceID = deviceID
		metrics.BatteryLevel = floatPointer(batteryLevel)
		metrics.SignalStrength = intPointer(signalStrength)
		metrics.NetworkType = stringPointer(networkType)
		metrics.TemperatureC = floatPointer(temperatureC)
		metrics.Latitude = floatPointer(latitude)
		metrics.Longitude = floatPointer(longitude)
		metrics.CabinNoiseRMS = floatPointer(cabinNoiseRMS)
		metrics.GPSSignalQuality = stringValue(gpsSignalQuality)
		metrics.SpeedKmh = floatPointer(speedKmh)
		metrics.AmbientLightLux = floatPointer(ambientLightLux)
		metrics.ServerLatencyMs = intPointer(serverLatencyMs)
		metrics.DeviceIPAddress = stringValue(deviceIPAddress)
		metrics.Jolt = floatPointer(jolt)
		metrics.CPUUsagePercent = floatPointer(cpuUsagePercent)
		metrics.RAMUsagePercent = floatPointer(ramUsagePercent)
		history = append(history, metrics)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate metrics history: %w", err)
	}
	if len(history) == 0 {
		return nil, ErrMetricsNotFound
	}

	return history, nil
}

func (store *Store) ListFrames(limit int) ([]models.FrameRecord, error) {
	if limit <= 0 {
		limit = 50
	}
	if limit > 200 {
		limit = 200
	}

	cutoff := store.retentionCutoff()
	rows, err := store.db.Query(
		`SELECT si.id, d.mac_address, si.path, si.captured_at, si.received_at
		 FROM stored_images si
		 JOIN devices d ON d.id = si.device_id
		 WHERE si.captured_at >= ?
		 ORDER BY si.captured_at DESC, si.id DESC
		 LIMIT ?`,
		formatSQLiteTime(cutoff),
		limit,
	)
	if err != nil {
		return nil, fmt.Errorf("list frames: %w", err)
	}
	defer rows.Close()

	frames := make([]models.FrameRecord, 0)
	for rows.Next() {
		var frame models.FrameRecord
		var capturedAtRaw string
		var receivedAtRaw string
		if err := rows.Scan(&frame.ID, &frame.DeviceID, &frame.Path, &capturedAtRaw, &receivedAtRaw); err != nil {
			return nil, fmt.Errorf("scan frame: %w", err)
		}
		if err := scanSQLiteTime(capturedAtRaw, &frame.CapturedAt); err != nil {
			return nil, fmt.Errorf("scan frame captured_at: %w", err)
		}
		if err := scanSQLiteTime(receivedAtRaw, &frame.ReceivedAt); err != nil {
			return nil, fmt.Errorf("scan frame received_at: %w", err)
		}
		frames = append(frames, frame)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate frames: %w", err)
	}
	return frames, nil
}

func (store *Store) GetFrameByID(imageID int64) (models.FrameRecord, error) {
	var frame models.FrameRecord
	var capturedAtRaw string
	var receivedAtRaw string
	cutoff := store.retentionCutoff()
	err := store.db.QueryRow(
		`SELECT si.id, d.mac_address, si.path, si.captured_at, si.received_at
		 FROM stored_images si
		 JOIN devices d ON d.id = si.device_id
		 WHERE si.id = ? AND si.captured_at >= ?`,
		imageID,
		formatSQLiteTime(cutoff),
	).Scan(&frame.ID, &frame.DeviceID, &frame.Path, &capturedAtRaw, &receivedAtRaw)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return models.FrameRecord{}, ErrFrameNotFound
		}
		return models.FrameRecord{}, fmt.Errorf("query frame by id: %w", err)
	}
	if err := scanSQLiteTime(capturedAtRaw, &frame.CapturedAt); err != nil {
		return models.FrameRecord{}, fmt.Errorf("scan frame captured_at: %w", err)
	}
	if err := scanSQLiteTime(receivedAtRaw, &frame.ReceivedAt); err != nil {
		return models.FrameRecord{}, fmt.Errorf("scan frame received_at: %w", err)
	}
	return frame, nil
}

func (store *Store) GetDeviceName(deviceID string) (string, error) {
	var name string
	err := store.db.QueryRow(
		`SELECT device_name FROM devices
		 WHERE mac_address = ? OR device_name = ? OR CAST(id AS TEXT) = ?
		 LIMIT 1`,
		deviceID,
	).Scan(&name)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return "", ErrDeviceNotFound
		}
		return "", fmt.Errorf("query device name: %w", err)
	}
	return name, nil
}

func (store *Store) SaveDiagnosticAudio(deviceID, segmentID, path string, startMs, endMs int64, rmsPeak float64, linkedAlertID, mode string) error {
	deviceRowID, err := store.resolveDeviceRowID(deviceID)
	if err != nil {
		return err
	}

	_, err = store.db.Exec(
		`INSERT INTO diagnostic_audio (
			device_id, segment_id, path, start_ms, end_ms, rms_peak, linked_alert_id, mode, received_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		deviceRowID,
		segmentID,
		path,
		startMs,
		endMs,
		rmsPeak,
		nullIfEmpty(linkedAlertID),
		nullIfEmpty(mode),
		formatSQLiteTime(time.Now().UTC()),
	)
	if err != nil {
		return fmt.Errorf("insert diagnostic audio: %w", err)
	}
	return nil
}

func stringValue(value sql.NullString) string {
	if value.Valid {
		return value.String
	}
	return ""
}

func (store *Store) CreateAction(action models.PhoneActionRecord) (models.PhoneActionRecord, error) {
	deviceRowID, err := store.resolveDeviceRowID(action.DeviceID)
	if err != nil {
		return models.PhoneActionRecord{}, err
	}

	payloadJSON, err := json.Marshal(action.Payload)
	if err != nil {
		return models.PhoneActionRecord{}, fmt.Errorf("marshal action payload: %w", err)
	}

	sentAt := action.SentAt
	if sentAt.IsZero() {
		sentAt = time.Now().UTC()
	}

	status := action.Status
	if status == "" {
		status = "pending"
	}

	err = store.db.QueryRow(
		`INSERT INTO phone_actions (device_id, action_type, payload, sent_at, status)
		 VALUES (?, ?, ?, ?, ?)
		 RETURNING id`,
		deviceRowID,
		action.ActionType,
		payloadJSON,
		formatSQLiteTime(sentAt),
		status,
	).Scan(&action.ID)
	if err != nil {
		return models.PhoneActionRecord{}, fmt.Errorf("insert phone action: %w", err)
	}

	action.SentAt = sentAt
	action.Status = status
	return action, nil
}

func (store *Store) GetPendingActions(deviceID string) ([]models.PhoneActionRecord, error) {
	deviceRowID, err := store.resolveDeviceRowID(deviceID)
	if err != nil {
		if errors.Is(err, ErrDeviceNotFound) {
			return []models.PhoneActionRecord{}, nil
		}
		return nil, err
	}

	rows, err := store.db.Query(
		`SELECT id, action_type, payload, sent_at, status
		 FROM phone_actions
		 WHERE device_id = ? AND status = 'pending'
		 ORDER BY sent_at ASC, id ASC`,
		deviceRowID,
	)
	if err != nil {
		return nil, fmt.Errorf("query pending actions: %w", err)
	}
	defer rows.Close()

	actions := make([]models.PhoneActionRecord, 0)
	for rows.Next() {
		var action models.PhoneActionRecord
		var payloadJSON []byte
		var sentAtRaw string
		if err := rows.Scan(&action.ID, &action.ActionType, &payloadJSON, &sentAtRaw, &action.Status); err != nil {
			return nil, fmt.Errorf("scan pending action: %w", err)
		}
		if err := scanSQLiteTime(sentAtRaw, &action.SentAt); err != nil {
			return nil, fmt.Errorf("scan pending action sent_at: %w", err)
		}
		action.DeviceID = deviceID
		if len(payloadJSON) > 0 {
			_ = json.Unmarshal(payloadJSON, &action.Payload)
		}
		if action.Payload == nil {
			action.Payload = map[string]any{}
		}
		actions = append(actions, action)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate pending actions: %w", err)
	}

	return actions, nil
}

func (store *Store) AcknowledgeAction(actionID int64, status string) error {
	if status == "" {
		status = "done"
	}

	result, err := store.db.Exec(
		`UPDATE phone_actions SET status = ? WHERE id = ?`,
		status,
		actionID,
	)
	if err != nil {
		return fmt.Errorf("acknowledge action: %w", err)
	}

	rowsAffected, err := result.RowsAffected()
	if err != nil {
		return fmt.Errorf("acknowledge action rows affected: %w", err)
	}
	if rowsAffected == 0 {
		return ErrActionNotFound
	}

	return nil
}

func (store *Store) UpsertSetting(setting models.AppSettingRecord) error {
	updatedAt := setting.UpdatedAt
	if updatedAt.IsZero() {
		updatedAt = time.Now().UTC()
	}

	_, err := store.db.Exec(
		`INSERT INTO app_settings (platform, key, value, updated_at)
		 VALUES (?, ?, ?, ?)
		 ON CONFLICT (platform, key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at`,
		setting.Platform,
		setting.Key,
		setting.Value,
		formatSQLiteTime(updatedAt),
	)
	if err != nil {
		return fmt.Errorf("upsert app setting: %w", err)
	}

	return nil
}

func (store *Store) GetSettings(platform string) ([]models.AppSettingRecord, error) {
	rows, err := store.db.Query(
		`SELECT platform, key, value, updated_at
		 FROM app_settings
		 WHERE platform = ?
		 ORDER BY key`,
		platform,
	)
	if err != nil {
		return nil, fmt.Errorf("query app settings: %w", err)
	}
	defer rows.Close()

	settings := make([]models.AppSettingRecord, 0)
	for rows.Next() {
		var setting models.AppSettingRecord
		var updatedAtRaw string
		if err := rows.Scan(&setting.Platform, &setting.Key, &setting.Value, &updatedAtRaw); err != nil {
			return nil, fmt.Errorf("scan app setting: %w", err)
		}
		if err := scanSQLiteTime(updatedAtRaw, &setting.UpdatedAt); err != nil {
			return nil, fmt.Errorf("scan app setting updated_at: %w", err)
		}
		settings = append(settings, setting)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate app settings: %w", err)
	}

	return settings, nil
}

func (store *Store) SaveConnection(connection models.WebRTCConnectionRecord) (models.WebRTCConnectionRecord, error) {
	deviceRowID, err := store.resolveDeviceRowID(connection.DeviceID)
	if err != nil {
		return models.WebRTCConnectionRecord{}, err
	}

	connectedAt := connection.ConnectedAt
	if connectedAt.IsZero() {
		connectedAt = time.Now().UTC()
	}

	status := connection.Status
	if status == "" {
		status = "active"
	}

	var disconnectedAt any
	if connection.DisconnectedAt != nil {
		disconnectedAt = formatSQLiteTime(*connection.DisconnectedAt)
	}

	err = store.db.QueryRow(
		`INSERT INTO webrtc_connections (device_id, room, identity, role, connected_at, disconnected_at, status)
		 VALUES (?, ?, ?, ?, ?, ?, ?)
		 RETURNING id`,
		deviceRowID,
		connection.Room,
		connection.Identity,
		connection.Role,
		formatSQLiteTime(connectedAt),
		disconnectedAt,
		status,
	).Scan(&connection.ID)
	if err != nil {
		return models.WebRTCConnectionRecord{}, fmt.Errorf("insert webrtc connection: %w", err)
	}

	connection.ConnectedAt = connectedAt
	connection.Status = status
	return connection, nil
}

func (store *Store) ListConnections(deviceID string, limit int) ([]models.WebRTCConnectionRecord, error) {
	if limit <= 0 {
		limit = 50
	}

	if strings.TrimSpace(deviceID) == "" {
		rows, err := store.db.Query(
			`SELECT w.id, d.mac_address, w.room, w.identity, w.role, w.connected_at, w.disconnected_at, w.status
			 FROM webrtc_connections w
			 JOIN devices d ON d.id = w.device_id
			 ORDER BY w.connected_at DESC, w.id DESC
			 LIMIT ?`,
			limit,
		)
		if err != nil {
			return nil, fmt.Errorf("list webrtc connections: %w", err)
		}
		defer rows.Close()
		return scanWebRTCConnections(rows)
	}

	deviceRowID, err := store.resolveDeviceRowID(deviceID)
	if err != nil {
		if errors.Is(err, ErrDeviceNotFound) {
			return []models.WebRTCConnectionRecord{}, nil
		}
		return nil, err
	}

	rows, err := store.db.Query(
		`SELECT w.id, d.mac_address, w.room, w.identity, w.role, w.connected_at, w.disconnected_at, w.status
		 FROM webrtc_connections w
		 JOIN devices d ON d.id = w.device_id
		 WHERE w.device_id = ?
		 ORDER BY w.connected_at DESC, w.id DESC
		 LIMIT ?`,
		deviceRowID,
		limit,
	)
	if err != nil {
		return nil, fmt.Errorf("list webrtc connections: %w", err)
	}
	defer rows.Close()
	return scanWebRTCConnections(rows)
}

func (store *Store) PurgeExpiredFrames(cutoff time.Time) ([]string, error) {
	rows, err := store.db.Query(
		`SELECT path FROM stored_images WHERE captured_at < ?`,
		formatSQLiteTime(cutoff.UTC()),
	)
	if err != nil {
		return nil, fmt.Errorf("query expired frames: %w", err)
	}
	defer rows.Close()

	framePaths := make([]string, 0)
	for rows.Next() {
		var framePath string
		if err := rows.Scan(&framePath); err != nil {
			return nil, fmt.Errorf("scan expired frame path: %w", err)
		}
		framePaths = append(framePaths, framePath)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate expired frames: %w", err)
	}

	if _, err := store.db.Exec(`DELETE FROM stored_images WHERE captured_at < ?`, formatSQLiteTime(cutoff.UTC())); err != nil {
		return nil, fmt.Errorf("delete expired frames: %w", err)
	}

	return framePaths, nil
}

func (store *Store) PurgeExpiredMetrics(cutoff time.Time) error {
	if _, err := store.db.Exec(`DELETE FROM android_telemetry WHERE recorded_at < ?`, formatSQLiteTime(cutoff.UTC())); err != nil {
		return fmt.Errorf("delete expired metrics: %w", err)
	}
	return nil
}


func (store *Store) SaveVisionAnalysis(record models.VisionAnalysisRecord) (models.VisionAnalysisRecord, error) {
	deviceRowID, err := store.resolveDeviceRowID(record.DeviceID)
	if err != nil {
		return models.VisionAnalysisRecord{}, err
	}

	analyzedAt := record.AnalyzedAt
	if analyzedAt.IsZero() {
		analyzedAt = time.Now().UTC()
	}

	var analyzedAtRaw string
	err = store.db.QueryRow(
		`INSERT INTO vision_analyses (device_id, image_id, model, prompt, analysis, analyzed_at)
		 VALUES (?, ?, ?, ?, ?, ?)
		 RETURNING id, analyzed_at`,
		deviceRowID,
		record.ImageID,
		record.Model,
		record.Prompt,
		record.Analysis,
		formatSQLiteTime(analyzedAt),
	).Scan(&record.ID, &analyzedAtRaw)
	if err != nil {
		return models.VisionAnalysisRecord{}, fmt.Errorf("insert vision analysis: %w", err)
	}
	if err := scanSQLiteTime(analyzedAtRaw, &record.AnalyzedAt); err != nil {
		return models.VisionAnalysisRecord{}, fmt.Errorf("scan vision analyzed_at: %w", err)
	}
	return record, nil
}

func (store *Store) GetLatestVisionAnalysis(deviceID string) (models.VisionAnalysisRecord, error) {
	deviceRowID, err := store.resolveDeviceRowID(deviceID)
	if err != nil {
		if errors.Is(err, ErrDeviceNotFound) {
			return models.VisionAnalysisRecord{}, ErrVisionAnalysisNotFound
		}
		return models.VisionAnalysisRecord{}, err
	}

	var record models.VisionAnalysisRecord
	var imageID sql.NullInt64
	var analyzedAtRaw string
	err = store.db.QueryRow(
		`SELECT id, image_id, model, prompt, analysis, analyzed_at
		 FROM vision_analyses
		 WHERE device_id = ?
		 ORDER BY analyzed_at DESC, id DESC
		 LIMIT 1`,
		deviceRowID,
	).Scan(&record.ID, &imageID, &record.Model, &record.Prompt, &record.Analysis, &analyzedAtRaw)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return models.VisionAnalysisRecord{}, ErrVisionAnalysisNotFound
		}
		return models.VisionAnalysisRecord{}, fmt.Errorf("query vision analysis: %w", err)
	}
	if err := scanSQLiteTime(analyzedAtRaw, &record.AnalyzedAt); err != nil {
		return models.VisionAnalysisRecord{}, fmt.Errorf("scan vision analyzed_at: %w", err)
	}
	record.DeviceID = deviceID
	if imageID.Valid {
		value := imageID.Int64
		record.ImageID = &value
	}
	return record, nil
}

func (store *Store) UpsertDevice(device models.Device) (models.Device, error) {
	if device.MACAddress == "" {
		return models.Device{}, fmt.Errorf("mac_address is required")
	}
	if device.DeviceName == "" {
		device.DeviceName = device.MACAddress
	}

	err := store.db.QueryRow(
		`INSERT INTO devices (device_name, mac_address)
		 VALUES (?, ?)
		 ON CONFLICT (mac_address) DO UPDATE SET device_name = EXCLUDED.device_name
		 RETURNING id, device_name, mac_address`,
		device.DeviceName,
		device.MACAddress,
	).Scan(&device.ID, &device.DeviceName, &device.MACAddress)
	if err != nil {
		return models.Device{}, fmt.Errorf("upsert device: %w", err)
	}

	return device, nil
}

func (store *Store) GetDeviceByMACAddress(macAddress string) (models.Device, error) {
	var device models.Device
	err := store.db.QueryRow(
		`SELECT id, device_name, mac_address FROM devices WHERE mac_address = ?`,
		macAddress,
	).Scan(&device.ID, &device.DeviceName, &device.MACAddress)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return models.Device{}, ErrDeviceNotFound
		}
		return models.Device{}, fmt.Errorf("query device: %w", err)
	}

	return device, nil
}

func (store *Store) lookupDeviceRowID(deviceIdentifier string) (int64, string, error) {
	var deviceRowID int64
	var macAddress string
	err := store.db.QueryRow(
		`SELECT id, mac_address FROM devices
		 WHERE mac_address = ? OR device_name = ? OR CAST(id AS TEXT) = ?
		 LIMIT 1`,
		deviceIdentifier,
		deviceIdentifier,
		deviceIdentifier,
	).Scan(&deviceRowID, &macAddress)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return 0, "", ErrDeviceNotFound
		}
		return 0, "", fmt.Errorf("lookup device: %w", err)
	}
	return deviceRowID, macAddress, nil
}

func (store *Store) DeleteDevice(deviceIdentifier string) error {
	deviceRowID, macAddress, err := store.lookupDeviceRowID(deviceIdentifier)
	if err != nil {
		return err
	}

	tx, err := store.db.Begin()
	if err != nil {
		return fmt.Errorf("begin delete device tx: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	deleteSteps := []struct {
		label string
		query string
	}{
		{"vision_analyses", `DELETE FROM vision_analyses WHERE device_id = ?`},
		{"stored_images", `DELETE FROM stored_images WHERE device_id = ?`},
		{"phone_actions", `DELETE FROM phone_actions WHERE device_id = ?`},
		{"webrtc_connections", `DELETE FROM webrtc_connections WHERE device_id = ?`},
		{"android_telemetry", `DELETE FROM android_telemetry WHERE device_id = ?`},
		{"diagnostic_audio", `DELETE FROM diagnostic_audio WHERE device_id = ?`},
		{"devices", `DELETE FROM devices WHERE id = ?`},
	}
	for _, step := range deleteSteps {
		if _, err := tx.Exec(step.query, deviceRowID); err != nil {
			return fmt.Errorf("delete %s for device: %w", step.label, err)
		}
	}

	for _, value := range []string{deviceIdentifier, macAddress, fmt.Sprintf("%d", deviceRowID)} {
		if _, err := tx.Exec(
			`DELETE FROM app_settings WHERE platform = 'web' AND key = 'default_device_id' AND value = ?`,
			value,
		); err != nil {
			return fmt.Errorf("clear default_device_id: %w", err)
		}
	}

	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit delete device: %w", err)
	}
	return nil
}

func (store *Store) resolveDeviceRowID(deviceIdentifier string) (int64, error) {
	var deviceRowID int64
	err := store.db.QueryRow(
		`SELECT id FROM devices
		 WHERE mac_address = ? OR device_name = ? OR CAST(id AS TEXT) = ?
		 LIMIT 1`,
		deviceIdentifier,
		deviceIdentifier,
		deviceIdentifier,
	).Scan(&deviceRowID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			insertedDevice, upsertErr := store.UpsertDevice(models.Device{
				DeviceName: deviceIdentifier,
				MACAddress: deviceIdentifier,
			})
			if upsertErr != nil {
				return 0, upsertErr
			}
			return insertedDevice.ID, nil
		}
		return 0, fmt.Errorf("resolve device: %w", err)
	}

	return deviceRowID, nil
}

func (store *Store) ListDevices() ([]models.DeviceListItem, error) {
	onlineCutoff := time.Now().UTC().Add(-5 * time.Minute)
	rows, err := store.db.Query(
		`SELECT d.mac_address, d.device_name,
		        CASE
		            WHEN (
		                (SELECT t.recorded_at FROM android_telemetry t
		                 WHERE t.device_id = d.id
		                 ORDER BY t.recorded_at DESC LIMIT 1) >= ?
		                OR
		                (SELECT w.connected_at FROM webrtc_connections w
		                 WHERE w.device_id = d.id
		                   AND w.role = 'publisher'
		                   AND w.status = 'active'
		                 ORDER BY w.connected_at DESC LIMIT 1) >= ?
		            )
		            THEN 'online'
		            ELSE 'offline'
		        END AS status
		 FROM devices d
		 ORDER BY d.device_name ASC, d.mac_address ASC`,
		formatSQLiteTime(onlineCutoff),
		formatSQLiteTime(onlineCutoff),
	)
	if err != nil {
		return nil, fmt.Errorf("list devices: %w", err)
	}
	defer rows.Close()

	devices := make([]models.DeviceListItem, 0)
	for rows.Next() {
		var device models.DeviceListItem
		if err := rows.Scan(&device.ID, &device.Name, &device.Status); err != nil {
			return nil, fmt.Errorf("scan device: %w", err)
		}
		devices = append(devices, device)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate devices: %w", err)
	}

	return devices, nil
}

func nullableFloat(value *float64) any {
	if value == nil {
		return nil
	}
	return *value
}

func nullableInt(value *int) any {
	if value == nil {
		return nil
	}
	return *value
}

func nullIfEmpty(value string) any {
	if value == "" {
		return nil
	}
	return value
}

func floatPointer(value sql.NullFloat64) *float64 {
	if !value.Valid {
		return nil
	}
	result := value.Float64
	return &result
}

func intPointer(value sql.NullInt64) *int {
	if !value.Valid {
		return nil
	}
	result := int(value.Int64)
	return &result
}

func stringPointer(value sql.NullString) string {
	if !value.Valid {
		return ""
	}
	return value.String
}
