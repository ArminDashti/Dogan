package handlers

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/dogan/dogan-server/internal/models"
	"github.com/gin-gonic/gin"
)

type stubDeviceStore struct {
	ensureDeviceID string
}

func (store *stubDeviceStore) ListDevices() ([]models.DeviceListItem, error) { return nil, nil }
func (store *stubDeviceStore) GetDeviceName(string) (string, error)          { return "", nil }
func (store *stubDeviceStore) DeleteDevice(string) error                     { return nil }
func (store *stubDeviceStore) EnsureDevice(deviceID string) error {
	store.ensureDeviceID = deviceID
	return nil
}

func TestDeviceRegisterHandler_Register(t *testing.T) {
	gin.SetMode(gin.TestMode)
	store := &stubDeviceStore{}
	handler := NewDeviceRegisterHandler(store)
	engine := gin.New()
	engine.POST("/devices/register", handler.Register)

	body, err := json.Marshal(models.DeviceRegisterRequest{DeviceID: "59402a501c1022d0"})
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	req := httptest.NewRequest(http.MethodPost, "/devices/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	engine.ServeHTTP(rec, req)

	if rec.Code != http.StatusNoContent {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusNoContent)
	}
	if store.ensureDeviceID != "59402a501c1022d0" {
		t.Fatalf("EnsureDevice id = %q", store.ensureDeviceID)
	}
}