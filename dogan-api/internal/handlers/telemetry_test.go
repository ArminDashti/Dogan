package handlers

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/dogan/dogan-server/internal/models"
	"github.com/gin-gonic/gin"
)

type stubMetricsStore struct {
	last models.DeviceMetricsRecord
}

func (s *stubMetricsStore) SaveMetrics(record models.DeviceMetricsRecord) error {
	s.last = record
	return nil
}

func (s *stubMetricsStore) GetLatestMetrics(deviceID string) (models.DeviceMetricsRecord, error) {
	return models.DeviceMetricsRecord{}, nil
}

func (s *stubMetricsStore) ListMetricsHistory(deviceID string, from, to time.Time, limit int) ([]models.DeviceMetricsRecord, error) {
	return nil, nil
}

func TestSubmitTelemetryPersistsJolt(t *testing.T) {
	gin.SetMode(gin.TestMode)
	store := &stubMetricsStore{}
	handler := NewTelemetryHandler(store, nil, nil, nil, "")

	body, err := json.Marshal(map[string]any{
		"device_id":       "device-1",
		"recorded_at":     time.Now().UTC().Format(time.RFC3339Nano),
		"cabin_noise_rms": 0.12,
		"jolt":            2.5,
	})
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	recorder := httptest.NewRecorder()
	context, _ := gin.CreateTestContext(recorder)
	context.Request = httptest.NewRequest(http.MethodPost, "/telemetry", bytes.NewReader(body))
	context.Request.Header.Set("Content-Type", "application/json")

	handler.SubmitTelemetry(context)

	if store.last.Jolt == nil || *store.last.Jolt != 2.5 {
		t.Fatalf("jolt not stored: %+v", store.last.Jolt)
	}
}