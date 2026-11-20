package cvclient

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestSaveFrameAndAnalyze(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		writer.Header().Set("Content-Type", "application/json")
		switch request.URL.Path {
		case "/frames":
			writer.WriteHeader(http.StatusCreated)
			_, _ = writer.Write([]byte(`{"path":"/data/frames/d1.jpg"}`))
		case "/analyze":
			_, _ = writer.Write([]byte(`{"analysis":"a car","model":"glm","prompt":"look"}`))
		default:
			http.NotFound(writer, request)
		}
	}))
	defer server.Close()

	client := New(server.URL)
	path, err := client.SaveFrame(context.Background(), "d1", time.Now(), "aGk=")
	if err != nil || path != "/data/frames/d1.jpg" {
		t.Fatalf("save path=%q err=%v", path, err)
	}
	analysis, err := client.Analyze(context.Background(), []byte{1, 2}, "look", "dogan-d1")
	if err != nil || analysis.Analysis != "a car" || analysis.Model != "glm" {
		t.Fatalf("analysis=%+v err=%v", analysis, err)
	}
}

func TestDisabledClient(t *testing.T) {
	client := New("")
	if client.Enabled() {
		t.Fatal("expected disabled")
	}
	if _, err := client.Analyze(context.Background(), []byte{1}, "", ""); err == nil {
		t.Fatal("expected error")
	}
}
