package mediamtx

import (
	"errors"
	"strings"
	"unicode"
)

const (
	RolePublisher  = "publisher"
	RoleSubscriber = "subscriber"
)

var (
	ErrInvalidRole   = errors.New("role must be publisher or subscriber")
	ErrNotConfigured = errors.New("streaming is not configured")
)

func PathForDevice(deviceID string) string {
	sanitized := sanitizeIdentifier(deviceID)
	if sanitized == "" {
		return "device-unknown"
	}

	return "device-" + sanitized
}

func DefaultIdentity(role, deviceID string) string {
	sanitizedDeviceID := sanitizeIdentifier(deviceID)
	if sanitizedDeviceID == "" {
		sanitizedDeviceID = "unknown"
	}

	if role == RolePublisher {
		return "publisher-" + sanitizedDeviceID
	}

	return "subscriber-" + sanitizedDeviceID
}

func sanitizeIdentifier(value string) string {
	var builder strings.Builder
	builder.Grow(len(value))

	for _, character := range strings.TrimSpace(value) {
		if unicode.IsLetter(character) || unicode.IsDigit(character) || character == '-' || character == '_' {
			builder.WriteRune(character)
			continue
		}

		builder.WriteRune('-')
	}

	return strings.Trim(builder.String(), "-")
}
