package livekit

import (
	"crypto/hmac"
	"crypto/sha1"
	"encoding/base64"
	"strconv"
	"strings"
	"testing"
	"time"
)

const testTurnSecret = "coturn-test-secret"

func TestIceServersRequiresSecret(t *testing.T) {
	servers := IceServers(IceServerOptions{
		TurnURL: "turns:dogan-turn.xaigrok.ir:443?transport=tcp",
	}, "dogan")
	if servers != nil {
		t.Fatalf("expected no ice servers without a secret, got %d entries", len(servers))
	}
}

func TestIceServersIssuesCoturnRestCredentials(t *testing.T) {
	servers := IceServers(IceServerOptions{
		TurnURL:       "turns:dogan-turn.xaigrok.ir:443?transport=tcp",
		TurnSecret:    testTurnSecret,
		CredentialTTL: 24 * time.Hour,
		ExtraURLs:     []string{" turn:backup.example.com:5349?transport=tcp ", ""},
	}, "dogan")
	if len(servers) != 2 {
		t.Fatalf("expected turn + extra entry, got %d", len(servers))
	}
	if servers[0].URLs[0] != "turns:dogan-turn.xaigrok.ir:443?transport=tcp" {
		t.Fatalf("unexpected turn url: %v", servers[0].URLs)
	}
	if servers[1].URLs[0] != "turn:backup.example.com:5349?transport=tcp" {
		t.Fatalf("unexpected extra url: %v", servers[1].URLs)
	}

	username := servers[0].Username
	parts := strings.SplitN(username, ":", 2)
	if len(parts) != 2 {
		t.Fatalf("username must be <unix_expiry>:<device_id>, got %q", username)
	}
	expiry, err := strconv.ParseInt(parts[0], 10, 64)
	if err != nil {
		t.Fatalf("expiry is not unix seconds: %v", err)
	}
	if parts[1] != "dogan" {
		t.Fatalf("unexpected device suffix: %q", parts[1])
	}
	if expiry <= time.Now().Unix() {
		t.Fatalf("expiry must be in the future, got %d", expiry)
	}
	if expiry > time.Now().Add(48*time.Hour).Unix() {
		t.Fatalf("expiry too far in the future, got %d", expiry)
	}

	mac := hmac.New(sha1.New, []byte(testTurnSecret))
	_, _ = mac.Write([]byte(username))
	expected := base64.StdEncoding.EncodeToString(mac.Sum(nil))
	if servers[0].Credential != expected {
		t.Fatalf("credential mismatch: got %q want %q", servers[0].Credential, expected)
	}
}

func TestTurnCredentialsUsesDefaultTTL(t *testing.T) {
	now := time.Unix(1_800_000_000, 0)
	username, credential := TurnCredentials(testTurnSecret, "", 0, now)
	if credential == "" {
		t.Fatal("credential must not be empty")
	}
	expectedExpiry := now.Add(DefaultTurnCredentialTTL).Unix()
	if !strings.HasPrefix(username, strconv.FormatInt(expectedExpiry, 10)+":dogan") {
		t.Fatalf("unexpected username %q", username)
	}
}
