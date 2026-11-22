package livekit

import (
	"crypto/hmac"
	"crypto/sha1"
	"encoding/base64"
	"fmt"
	"strings"
	"time"

	"github.com/dogan/dogan-server/internal/models"
)

const DefaultTurnCredentialTTL = 24 * time.Hour

// IceServerOptions describes the coturn edge that fronts LiveKit TURN.
// Credentials use coturn's use-auth-secret REST scheme so the app never needs the shared secret.
type IceServerOptions struct {
	TurnURL       string
	TurnSecret    string
	CredentialTTL time.Duration
	ExtraURLs     []string
}

// IceServers returns relay-only ICE entries with time-limited coturn credentials.
// It returns nil when no TURN secret is configured so the field is omitted from API responses.
func IceServers(options IceServerOptions, deviceID string) []models.IceServerConfig {
	secret := strings.TrimSpace(options.TurnSecret)
	turnURL := strings.TrimSpace(options.TurnURL)
	if secret == "" || turnURL == "" {
		return nil
	}

	username, credential := TurnCredentials(secret, deviceID, options.CredentialTTL, time.Now())
	servers := []models.IceServerConfig{
		{
			URLs:       []string{turnURL},
			Username:   username,
			Credential: credential,
		},
	}

	for _, extra := range options.ExtraURLs {
		extra = strings.TrimSpace(extra)
		if extra == "" {
			continue
		}
		servers = append(servers, models.IceServerConfig{URLs: []string{extra}})
	}

	return servers
}

// TurnCredentials builds coturn REST credentials: username "<unix_expiry>:<device_id>"
// and credential base64(HMAC-SHA1(secret, username)).
func TurnCredentials(secret string, deviceID string, ttl time.Duration, now time.Time) (string, string) {
	if ttl <= 0 {
		ttl = DefaultTurnCredentialTTL
	}
	subject := strings.TrimSpace(deviceID)
	if subject == "" {
		subject = "dogan"
	}
	username := fmt.Sprintf("%d:%s", now.Add(ttl).Unix(), subject)
	mac := hmac.New(sha1.New, []byte(secret))
	_, _ = mac.Write([]byte(username))
	return username, base64.StdEncoding.EncodeToString(mac.Sum(nil))
}
