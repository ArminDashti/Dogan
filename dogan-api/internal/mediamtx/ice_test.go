package mediamtx

import (
	"crypto/hmac"
	"crypto/sha1"
	"encoding/base64"
	"strconv"
	"strings"
	"testing"
	"time"
)

func TestIceServersAlwaysIncludesStun(t *testing.T) {
	servers := IceServers(IceServerOptions{}, "watchman")
	if len(servers) != 1 || len(servers[0].URLs) != 1 || servers[0].URLs[0] != "stun:stun.l.google.com:19302" {
		t.Fatalf("unexpected servers: %+v", servers)
	}
}

func TestIceServersT3ExternalTLS(t *testing.T) {
	servers := IceServers(IceServerOptions{
		TurnDomain:      "dogan-turn.xaigrok.ir",
		TurnExternalTLS: true,
		TurnSecret:      "turn-shared-secret",
		TurnTTL:         10 * time.Minute,
		ExtraURLs:       []string{"stun:stun.example.com:3478", "  "},
	}, "watchman")

	if len(servers) != 3 {
		t.Fatalf("expected stun + turn + extra, got %d entries", len(servers))
	}
	turn := servers[1]
	if turn.URLs[0] != "turns:dogan-turn.xaigrok.ir:443?transport=tcp" {
		t.Fatalf("unexpected turn url %q", turn.URLs[0])
	}
	if turn.Username == "" || turn.Credential == "" {
		t.Fatal("expected TURN REST credentials")
	}

	parts := strings.SplitN(turn.Username, ":", 2)
	if len(parts) != 2 || parts[1] != "watchman" {
		t.Fatalf("unexpected turn username %q", turn.Username)
	}
	expiry, err := strconv.ParseInt(parts[0], 10, 64)
	if err != nil {
		t.Fatalf("turn expiry is not unix time: %v", err)
	}
	now := time.Now().Unix()
	if expiry < now+9*60 || expiry > now+11*60 {
		t.Fatalf("unexpected turn expiry %d", expiry)
	}

	mac := hmac.New(sha1.New, []byte("turn-shared-secret"))
	_, _ = mac.Write([]byte(turn.Username))
	expectedCredential := base64.StdEncoding.EncodeToString(mac.Sum(nil))
	if turn.Credential != expectedCredential {
		t.Fatalf("unexpected turn credential %q", turn.Credential)
	}

	if servers[2].URLs[0] != "stun:stun.example.com:3478" {
		t.Fatalf("unexpected extra url %q", servers[2].URLs[0])
	}
}

func TestIceServersTurnWithoutExternalTLSAndSecret(t *testing.T) {
	servers := IceServers(IceServerOptions{TurnDomain: "turn.example.com"}, "watchman")
	if len(servers) != 2 {
		t.Fatalf("expected stun + turn, got %d entries", len(servers))
	}
	if servers[1].URLs[0] != "turn:turn.example.com:5349?transport=tcp" {
		t.Fatalf("unexpected turn url %q", servers[1].URLs[0])
	}
	if servers[1].Username != "" || servers[1].Credential != "" {
		t.Fatal("expected no credentials without a TURN secret")
	}
}
