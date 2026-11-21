package store

import (
	"encoding/base64"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// PersistFrameImage decodes a base64 JPEG payload and writes it under framesDir.
func PersistFrameImage(framesDir, deviceID string, capturedAt time.Time, imageData string) (string, error) {
	payload := strings.TrimSpace(imageData)
	if payload == "" {
		return "", fmt.Errorf("image data is empty")
	}
	if commaIndex := strings.Index(payload, ","); strings.HasPrefix(payload, "data:") && commaIndex != -1 {
		payload = payload[commaIndex+1:]
	}
	decoded, err := base64.StdEncoding.DecodeString(payload)
	if err != nil {
		return "", fmt.Errorf("decode image data: %w", err)
	}
	if len(decoded) == 0 {
		return "", fmt.Errorf("image is empty")
	}
	if err := os.MkdirAll(framesDir, 0o755); err != nil {
		return "", fmt.Errorf("create frames directory: %w", err)
	}
	fileName := fmt.Sprintf("%s-%s.jpg", sanitizeFrameDeviceID(deviceID), capturedAt.UTC().Format("060102-150405"))
	framePath := filepath.Join(framesDir, fileName)
	if err := os.WriteFile(framePath, decoded, 0o644); err != nil {
		return "", fmt.Errorf("write frame image: %w", err)
	}
	return framePath, nil
}

// DecodeImagePayload decodes base64 image bytes from Android or web payloads.
func DecodeImagePayload(imageData string) ([]byte, error) {
	payload := strings.TrimSpace(imageData)
	if payload == "" {
		return nil, fmt.Errorf("image data is empty")
	}
	if commaIndex := strings.Index(payload, ","); strings.HasPrefix(payload, "data:") && commaIndex != -1 {
		payload = payload[commaIndex+1:]
	}
	decoded, err := base64.StdEncoding.DecodeString(payload)
	if err != nil {
		return nil, fmt.Errorf("decode image data: %w", err)
	}
	if len(decoded) == 0 {
		return nil, fmt.Errorf("image is empty")
	}
	return decoded, nil
}

// SanitizeDeviceSegment makes a device id safe for file paths.
func SanitizeDeviceSegment(value string) string {
	return sanitizeFrameDeviceID(value)
}

func sanitizeFrameDeviceID(value string) string {
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