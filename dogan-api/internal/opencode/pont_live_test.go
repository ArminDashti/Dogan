package opencode

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"testing"
	"time"
)

func TestAnalyzeImageAgainstPontLive(t *testing.T) {
	apiKey := os.Getenv("OPENCODE_GO_API_KEY")
	if apiKey == "" || os.Getenv("PONT_LIVE_TEST") != "1" {
		t.Skip("set PONT_LIVE_TEST=1 and OPENCODE_GO_API_KEY to run live Pont smoke")
	}

	jpegPath := filepath.Join(t.TempDir(), "red.jpg")
	cmd := exec.Command("ffmpeg", "-y", "-f", "lavfi", "-i", "color=c=red:s=96x96:d=1", "-frames:v", "1", jpegPath)
	if out, err := cmd.CombinedOutput(); err != nil {
		t.Fatalf("ffmpeg: %v\n%s", err, out)
	}
	imageBytes, err := os.ReadFile(jpegPath)
	if err != nil {
		t.Fatalf("read jpeg: %v", err)
	}

	baseURL := os.Getenv("OPENCODE_GO_BASE_URL")
	model := os.Getenv("OPENCODE_GO_MODEL")
	client := NewClient(baseURL, apiKey, model, "What color is dominant? One word.")
	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()

	result, err := client.AnalyzeImage(ctx, imageBytes, "image/jpeg", "", "dogan-pont-live")
	if err != nil {
		t.Fatalf("AnalyzeImage: %v", err)
	}
	if result == "" {
		t.Fatal("empty analysis")
	}
	t.Logf("model=%s analysis=%q", client.Model(), result)
}
