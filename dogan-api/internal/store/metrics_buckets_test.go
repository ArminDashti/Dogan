package store

import (
	"path/filepath"
	"testing"
	"time"

	"github.com/dogan/dogan-server/internal/models"
)

func TestListMetricsBucketsGroupsByWindow(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "dogan.db")
	store, err := Open("file:"+filepath.ToSlash(dbPath), 0)
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	defer store.Close()

	device, err := store.UpsertDevice(models.Device{
		MACAddress: "aa:bb:cc:dd:ee:01",
		DeviceName: "bucket-phone",
	})
	if err != nil {
		t.Fatalf("UpsertDevice: %v", err)
	}

	base := time.Date(2026, 11, 26, 10, 0, 0, 0, time.UTC)
	for index := 0; index < 4; index++ {
		battery := float64(50 + index*10)
		signal := -70 - index*5
		sim2 := -90
		noise := 0.01
		jolt := 1.5
		latency := 40 + index*10
		temperature := 30.0
		record := models.DeviceMetricsRecord{
			DeviceID:        device.MACAddress,
			BatteryLevel:    &battery,
			SignalStrength:  &signal,
			Sim1SignalDbm:   &signal,
			Sim2SignalDbm:   &sim2,
			NetworkType:     "4G",
			TemperatureC:    &temperature,
			CabinNoiseRMS:   &noise,
			Jolt:            &jolt,
			ServerLatencyMs: &latency,
			RecordedAt:      base.Add(time.Duration(index) * 5 * time.Minute),
			ReceivedAt:      base.Add(time.Duration(index) * 5 * time.Minute),
		}
		if err := store.SaveMetrics(record); err != nil {
			t.Fatalf("SaveMetrics: %v", err)
		}
	}

	buckets, err := store.ListMetricsBuckets(device.MACAddress, base.Add(-time.Hour), base.Add(time.Hour), 900)
	if err != nil {
		t.Fatalf("ListMetricsBuckets: %v", err)
	}
	if len(buckets) != 2 {
		t.Fatalf("buckets=%d want 2", len(buckets))
	}
	if buckets[0].BatteryLevel == nil || *buckets[0].BatteryLevel != 60 {
		t.Fatalf("first bucket battery=%v want 60 (average of 50, 60 and 70)", buckets[0].BatteryLevel)
	}
	if buckets[0].Sim2SignalDbm == nil || *buckets[0].Sim2SignalDbm != -90 {
		t.Fatalf("first bucket sim2=%v want -90", buckets[0].Sim2SignalDbm)
	}
	if buckets[0].NetworkType != "4G" {
		t.Fatalf("first bucket network=%q want 4G", buckets[0].NetworkType)
	}
	if buckets[1].RecordedAt.Sub(buckets[0].RecordedAt) != 15*time.Minute {
		t.Fatalf("bucket spacing=%v want 15m", buckets[1].RecordedAt.Sub(buckets[0].RecordedAt))
	}
}

func TestTehranDayBoundaries(t *testing.T) {
	at := time.Date(2026, 11, 26, 22, 0, 0, 0, time.UTC)
	start := TehranStartOfDay(at)
	if got, want := start, time.Date(2026, 11, 27, 0, 0, 0, 0, time.UTC).Add(-TehranOffset); !got.Equal(want) {
		t.Fatalf("start=%v want %v", got, want)
	}
	if got, want := TehranStartOfYesterday(at), start.AddDate(0, 0, -1); !got.Equal(want) {
		t.Fatalf("yesterday=%v want %v", got, want)
	}
}
