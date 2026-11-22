package config

import (
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/dogan/dogan-server/internal/auth"
)

const (
	defaultListenAddress      = ":8080"
	defaultEmbeddedAPIToken   = "dg_dev_a8f3c2e1b9d74f6a0e5c3b9d2f7a1e4c8b6d0f3a7e2c9b5d1f8a4e6c0b3d7f9"
	defaultJWTSecret          = "dogan-dev-jwt-secret"
	defaultTokenTTL           = 24 * time.Hour
	defaultDatabaseURL        = "file:./data/dogan.db"
	defaultFramesDir          = "frames"
	defaultSoundsDir          = "sounds"
	defaultAudioDir           = "diagnostic-audio"
	defaultRetentionDays      = 7
	defaultStreamingWebRTCURL = "http://localhost:8889"
	defaultStreamingHLSURL    = "http://localhost:8888"
	defaultMediaMTXAPIURL     = "http://dogan-mediamtx:9997"
	defaultStreamTokenTTL     = time.Hour
	defaultTurnTTL            = 24 * time.Hour
	defaultDeviceAPIKey       = auth.DefaultDeviceAPIKey
	defaultCORSOrigins        = "http://localhost:30808,http://127.0.0.1:30808,https://dogan.xaigrok.ir"
)

type Config struct {
	ListenAddress      string
	EmbeddedAPIToken   string
	DeviceAPIKey       string
	JWTSecret          string
	TokenTTL           time.Duration
	DatabaseURL        string
	FramesDir          string
	SoundsDir          string
	AudioDir           string
	RetentionPeriod    time.Duration
	StreamingWebRTCURL string
	StreamingHLSURL    string
	MediaMTXAPIURL     string
	StreamTokenSecret  string
	StreamTokenTTL     time.Duration
	TurnDomain         string
	TurnExternalTLS    bool
	TurnSecret         string
	TurnTTL            time.Duration
	IceServerURLs      []string
	CORSAllowedOrigins []string
	CVURL              string
}

func Load() Config {
	return Config{
		ListenAddress:      envOrDefault("DOGAN_LISTEN_ADDRESS", defaultListenAddress),
		EmbeddedAPIToken:   envOrDefault("DOGAN_EMBEDDED_API_TOKEN", defaultEmbeddedAPIToken),
		DeviceAPIKey:       envOrDefault("DOGAN_DEVICE_API_KEY", defaultDeviceAPIKey),
		JWTSecret:          envOrDefault("DOGAN_JWT_SECRET", defaultJWTSecret),
		TokenTTL:           envDurationOrDefault("DOGAN_TOKEN_TTL", defaultTokenTTL),
		DatabaseURL:        envOrDefault("DOGAN_DATABASE_URL", defaultDatabaseURL),
		FramesDir:          envOrDefault("DOGAN_FRAMES_DIR", defaultFramesDir),
		SoundsDir:          envOrDefault("DOGAN_SOUNDS_DIR", defaultSoundsDir),
		AudioDir:           envOrDefault("DOGAN_AUDIO_DIR", defaultAudioDir),
		RetentionPeriod:    envDaysOrDefault("DOGAN_RETENTION_DAYS", defaultRetentionDays),
		StreamingWebRTCURL: envOrDefault("DOGAN_MEDIAMTX_WEBRTC_PUBLIC_URL", defaultStreamingWebRTCURL),
		StreamingHLSURL:    envOrDefault("DOGAN_MEDIAMTX_HLS_PUBLIC_URL", defaultStreamingHLSURL),
		MediaMTXAPIURL:     envOrDefault("DOGAN_MEDIAMTX_API_URL", defaultMediaMTXAPIURL),
		StreamTokenSecret:  envOrDefault("DOGAN_STREAM_TOKEN_SECRET", ""),
		StreamTokenTTL:     envDurationOrDefault("DOGAN_STREAM_TOKEN_TTL", defaultStreamTokenTTL),
		TurnDomain:         envOrDefault("DOGAN_TURN_DOMAIN", ""),
		TurnExternalTLS:    envBoolOrDefault("DOGAN_TURN_EXTERNAL_TLS", true),
		TurnSecret:         envOrDefault("DOGAN_TURN_SECRET", ""),
		TurnTTL:            envDurationOrDefault("DOGAN_TURN_TTL", defaultTurnTTL),
		IceServerURLs:      envCSVOrDefault("DOGAN_ICE_SERVER_URLS", ""),
		CORSAllowedOrigins: envCSVOrDefault("DOGAN_CORS_ALLOWED_ORIGINS", defaultCORSOrigins),
		CVURL:              envOrDefault("DOGAN_CV_URL", ""),
	}
}

func envOrDefault(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func envCSVOrDefault(key, fallback string) []string {
	raw := envOrDefault(key, fallback)
	parts := strings.Split(raw, ",")
	values := make([]string, 0, len(parts))
	for _, part := range parts {
		value := strings.TrimSpace(part)
		if value != "" {
			values = append(values, value)
		}
	}
	return values
}

func envDurationOrDefault(key string, fallback time.Duration) time.Duration {
	raw := os.Getenv(key)
	if raw == "" {
		return fallback
	}

	seconds, err := strconv.Atoi(raw)
	if err != nil || seconds <= 0 {
		return fallback
	}

	return time.Duration(seconds) * time.Second
}

func envBoolOrDefault(key string, fallback bool) bool {
	raw := os.Getenv(key)
	if raw == "" {
		return fallback
	}
	parsed, err := strconv.ParseBool(raw)
	if err != nil {
		return fallback
	}
	return parsed
}

func envDaysOrDefault(key string, fallbackDays int) time.Duration {
	raw := os.Getenv(key)
	if raw == "" {
		return time.Duration(fallbackDays) * 24 * time.Hour
	}

	days, err := strconv.Atoi(raw)
	if err != nil || days <= 0 {
		return time.Duration(fallbackDays) * 24 * time.Hour
	}

	return time.Duration(days) * 24 * time.Hour
}
