package handlers

import (
	"context"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/dogan/dogan-server/internal/mediamtx"
	"github.com/dogan/dogan-server/internal/streaming"
	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
	"github.com/gorilla/websocket"
)

type capturedFrame struct {
	video     [][]byte
	audio     []byte
	timestamp time.Duration
}

type testPublisher struct {
	frames chan capturedFrame
}

func (publisher *testPublisher) WriteVideo(timestamp time.Duration, units [][]byte) error {
	publisher.frames <- capturedFrame{timestamp: timestamp, video: units}
	return nil
}

func (publisher *testPublisher) WriteAudio(timestamp time.Duration, frame []byte) error {
	publisher.frames <- capturedFrame{timestamp: timestamp, audio: frame}
	return nil
}

func (*testPublisher) Close() {}

func TestStreamPublisherBridgesAuthenticatedWebSocketFrames(t *testing.T) {
	gin.SetMode(gin.TestMode)
	issuer := testStreamTokenIssuer()
	tokenResponse, err := issuer.IssueToken(mediamtx.TokenRequest{
		DeviceID: "camera-1",
		Role:     mediamtx.RolePublisher,
	})
	if err != nil {
		t.Fatal(err)
	}

	publisher := &testPublisher{frames: make(chan capturedFrame, 2)}
	handler := NewStreamPublisherHandler(issuer, "127.0.0.1:1935")
	handler.createPublisher = func(
		_ context.Context,
		path string,
		token string,
		configuration streaming.PublishConfiguration,
	) (streaming.Publisher, error) {
		if path != "device-camera-1" || token != tokenResponse.Token {
			t.Errorf("unexpected publish target: path=%q", path)
		}
		if !configuration.VideoEnabled || !configuration.AudioEnabled ||
			configuration.SampleRate != 44100 || configuration.Channels != 1 {
			t.Errorf("unexpected publish configuration: %+v", configuration)
		}
		return publisher, nil
	}

	engine := gin.New()
	engine.GET(streamPublishPath, handler.Publish)
	server := httptest.NewServer(engine)
	defer server.Close()

	connection, _, err := websocket.DefaultDialer.Dial(
		"ws"+strings.TrimPrefix(server.URL, "http")+streamPublishPath,
		http.Header{"Authorization": []string{"Bearer " + tokenResponse.Token}},
	)
	if err != nil {
		t.Fatal(err)
	}
	defer connection.Close()

	configuration, err := json.Marshal(streamPublishConfiguration{
		Type:         "config",
		Version:      1,
		VideoEnabled: true,
		AudioEnabled: true,
		SPS:          base64.StdEncoding.EncodeToString([]byte{0x67, 0x64}),
		PPS:          base64.StdEncoding.EncodeToString([]byte{0x68, 0x01}),
		SampleRate:   44100,
		Channels:     1,
	})
	if err != nil {
		t.Fatal(err)
	}
	if err := connection.WriteMessage(websocket.TextMessage, configuration); err != nil {
		t.Fatal(err)
	}
	var response map[string]string
	if err := connection.ReadJSON(&response); err != nil {
		t.Fatal(err)
	}
	if response["type"] != "ready" {
		t.Fatalf("unexpected bridge response: %v", response)
	}

	videoFrame := make([]byte, streamFrameHeader+6)
	videoFrame[0] = streamVideoFrame
	binary.BigEndian.PutUint64(videoFrame[1:streamFrameHeader], 125000)
	copy(videoFrame[streamFrameHeader:], []byte{0, 0, 0, 2, 0x65, 0x01})
	if err := connection.WriteMessage(websocket.BinaryMessage, videoFrame); err != nil {
		t.Fatal(err)
	}
	audioFrame := make([]byte, streamFrameHeader+2)
	audioFrame[0] = streamAudioFrame
	binary.BigEndian.PutUint64(audioFrame[1:streamFrameHeader], 150000)
	copy(audioFrame[streamFrameHeader:], []byte{0x21, 0x10})
	if err := connection.WriteMessage(websocket.BinaryMessage, audioFrame); err != nil {
		t.Fatal(err)
	}

	video := <-publisher.frames
	if video.timestamp != 125*time.Millisecond || len(video.video) != 1 ||
		string(video.video[0]) != string([]byte{0x65, 0x01}) {
		t.Fatalf("unexpected video frame: %+v", video)
	}
	audio := <-publisher.frames
	if audio.timestamp != 150*time.Millisecond || string(audio.audio) != string([]byte{0x21, 0x10}) {
		t.Fatalf("unexpected audio frame: %+v", audio)
	}
}

func TestStreamPublisherRejectsMismatchedDevicePath(t *testing.T) {
	gin.SetMode(gin.TestMode)
	issuer := testStreamTokenIssuer()
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, mediamtx.Claims{
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    "dogan-api",
			Audience:  jwt.ClaimStrings{"dogan-mediamtx"},
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(time.Hour)),
		},
		DeviceID: "camera-1",
		Path:     "device-camera-2",
		Action:   mediamtx.ActionPublish,
	})
	tokenString, err := token.SignedString([]byte("test-stream-secret"))
	if err != nil {
		t.Fatal(err)
	}

	handler := NewStreamPublisherHandler(issuer, "127.0.0.1:1935")
	engine := gin.New()
	engine.GET(streamPublishPath, handler.Publish)
	server := httptest.NewServer(engine)
	defer server.Close()

	_, response, err := websocket.DefaultDialer.Dial(
		"ws"+strings.TrimPrefix(server.URL, "http")+streamPublishPath+"?token="+tokenString,
		nil,
	)
	if err == nil {
		t.Fatal("expected mismatched device path to be rejected")
	}
	if response == nil || response.StatusCode != http.StatusUnauthorized {
		t.Fatalf("unexpected response: %v", response)
	}
}

func TestStreamPublishWebSocketURLUsesForwardedHTTPS(t *testing.T) {
	context, _ := gin.CreateTestContext(httptest.NewRecorder())
	context.Request = httptest.NewRequest(http.MethodPost, "/dogan/api/v1/webrtc/session", nil)
	context.Request.Host = "dogan-api.example.test"
	context.Request.Header.Set("X-Forwarded-Proto", "https")

	publishURL := streamPublishWebSocketURL(context)
	if publishURL != "wss://dogan-api.example.test"+streamPublishPath {
		t.Fatalf("unexpected publish URL: %s", publishURL)
	}
}

func TestStreamTokenFromRequestAcceptsBearerAndQuery(t *testing.T) {
	bearerRequest := httptest.NewRequest(http.MethodGet, streamPublishPath, nil)
	bearerRequest.Header.Set("Authorization", "Bearer header-token")
	if token := streamTokenFromRequest(bearerRequest); token != "header-token" {
		t.Fatalf("unexpected bearer token: %q", token)
	}

	queryRequest := httptest.NewRequest(http.MethodGet, streamPublishPath+"?token=query-token", nil)
	if token := streamTokenFromRequest(queryRequest); token != "query-token" {
		t.Fatalf("unexpected query token: %q", token)
	}
}

func testStreamTokenIssuer() *mediamtx.TokenIssuer {
	return mediamtx.NewTokenIssuer(mediamtx.Config{
		WebRTCPublicURL: "https://media.example.test",
		TokenSecret:     "test-stream-secret",
		TokenTTL:        time.Hour,
	})
}
