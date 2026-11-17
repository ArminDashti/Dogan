package store

import (
	"database/sql"
	"errors"
	"path/filepath"
	"testing"

	"github.com/dogan/dogan-server/internal/models"
	_ "modernc.org/sqlite"
)

func TestOpenAppliesSQLiteSchemaAndMigrations(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "dogan.db")
	store, err := Open("file:"+filepath.ToSlash(dbPath), 0)
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	defer store.Close()

	var name string
	err = store.db.QueryRow(
		`SELECT name FROM sqlite_master WHERE type='table' AND name='vision_analyses'`,
	).Scan(&name)
	if err != nil {
		t.Fatalf("vision_analyses missing: %v", err)
	}

	device, err := store.UpsertDevice(models.Device{
		MACAddress: "aa:bb:cc:dd:ee:ff",
		DeviceName: "test-phone",
	})
	if err != nil {
		t.Fatalf("UpsertDevice: %v", err)
	}

	if err := store.DeleteDevice(device.MACAddress); err != nil {
		t.Fatalf("DeleteDevice: %v", err)
	}
	if err := store.DeleteDevice(device.MACAddress); !errors.Is(err, ErrDeviceNotFound) {
		t.Fatalf("DeleteDevice again: got %v want ErrDeviceNotFound", err)
	}
}

func TestAddColumnIfMissing(t *testing.T) {
	database, err := sql.Open("sqlite", "file:test-migrate?mode=memory&cache=shared")
	if err != nil {
		t.Fatalf("open: %v", err)
	}
	defer database.Close()

	if _, err := database.Exec(`CREATE TABLE android_telemetry (id INTEGER PRIMARY KEY, device_id INTEGER NOT NULL, recorded_at TEXT NOT NULL, received_at TEXT NOT NULL)`); err != nil {
		t.Fatalf("create: %v", err)
	}
	if err := addColumnIfMissing(database, "android_telemetry", "jolt", "REAL"); err != nil {
		t.Fatalf("add: %v", err)
	}
	if err := addColumnIfMissing(database, "android_telemetry", "jolt", "REAL"); err != nil {
		t.Fatalf("idempotent add: %v", err)
	}
}
