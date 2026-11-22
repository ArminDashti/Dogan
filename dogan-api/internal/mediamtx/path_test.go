package mediamtx

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestPathForDeviceAndDefaultIdentity(t *testing.T) {
	if path := PathForDevice("  Cam/01  "); path != "device-Cam-01" {
		t.Fatalf("unexpected path %q", path)
	}
	if path := PathForDevice("!!!"); path != "device-unknown" {
		t.Fatalf("unexpected empty-device path %q", path)
	}
	if identity := DefaultIdentity(RolePublisher, "Cam/01"); identity != "publisher-Cam-01" {
		t.Fatalf("unexpected publisher identity %q", identity)
	}
	if identity := DefaultIdentity(RoleSubscriber, ""); identity != "subscriber-unknown" {
		t.Fatalf("unexpected subscriber identity %q", identity)
	}
}

func TestControlClientListsPaths(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if request.URL.Path != "/v3/paths/list" {
			t.Errorf("unexpected request path %q", request.URL.Path)
		}
		writer.Header().Set("Content-Type", "application/json")
		fmt.Fprint(writer, `{"items":[{"name":"device-Cam-01","available":true,"ready":false,"source":{"type":"webRTCSession"},"readers":[{"id":"reader-1"}]}]}`)
	}))
	defer server.Close()

	paths, err := NewControlClient(server.URL).ListPaths()
	if err != nil {
		t.Fatalf("list paths: %v", err)
	}
	if len(paths) != 1 {
		t.Fatalf("expected one path, got %d", len(paths))
	}
	if paths[0].Name != "device-Cam-01" || !paths[0].Ready || paths[0].SourceType != "webRTCSession" || paths[0].ReadersCount != 1 {
		t.Fatalf("unexpected path info: %+v", paths[0])
	}
}

func TestControlClientReportsUnreachableServer(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		writer.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	if _, err := NewControlClient(server.URL).ListPaths(); err == nil {
		t.Fatal("expected control api error")
	}
}
