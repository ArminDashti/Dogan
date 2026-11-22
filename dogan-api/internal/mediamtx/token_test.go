package mediamtx

import (
	"errors"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

func testIssuer() *TokenIssuer {
	return NewTokenIssuer(Config{
		WebRTCPublicURL: "http://localhost:8889/",
		HLSPublicURL:    "http://localhost:8888/",
		APIURL:          "http://dogan-mediamtx:9997",
		TokenSecret:     "test-stream-secret",
		TokenTTL:        time.Hour,
	})
}

func TestIssueTokenPublisher(t *testing.T) {
	response, err := testIssuer().IssueToken(TokenRequest{DeviceID: "Cam-01", Role: RolePublisher})
	if err != nil {
		t.Fatalf("issue publisher token: %v", err)
	}

	if response.Path != "device-Cam-01" {
		t.Fatalf("unexpected path %q", response.Path)
	}
	if response.Identity != "publisher-Cam-01" {
		t.Fatalf("unexpected identity %q", response.Identity)
	}
	if response.Action != ActionPublish {
		t.Fatalf("unexpected action %q", response.Action)
	}
	if response.WHIPURL != "http://localhost:8889/device-Cam-01/whip" {
		t.Fatalf("unexpected whip url %q", response.WHIPURL)
	}
	if response.WHEPURL != "http://localhost:8889/device-Cam-01/whep" {
		t.Fatalf("unexpected whep url %q", response.WHEPURL)
	}
	if response.HLSURL != "http://localhost:8888/device-Cam-01/index.m3u8" {
		t.Fatalf("unexpected hls url %q", response.HLSURL)
	}
	if response.ExpiresAt.IsZero() {
		t.Fatal("expected expiration timestamp")
	}
}

func TestIssueTokenSubscriberWithoutHLSPublicURL(t *testing.T) {
	issuer := NewTokenIssuer(Config{WebRTCPublicURL: "https://dogan-media.xaigrok.ir", TokenSecret: "secret", TokenTTL: time.Hour})
	response, err := issuer.IssueToken(TokenRequest{DeviceID: "dogan-test-device"})
	if err != nil {
		t.Fatalf("issue subscriber token: %v", err)
	}

	if response.Action != ActionRead {
		t.Fatalf("unexpected action %q", response.Action)
	}
	if response.WHEPURL != "https://dogan-media.xaigrok.ir/device-dogan-test-device/whep" {
		t.Fatalf("unexpected whep url %q", response.WHEPURL)
	}
	if response.HLSURL != "" {
		t.Fatalf("expected empty hls url, got %q", response.HLSURL)
	}
}

func TestIssueTokenValidatesRoleAndConfiguration(t *testing.T) {
	if _, err := testIssuer().IssueToken(TokenRequest{DeviceID: "cam", Role: "admin"}); !errors.Is(err, ErrInvalidRole) {
		t.Fatalf("expected ErrInvalidRole, got %v", err)
	}

	issuer := NewTokenIssuer(Config{TokenSecret: "secret", TokenTTL: time.Hour})
	if _, err := issuer.IssueToken(TokenRequest{DeviceID: "cam"}); !errors.Is(err, ErrNotConfigured) {
		t.Fatalf("expected ErrNotConfigured, got %v", err)
	}
}

func TestValidateTokenClaims(t *testing.T) {
	issuer := testIssuer()
	response, err := issuer.IssueToken(TokenRequest{DeviceID: "Cam-01", Identity: "watchman", Role: RolePublisher, ValidFor: 30 * time.Minute})
	if err != nil {
		t.Fatalf("issue token: %v", err)
	}

	claims, err := issuer.ValidateToken(response.Token)
	if err != nil {
		t.Fatalf("validate token: %v", err)
	}
	if claims.Subject != "watchman" || claims.DeviceID != "Cam-01" || claims.Path != "device-Cam-01" || claims.Action != ActionPublish {
		t.Fatalf("unexpected claims: %+v", claims)
	}
	if claims.Issuer != "dogan-api" || len(claims.Audience) != 1 || claims.Audience[0] != "dogan-mediamtx" {
		t.Fatalf("unexpected issuer/audience: %+v", claims)
	}
	if claims.ExpiresAt == nil || claims.IssuedAt == nil {
		t.Fatal("expected iat and exp claims")
	}
}

func TestValidateTokenRejectsInvalidTokens(t *testing.T) {
	issuer := testIssuer()
	response, err := issuer.IssueToken(TokenRequest{DeviceID: "cam"})
	if err != nil {
		t.Fatalf("issue token: %v", err)
	}

	otherIssuer := NewTokenIssuer(Config{WebRTCPublicURL: "https://dogan-media.xaigrok.ir", TokenSecret: "other-secret", TokenTTL: time.Hour})
	if _, err := otherIssuer.ValidateToken(response.Token); err == nil {
		t.Fatal("expected wrong-secret token to be rejected")
	}

	for _, mutate := range []func(*Claims){
		func(claims *Claims) { claims.Issuer = "another-api" },
		func(claims *Claims) { claims.Audience = jwt.ClaimStrings{"another-audience"} },
		func(claims *Claims) { claims.ExpiresAt = jwt.NewNumericDate(time.Now().Add(-time.Minute)) },
	} {
		claims := Claims{}
		parsed, parseErr := jwt.ParseWithClaims(response.Token, &claims, func(token *jwt.Token) (any, error) {
			return []byte("test-stream-secret"), nil
		})
		if parseErr != nil || !parsed.Valid {
			t.Fatalf("parse original token: %v", parseErr)
		}
		mutate(&claims)
		modifiedToken, signErr := jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString([]byte("test-stream-secret"))
		if signErr != nil {
			t.Fatalf("sign modified token: %v", signErr)
		}
		if _, err := issuer.ValidateToken(modifiedToken); err == nil {
			t.Fatal("expected mutated token to be rejected")
		}
	}
}
