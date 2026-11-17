package livekit

import "testing"

func TestIceServersWithT3Turn(t *testing.T) {
	servers := IceServers(IceServerOptions{
		TurnDomain:      "dogan-turn.xaigrok.ir",
		TurnExternalTLS: true,
	})
	if len(servers) != 2 {
		t.Fatalf("expected stun + turn, got %d entries", len(servers))
	}
	if servers[1].URLs[0] != "turns:dogan-turn.xaigrok.ir:443?transport=tcp" {
		t.Fatalf("unexpected turn url: %v", servers[1].URLs)
	}
}