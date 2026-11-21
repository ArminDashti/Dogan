package store

import (
	"encoding/base64"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestPersistFrameImageRoundTrip(t *testing.T) {
	dir := t.TempDir()
	jpeg := []byte{0xff, 0xd8, 0xff, 0xd9}
	encoded := base64.StdEncoding.EncodeToString(jpeg)
	path, err := PersistFrameImage(dir, "unit-test", time.Unix(0, 0).UTC(), encoded)
	if err != nil {
		t.Fatalf("persist: %v", err)
	}
	if !filepath.IsAbs(path) {
		path = filepath.Join(dir, filepath.Base(path))
	}
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read: %v", err)
	}
	if string(data) != string(jpeg) {
		t.Fatal("jpeg bytes mismatch")
	}
}