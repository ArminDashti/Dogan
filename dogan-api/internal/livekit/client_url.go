package livekit

import (
	"net/url"
	"strings"
)

// ClientURLWarnings reports misconfigurations that often cause browser signaling or ICE failures.
func ClientURLWarnings(clientURL string) []string {
	trimmed := strings.TrimSpace(clientURL)
	if trimmed == "" {
		return []string{"livekit client url is empty; set DOGAN_LIVEKIT_PUBLIC_URL"}
	}

	parsed, err := url.Parse(trimmed)
	if err != nil || parsed.Host == "" {
		return []string{"livekit client url is not a valid websocket url: " + trimmed}
	}

	host := strings.ToLower(parsed.Hostname())
	warnings := make([]string, 0, 3)

	if host == "localhost" || host == "127.0.0.1" || strings.HasSuffix(host, ".local") {
		warnings = append(warnings,
			"livekit client url points at "+host+"; remote browsers cannot reach it — set DOGAN_LIVEKIT_PUBLIC_URL=wss://dogan-livekit.xaigrok.ir on production")
	}

	if parsed.Scheme == "ws" {
		warnings = append(warnings,
			"livekit client url uses ws://; pages served over HTTPS require wss:// (DOGAN_LIVEKIT_PUBLIC_URL)")
	}

	if parsed.Scheme != "ws" && parsed.Scheme != "wss" {
		warnings = append(warnings, "livekit client url scheme must be ws or wss, got "+parsed.Scheme)
	}

	return warnings
}