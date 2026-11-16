package handlers

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/dogan/dogan-server/internal/models"
	"github.com/gin-gonic/gin"
)

type stubWebRTCStore struct {
	lastDeviceID string
	lastLimit    int
}

func (s *stubWebRTCStore) SaveConnection(connection models.WebRTCConnectionRecord) (models.WebRTCConnectionRecord, error) {
	return connection, nil
}

func (s *stubWebRTCStore) ListConnections(deviceID string, limit int) ([]models.WebRTCConnectionRecord, error) {
	s.lastDeviceID = deviceID
	s.lastLimit = limit
	return []models.WebRTCConnectionRecord{}, nil
}

func TestListConnectionsWithoutDeviceIDReturnsOK(t *testing.T) {
	gin.SetMode(gin.TestMode)
	stub := &stubWebRTCStore{}
	handler := NewWebRTCHandler(stub)

	recorder := httptest.NewRecorder()
	context, _ := gin.CreateTestContext(recorder)
	context.Request = httptest.NewRequest(http.MethodGet, "/webrtc/connections", nil)

	handler.ListConnections(context)

	if recorder.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d body=%s", recorder.Code, recorder.Body.String())
	}
	if stub.lastDeviceID != "" {
		t.Fatalf("expected empty device id passed to store, got %q", stub.lastDeviceID)
	}

	var body struct {
		Connections []models.WebRTCConnectionRecord `json:"connections"`
	}
	if err := json.Unmarshal(recorder.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode body: %v", err)
	}
	if body.Connections == nil {
		t.Fatal("expected connections slice, got null")
	}
}
