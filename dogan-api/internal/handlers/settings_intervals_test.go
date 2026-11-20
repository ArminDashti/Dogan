package handlers

import (
	"testing"
	"time"

	"github.com/dogan/dogan-server/internal/models"
)

func TestSensorIntervalDefaultsAndClamp(t *testing.T) {
	response := toWebSettingsResponse(nil, []models.AppSettingRecord{
		{Key: "gps_interval_sec", Value: "0"},
		{Key: "noise_interval_sec", Value: "5"},
	})
	if response.GpsIntervalSec != 1 {
		t.Fatalf("gps clamp = %d", response.GpsIntervalSec)
	}
	if response.NoiseIntervalSec != 5 {
		t.Fatalf("noise = %d", response.NoiseIntervalSec)
	}
	if response.LatencyIntervalSec != 1 || response.BatteryIntervalSec != 60 || response.Sim1SignalIntervalSec != 30 {
		t.Fatalf("defaults gps-family not applied: %+v", response)
	}
	if response.ImageIntervalSec != 5 {
		t.Fatalf("image default = %d", response.ImageIntervalSec)
	}

	seconds := 5
	records := sensorIntervalRecords(models.WebSettingsPayload{ImageIntervalSec: &seconds}, time.Unix(0, 0))
	var capture, policy string
	for _, record := range records {
		switch record.Key {
		case "capture_interval_ms":
			capture = record.Value
		case "watcher_image_upload_policy":
			policy = record.Value
		}
	}
	if capture != "5000" || policy != "auto" {
		t.Fatalf("image records capture=%q policy=%q", capture, policy)
	}
}
