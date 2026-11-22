package mediamtx

import (
	"net/url"
	"strings"
)

func ClientURLWarnings(publicURL string) []string {
	trimmed := strings.TrimSpace(publicURL)
	if trimmed == "" {
		return []string{"streaming public url is empty; set DOGAN_MEDIAMTX_WEBRTC_PUBLIC_URL / DOGAN_MEDIAMTX_HLS_PUBLIC_URL"}
	}

	parsed, err := url.Parse(trimmed)
	if err != nil || parsed.Host == "" || parsed.Scheme == "" {
		return []string{"streaming public url is not a valid http url: " + trimmed}
	}

	host := strings.ToLower(parsed.Hostname())
	warnings := make([]string, 0, 3)

	if host == "localhost" || host == "127.0.0.1" || strings.HasSuffix(host, ".local") {
		warnings = append(warnings,
			"streaming public url points at "+host+"; remote clients cannot reach it — set DOGAN_MEDIAMTX_WEBRTC_PUBLIC_URL / DOGAN_MEDIAMTX_HLS_PUBLIC_URL to the public https:// hostnames")
	}

	if isDockerInternalHost(host) {
		warnings = append(warnings,
			"streaming public url uses docker-internal hostname "+host+"; clients outside the stack cannot resolve it")
	}

	if parsed.Scheme == "http" && !isLocalHost(host) {
		warnings = append(warnings,
			"streaming public url uses http://; pages served over HTTPS require https:// (DOGAN_MEDIAMTX_WEBRTC_PUBLIC_URL / DOGAN_MEDIAMTX_HLS_PUBLIC_URL)")
	}

	if parsed.Scheme != "http" && parsed.Scheme != "https" {
		warnings = append(warnings, "streaming public url scheme must be http or https, got "+parsed.Scheme)
	}

	return warnings
}

func isLocalHost(host string) bool {
	return host == "localhost" || host == "127.0.0.1" || host == "::1" || strings.HasSuffix(host, ".local")
}

func isDockerInternalHost(host string) bool {
	switch host {
	case "dogan-api", "dogan-mediamtx", "dogan-turn", "dogan-cv", "dogan-webui", "mediamtx", "api", "turn", "host.docker.internal":
		return true
	default:
		return false
	}
}
