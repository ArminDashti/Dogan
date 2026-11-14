package opencode

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestAnalyzeImageSendsVisionPayload(t *testing.T) {
	t.Parallel()

	var gotAuth, gotSession, gotPath string
	var gotBody chatRequest

	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		gotAuth = request.Header.Get("Authorization")
		gotSession = request.Header.Get("x-opencode-session")
		gotPath = request.URL.Path
		body, err := io.ReadAll(request.Body)
		if err != nil {
			t.Fatalf("read body: %v", err)
		}
		if err := json.Unmarshal(body, &gotBody); err != nil {
			t.Fatalf("decode body: %v", err)
		}
		writer.Header().Set("Content-Type", "application/json")
		_, _ = writer.Write([]byte(`{"choices":[{"message":{"content":"Blue square"}}]}`))
	}))
	defer server.Close()

	client := NewClient(server.URL, "oc_sk_test", "glm-5.3-flash", "Describe it.")
	result, err := client.AnalyzeImage(context.Background(), []byte{0xff, 0xd8, 0xff}, "image/jpeg", "", "dogan-device-1")
	if err != nil {
		t.Fatalf("AnalyzeImage: %v", err)
	}
	if result != "Blue square" {
		t.Fatalf("result = %q", result)
	}
	if gotAuth != "Bearer oc_sk_test" {
		t.Fatalf("auth = %q", gotAuth)
	}
	if gotSession != "dogan-device-1" {
		t.Fatalf("session = %q", gotSession)
	}
	if gotPath != "/chat/completions" {
		t.Fatalf("path = %q", gotPath)
	}
	if gotBody.Model != "glm-5.3-flash" {
		t.Fatalf("model = %q", gotBody.Model)
	}
	if len(gotBody.Messages) != 1 || len(gotBody.Messages[0].Content) != 2 {
		t.Fatalf("unexpected messages: %+v", gotBody.Messages)
	}
	if gotBody.Messages[0].Content[0].Text != "Describe it." {
		t.Fatalf("prompt = %q", gotBody.Messages[0].Content[0].Text)
	}
	if gotBody.Messages[0].Content[1].ImageURL == nil || !strings.HasPrefix(gotBody.Messages[0].Content[1].ImageURL.URL, "data:image/jpeg;base64,") {
		t.Fatalf("image url = %+v", gotBody.Messages[0].Content[1].ImageURL)
	}
}

func TestAnalyzeImageRequiresAPIKey(t *testing.T) {
	t.Parallel()
	client := NewClient("", "", "", "")
	if client.Enabled() {
		t.Fatal("expected disabled client")
	}
	_, err := client.AnalyzeImage(context.Background(), []byte{1}, "image/jpeg", "", "")
	if err == nil {
		t.Fatal("expected error")
	}
}
