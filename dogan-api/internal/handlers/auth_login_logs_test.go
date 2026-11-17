package handlers

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/dogan/dogan-server/internal/models"
	"github.com/gin-gonic/gin"
)

type stubLoginLogStore struct {
	lastLimit int
}

func (s *stubLoginLogStore) SaveLoginLog(ip, username, browserInfo string, success bool) error {
	return nil
}

func (s *stubLoginLogStore) ListLoginLogs(limit int) ([]models.LoginLogRecord, error) {
	s.lastLimit = limit
	return []models.LoginLogRecord{}, nil
}

func TestListLoginLogsReturnsOK(t *testing.T) {
	gin.SetMode(gin.TestMode)
	stub := &stubLoginLogStore{}
	handler := &AuthHandler{loginLogStore: stub}

	recorder := httptest.NewRecorder()
	context, _ := gin.CreateTestContext(recorder)
	context.Request = httptest.NewRequest(http.MethodGet, "/auth/login-logs?limit=25", nil)

	handler.ListLoginLogs(context)

	if recorder.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d body=%s", recorder.Code, recorder.Body.String())
	}
	if stub.lastLimit != 25 {
		t.Fatalf("expected limit 25, got %d", stub.lastLimit)
	}

	var body struct {
		LoginLogs []models.LoginLogRecord `json:"login_logs"`
	}
	if err := json.Unmarshal(recorder.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode body: %v", err)
	}
	if body.LoginLogs == nil {
		t.Fatal("expected login_logs slice, got null")
	}
}