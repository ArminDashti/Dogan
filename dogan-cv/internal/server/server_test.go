package server

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/dogan/dogan-cv/internal/pont"
)

func TestSaveFrameRejectsBadBase64(t *testing.T) {
	handler := New(t.TempDir(), pont.NewClient("", "", "", "")).Handler()
	request := httptest.NewRequest(http.MethodPost, "/frames", strings.NewReader(`{"device_id":"d1","image_data":"@@@"}`))
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	if response.Code != http.StatusBadRequest {
		t.Fatalf("status = %d body = %s", response.Code, response.Body.String())
	}
}

func TestAnalyzeRequiresAPIKey(t *testing.T) {
	handler := New(t.TempDir(), pont.NewClient("", "", "", "")).Handler()
	request := httptest.NewRequest(http.MethodPost, "/analyze", strings.NewReader(`{"image_data":"aGk="}`))
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	if response.Code != http.StatusServiceUnavailable {
		t.Fatalf("status = %d body = %s", response.Code, response.Body.String())
	}
}
