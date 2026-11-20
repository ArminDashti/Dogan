package frames

import (
	"encoding/base64"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"
)

func Persist(framesDir, deviceID string, capturedAt time.Time, imageData string) (string, error) {
	decodedImage, err := Decode(imageData)
	if err != nil {
		return "", err
	}
	return Write(framesDir, deviceID, capturedAt, decodedImage)
}

func Write(framesDir, deviceID string, capturedAt time.Time, imageBytes []byte) (string, error) {
	if len(imageBytes) == 0 {
		return "", fmt.Errorf("image is empty")
	}
	if err := os.MkdirAll(framesDir, 0o755); err != nil {
		return "", fmt.Errorf("create frames directory: %w", err)
	}
	fileName := fmt.Sprintf("%s-%s.jpg", sanitize(deviceID), capturedAt.UTC().Format("060102-150405"))
	framePath := filepath.Join(framesDir, fileName)
	if err := os.WriteFile(framePath, imageBytes, 0o644); err != nil {
		return "", fmt.Errorf("write frame image: %w", err)
	}
	return framePath, nil
}

func Decode(imageData string) ([]byte, error) {
	payload := strings.TrimSpace(imageData)
	if payload == "" {
		return nil, fmt.Errorf("image_data is empty")
	}
	if commaIndex := strings.Index(payload, ","); strings.HasPrefix(payload, "data:") && commaIndex != -1 {
		payload = payload[commaIndex+1:]
	}
	decodedImage, err := base64.StdEncoding.DecodeString(payload)
	if err != nil {
		return nil, fmt.Errorf("decode image_data: %w", err)
	}
	if len(decodedImage) == 0 {
		return nil, fmt.Errorf("image is empty")
	}
	return decodedImage, nil
}

func sanitize(value string) string {
	replaced := strings.Map(func(character rune) rune {
		switch character {
		case '\\', '/', ':', '*', '?', '"', '<', '>', '|', ' ':
			return '-'
		default:
			return character
		}
	}, value)
	replaced = strings.Trim(replaced, "-")
	if replaced == "" {
		return "device"
	}
	return replaced
}
