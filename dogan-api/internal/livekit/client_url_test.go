package livekit

import "testing"

func TestClientURLWarningsProductionWss(t *testing.T) {
	if warnings := ClientURLWarnings("wss://dogan-livekit.xaigrok.ir"); len(warnings) != 0 {
		t.Fatalf("expected no warnings, got %v", warnings)
	}
}

func TestClientURLWarningsLocalhost(t *testing.T) {
	warnings := ClientURLWarnings("ws://localhost:7880")
	if len(warnings) < 2 {
		t.Fatalf("expected localhost and ws warnings, got %v", warnings)
	}
}