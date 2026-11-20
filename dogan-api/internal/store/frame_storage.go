package store

import (
	"encoding/base64"
	"fmt"
	"strings"
)

// DecodeImagePayload decodes raw or data-URL base64 image payloads.
func DecodeImagePayload(imageData string) ([]byte, error) {
	return decodeImagePayload(imageData)
}

func decodeImagePayload(imageData string) ([]byte, error) {
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

	return decodedImage, nil
}

func sanitizePathSegment(value string) string {
	replaced := strings.Map(func(character rune) rune {
		switch character {
		case '\\', '/', ':', '*', '?', '"', '<', '>', '|', ' ':
			return '-'
		default:
			return character
		}
	}, value)

	return strings.Trim(replaced, "-")
}

// SanitizeDeviceSegment is the exported form of sanitizePathSegment for handlers.
func SanitizeDeviceSegment(value string) string {
	return sanitizePathSegment(value)
}
