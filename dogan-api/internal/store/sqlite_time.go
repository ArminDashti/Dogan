package store

import (
	"database/sql"
	"fmt"
	"strings"
	"time"
)

// formatSQLiteTime stores UTC timestamps in RFC3339Nano so they round-trip
// through modernc.org/sqlite. Go's default time.Time Text.Value string
// ("2006-01-02 15:04:05.999999999 +0000 UTC") cannot be scanned back into time.Time.
func formatSQLiteTime(value time.Time) string {
	return value.UTC().Format(time.RFC3339Nano)
}

func parseSQLiteTime(raw string) (time.Time, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return time.Time{}, nil
	}

	layouts := []string{
		time.RFC3339Nano,
		time.RFC3339,
		"2006-01-02 15:04:05.999999999 -0700 MST",
		"2006-01-02 15:04:05.999999999 -0700 UTC",
		"2006-01-02 15:04:05.999999 -0700 MST",
		"2006-01-02 15:04:05.999999 -0700 UTC",
		"2006-01-02 15:04:05 -0700 MST",
		"2006-01-02 15:04:05 -0700 UTC",
		"2006-01-02 15:04:05",
	}
	var lastErr error
	for _, layout := range layouts {
		parsed, err := time.Parse(layout, raw)
		if err == nil {
			return parsed.UTC(), nil
		}
		lastErr = err
	}
	return time.Time{}, fmt.Errorf("parse sqlite time %q: %w", raw, lastErr)
}

func scanSQLiteTime(raw string, dest *time.Time) error {
	parsed, err := parseSQLiteTime(raw)
	if err != nil {
		return err
	}
	*dest = parsed
	return nil
}

func scanNullableSQLiteTime(raw sql.NullString) (*time.Time, error) {
	if !raw.Valid || strings.TrimSpace(raw.String) == "" {
		return nil, nil
	}
	parsed, err := parseSQLiteTime(raw.String)
	if err != nil {
		return nil, err
	}
	return &parsed, nil
}

// rewriteLegacySQLiteTimestamps converts Go time.String() values already on disk
// into RFC3339Nano so lexical range filters and modernc scans stay consistent.
func rewriteLegacySQLiteTimestamps(database *sql.DB) error {
	targets := []struct {
		table  string
		column string
	}{
		{"android_telemetry", "recorded_at"},
		{"android_telemetry", "received_at"},
		{"webrtc_connections", "connected_at"},
		{"webrtc_connections", "disconnected_at"},
		{"stored_images", "captured_at"},
		{"stored_images", "received_at"},
		{"login_logs", "attempted_at"},
		{"phone_actions", "sent_at"},
		{"app_settings", "updated_at"},
		{"diagnostic_audio", "received_at"},
		{"vision_analyses", "analyzed_at"},
	}

	for _, target := range targets {
		query := fmt.Sprintf(
			`SELECT rowid, %s FROM %s WHERE %s LIKE '%% UTC' OR %s LIKE '%% MST'`,
			target.column,
			target.table,
			target.column,
			target.column,
		)
		rows, err := database.Query(query)
		if err != nil {
			return fmt.Errorf("query legacy timestamps %s.%s: %w", target.table, target.column, err)
		}

		type pending struct {
			rowID int64
			value string
		}
		pendingRows := make([]pending, 0)
		for rows.Next() {
			var rowID int64
			var raw sql.NullString
			if err := rows.Scan(&rowID, &raw); err != nil {
				rows.Close()
				return fmt.Errorf("scan legacy timestamp %s.%s: %w", target.table, target.column, err)
			}
			if !raw.Valid || strings.TrimSpace(raw.String) == "" {
				continue
			}
			parsed, err := parseSQLiteTime(raw.String)
			if err != nil {
				rows.Close()
				return fmt.Errorf("parse legacy timestamp %s.%s row %d: %w", target.table, target.column, rowID, err)
			}
			pendingRows = append(pendingRows, pending{rowID: rowID, value: formatSQLiteTime(parsed)})
		}
		if err := rows.Err(); err != nil {
			rows.Close()
			return fmt.Errorf("iterate legacy timestamps %s.%s: %w", target.table, target.column, err)
		}
		rows.Close()

		update := fmt.Sprintf(`UPDATE %s SET %s = ? WHERE rowid = ?`, target.table, target.column)
		for _, row := range pendingRows {
			if _, err := database.Exec(update, row.value, row.rowID); err != nil {
				return fmt.Errorf("rewrite legacy timestamp %s.%s row %d: %w", target.table, target.column, row.rowID, err)
			}
		}
	}

	return nil
}
