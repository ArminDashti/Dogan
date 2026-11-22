package config

import (
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/dogan/dogan-server/internal/auth"
)

const (
	defaultListenAddress    = ":8080"
	defaultEmbeddedAPIToken = "dg_dev_a8f3c2e1b9d74f6a0e5c3b9d2f7a1e4c8b6d0f3a7e2c9b5d1f8a4e6c0b3d7f9"
	defaultJWTSecret        = "dogan-dev-jwt-secret"
	defaultTokenTTL         = 24 * time.Hour
	defaultDatabaseURL      = "file:./data/dogan.db"
	defaultFramesDir        = "frames"
	defaultSoundsDir        = "sounds"
	defaultAudioDir         = "diagnostic-audio"
	defaultRetentionDays    = 7
	defaultLiveKitURL       = "ws://localhost:7880"
	defaultLiveKitAPIKey    = "devkey"
	defaultLiveKitAPISecret = "secret"
	defaultLiveKitTokenTTL  = time.Hour
	defaultLiveKitTurnDomain = "dogan-turn.xaigrok.ir"
	defaultTurnURL           = "turns:dogan-turn.xaigrok.ir:443?transport=tcp"
	defaultTurnCredentialTTL = 24 * time.Hour
	defaultDeviceAPIKey     = auth.DefaultDeviceAPIKey
	defaultCORSOrigins = "http://localhost:30808,http://127.0.0.1:30808,https://dogan.xaigrok.ir"
)

type Config struct {
	ListenAddress       string
	EmbeddedAPIToken    string
	DeviceAPIKey        string
	JWTSecret           string
	TokenTTL            time.Duration
	DatabaseURL         string
	FramesDir           string
	SoundsDir           string
	AudioDir            string
	RetentionPeriod     time.Duration
	LiveKitURL          string
	LiveKitPublicURL    string
	LiveKitAPIKey       string
	LiveKitAPISecret    string
	LiveKitTokenTTL     time.Duration
	LiveKitTurnDomain   string
	TurnURL             string
	TurnSecret          string
	TurnCredentialTTL   time.Duration
	LiveKitIceServerURLs []string
	CORSAllowedOrigins []string
	CVURL              string
}

func (config Config) ClientLiveKitURL() string {
	if config.LiveKitPublicURL != "" {
		return config.LiveKitPublicURL
	}
	return config.LiveKitURL
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
		LiveKitURL:         envOrDefault("DOGAN_LIVEKIT_URL", defaultLiveKitURL),
		LiveKitPublicURL:   envOrDefault("DOGAN_LIVEKIT_PUBLIC_URL", ""),
		LiveKitAPIKey:      envOrDefault("DOGAN_LIVEKIT_API_KEY", defaultLiveKitAPIKey),
		LiveKitAPISecret:   envOrDefault("DOGAN_LIVEKIT_API_SECRET", defaultLiveKitAPISecret),
		LiveKitTokenTTL:    envDurationOrDefault("DOGAN_LIVEKIT_TOKEN_TTL", defaultLiveKitTokenTTL),
		LiveKitTurnDomain:  envOrDefault("DOGAN_LIVEKIT_TURN_DOMAIN", defaultLiveKitTurnDomain),
		TurnURL:            envOrDefault("DOGAN_TURN_URL", defaultTurnURL),
		TurnSecret:         os.Getenv("DOGAN_TURN_SECRET"),
		TurnCredentialTTL:  envDurationOrDefault("DOGAN_TURN_CREDENTIAL_TTL", defaultTurnCredentialTTL),
		LiveKitIceServerURLs:   envCSVOrDefault("DOGAN_LIVEKIT_ICE_SERVER_URLS", ""),
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
	origins := make([]string, 0, len(parts))
	for _, part := range parts {
		origin := strings.TrimSpace(part)
		if origin != "" {
			origins = append(origins, origin)
		}
	}
	return origins
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
