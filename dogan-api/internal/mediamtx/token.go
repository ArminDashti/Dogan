package mediamtx

import (
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const (
	ActionPublish  = "publish"
	ActionRead     = "read"
	ActionPlayback = "playback"

	tokenIssuerName = "dogan-api"
	tokenAudience   = "dogan-mediamtx"
)

type Config struct {
	WebRTCPublicURL string
	HLSPublicURL    string
	APIURL          string
	TokenSecret     string
	TokenTTL        time.Duration
}

func (config Config) Enabled() bool {
	return strings.TrimSpace(config.WebRTCPublicURL) != "" && strings.TrimSpace(config.TokenSecret) != ""
}

type TokenRequest struct {
	DeviceID string
	Identity string
	Role     string
	ValidFor time.Duration
}

type TokenResponse struct {
	Token     string
	Path      string
	Identity  string
	Action    string
	WHIPURL   string
	WHEPURL   string
	HLSURL    string
	ExpiresAt time.Time
}

type Claims struct {
	jwt.RegisteredClaims
	DeviceID string `json:"device_id"`
	Path     string `json:"path"`
	Action   string `json:"action"`
}

type TokenIssuer struct {
	config Config
}

func NewTokenIssuer(config Config) *TokenIssuer {
	return &TokenIssuer{config: config}
}

func (issuer *TokenIssuer) Enabled() bool {
	return issuer.config.Enabled()
}

func (issuer *TokenIssuer) IssueToken(request TokenRequest) (TokenResponse, error) {
	if !issuer.config.Enabled() {
		return TokenResponse{}, ErrNotConfigured
	}

	role := request.Role
	if role == "" {
		role = RoleSubscriber
	}
	if role != RolePublisher && role != RoleSubscriber {
		return TokenResponse{}, ErrInvalidRole
	}

	action := ActionRead
	if role == RolePublisher {
		action = ActionPublish
	}

	streamPath := PathForDevice(request.DeviceID)
	identity := strings.TrimSpace(request.Identity)
	if identity == "" {
		identity = DefaultIdentity(role, request.DeviceID)
	}

	validFor := request.ValidFor
	if validFor <= 0 {
		validFor = issuer.config.TokenTTL
	}
	if validFor <= 0 {
		return TokenResponse{}, errors.New("stream token ttl must be positive")
	}

	now := time.Now().UTC()
	expiresAt := now.Add(validFor)
	claims := Claims{
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    tokenIssuerName,
			Subject:   identity,
			Audience:  jwt.ClaimStrings{tokenAudience},
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(expiresAt),
		},
		DeviceID: request.DeviceID,
		Path:     streamPath,
		Action:   action,
	}

	token, err := jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString([]byte(issuer.config.TokenSecret))
	if err != nil {
		return TokenResponse{}, fmt.Errorf("issue stream token: %w", err)
	}

	webrtcBaseURL := strings.TrimRight(strings.TrimSpace(issuer.config.WebRTCPublicURL), "/")
	response := TokenResponse{
		Token:     token,
		Path:      streamPath,
		Identity:  identity,
		Action:    action,
		WHIPURL:   webrtcBaseURL + "/" + streamPath + "/whip",
		WHEPURL:   webrtcBaseURL + "/" + streamPath + "/whep",
		ExpiresAt: expiresAt,
	}

	hlsBaseURL := strings.TrimRight(strings.TrimSpace(issuer.config.HLSPublicURL), "/")
	if hlsBaseURL != "" {
		response.HLSURL = hlsBaseURL + "/" + streamPath + "/index.m3u8"
	}

	return response, nil
}

func (issuer *TokenIssuer) ValidateToken(tokenString string) (Claims, error) {
	if !issuer.config.Enabled() {
		return Claims{}, ErrNotConfigured
	}

	var claims Claims
	parsedToken, err := jwt.ParseWithClaims(
		strings.TrimSpace(tokenString),
		&claims,
		func(token *jwt.Token) (any, error) {
			return []byte(issuer.config.TokenSecret), nil
		},
		jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}),
		jwt.WithIssuer(tokenIssuerName),
		jwt.WithAudience(tokenAudience),
	)
	if err != nil {
		return Claims{}, fmt.Errorf("validate stream token: %w", err)
	}
	if !parsedToken.Valid {
		return Claims{}, errors.New("validate stream token: invalid token")
	}

	return claims, nil
}
