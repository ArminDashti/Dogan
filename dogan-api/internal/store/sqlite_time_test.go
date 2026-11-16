package store

import (
	"testing"
	"time"
)

func TestParseSQLiteTimeGoStringAndRFC3339(t *testing.T) {
	goString := "2026-11-16 07:09:00.989393 +0000 UTC"
	parsed, err := parseSQLiteTime(goString)
	if err != nil {
		t.Fatalf("parse go string: %v", err)
	}
	if got := parsed.UTC().Format(time.RFC3339Nano); got != "2026-11-16T07:09:00.989393Z" {
		t.Fatalf("unexpected parsed time %s", got)
	}

	rfc := formatSQLiteTime(parsed)
	roundTrip, err := parseSQLiteTime(rfc)
	if err != nil {
		t.Fatalf("parse rfc3339: %v", err)
	}
	if !roundTrip.Equal(parsed) {
		t.Fatalf("round-trip mismatch: %v vs %v", roundTrip, parsed)
	}
}
