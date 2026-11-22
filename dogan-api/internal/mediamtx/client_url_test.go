package mediamtx

import "testing"

func TestClientURLWarningsProductionHTTPS(t *testing.T) {
	if warnings := ClientURLWarnings("https://dogan-media.xaigrok.ir"); len(warnings) != 0 {
		t.Fatalf("expected no warnings, got %v", warnings)
	}
}

func TestClientURLWarningsLocalAndDockerHosts(t *testing.T) {
	localhostWarnings := ClientURLWarnings("http://localhost:8889")
	if len(localhostWarnings) == 0 {
		t.Fatal("expected localhost warning")
	}

	dockerWarnings := ClientURLWarnings("http://dogan-mediamtx:8889")
	if len(dockerWarnings) < 2 {
		t.Fatalf("expected docker-internal and http warnings, got %v", dockerWarnings)
	}

	plainHTTPWarnings := ClientURLWarnings("http://media.example.com:8889")
	if len(plainHTTPWarnings) != 1 {
		t.Fatalf("expected one http warning, got %v", plainHTTPWarnings)
	}

	if warnings := ClientURLWarnings(""); len(warnings) != 1 {
		t.Fatalf("expected one empty-url warning, got %v", warnings)
	}
}
