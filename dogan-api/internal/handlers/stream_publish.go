package handlers

import (
	"context"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"errors"
	"math"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/bluenviron/mediacommon/v2/pkg/codecs/h264"
	"github.com/dogan/dogan-server/internal/mediamtx"
	"github.com/dogan/dogan-server/internal/streaming"
	"github.com/gin-gonic/gin"
	"github.com/gorilla/websocket"
)

const (
	streamPublishPath        = "/dogan/api/v1/stream/publish"
	streamFrameHeader        = 9
	streamMessageLimit       = 4 << 20
	streamConfigurationLimit = 4096
	streamVideoFrame         = 1
	streamAudioFrame         = 2
)

type StreamPublisherHandler struct {
	tokenIssuer     *mediamtx.TokenIssuer
	createPublisher func(context.Context, string, string, streaming.PublishConfiguration) (streaming.Publisher, error)
}

type streamPublishConfiguration struct {
	Type         string `json:"type"`
	Version      int    `json:"version"`
	VideoEnabled bool   `json:"video_enabled"`
	AudioEnabled bool   `json:"audio_enabled"`
	SPS          string `json:"sps"`
	PPS          string `json:"pps"`
	SampleRate   int    `json:"sample_rate"`
	Channels     int    `json:"channels"`
}

func NewStreamPublisherHandler(
	tokenIssuer *mediamtx.TokenIssuer,
	rtmpAddress string,
) *StreamPublisherHandler {
	return &StreamPublisherHandler{
		tokenIssuer: tokenIssuer,
		createPublisher: func(
			ctx context.Context,
			path string,
			token string,
			configuration streaming.PublishConfiguration,
		) (streaming.Publisher, error) {
			return streaming.NewRTMPPublisher(ctx, rtmpAddress, path, token, configuration)
		},
	}
}

func (handler *StreamPublisherHandler) Publish(context *gin.Context) {
	token := streamTokenFromRequest(context.Request)
	claims, err := handler.tokenIssuer.ValidateToken(token)
	if err != nil || claims.Action != mediamtx.ActionPublish ||
		claims.DeviceID == "" || claims.Path != mediamtx.PathForDevice(claims.DeviceID) {
		context.AbortWithStatus(http.StatusUnauthorized)
		return
	}

	upgrader := websocket.Upgrader{
		CheckOrigin: func(request *http.Request) bool {
			return request.Header.Get("Origin") == ""
		},
	}
	connection, err := upgrader.Upgrade(context.Writer, context.Request, nil)
	if err != nil {
		return
	}
	defer connection.Close()
	connection.SetReadLimit(streamMessageLimit)

	messageType, payload, err := connection.ReadMessage()
	if err != nil || messageType != websocket.TextMessage || len(payload) > streamConfigurationLimit {
		writeStreamError(connection)
		return
	}
	configuration, err := decodeStreamConfiguration(payload)
	if err != nil {
		writeStreamError(connection)
		return
	}

	publisher, err := handler.createPublisher(
		context.Request.Context(),
		claims.Path,
		token,
		configuration,
	)
	if err != nil {
		writeStreamError(connection)
		return
	}
	defer publisher.Close()
	if err := connection.WriteJSON(map[string]string{"type": "ready"}); err != nil {
		return
	}

	for {
		messageType, payload, err = connection.ReadMessage()
		if err != nil {
			return
		}
		if messageType != websocket.BinaryMessage || forwardStreamFrame(publisher, payload) != nil {
			writeStreamError(connection)
			return
		}
	}
}

func streamPublishWebSocketURL(context *gin.Context) string {
	protocol := strings.TrimSpace(strings.Split(context.GetHeader("X-Forwarded-Proto"), ",")[0])
	scheme := "ws"
	if context.Request.TLS != nil || strings.EqualFold(protocol, "https") {
		scheme = "wss"
	}
	return (&url.URL{
		Scheme: scheme,
		Host:   context.Request.Host,
		Path:   streamPublishPath,
	}).String()
}

func streamTokenFromRequest(request *http.Request) string {
	authorization := strings.Fields(request.Header.Get("Authorization"))
	if len(authorization) == 2 && strings.EqualFold(authorization[0], "Bearer") {
		return authorization[1]
	}
	return request.URL.Query().Get("token")
}

func decodeStreamConfiguration(payload []byte) (streaming.PublishConfiguration, error) {
	var wire streamPublishConfiguration
	if err := json.Unmarshal(payload, &wire); err != nil {
		return streaming.PublishConfiguration{}, err
	}
	if wire.Type != "config" || wire.Version != 1 || (!wire.VideoEnabled && !wire.AudioEnabled) {
		return streaming.PublishConfiguration{}, errors.New("invalid stream configuration")
	}

	configuration := streaming.PublishConfiguration{
		VideoEnabled: wire.VideoEnabled,
		AudioEnabled: wire.AudioEnabled,
		SampleRate:   wire.SampleRate,
		Channels:     wire.Channels,
	}
	if wire.VideoEnabled {
		var err error
		configuration.SPS, err = decodeNAL(wire.SPS, 7)
		if err != nil {
			return streaming.PublishConfiguration{}, err
		}
		configuration.PPS, err = decodeNAL(wire.PPS, 8)
		if err != nil {
			return streaming.PublishConfiguration{}, err
		}
	}
	if wire.AudioEnabled && (wire.SampleRate < 8000 || wire.SampleRate > 96000 ||
		(wire.Channels != 1 && wire.Channels != 2)) {
		return streaming.PublishConfiguration{}, errors.New("invalid AAC configuration")
	}
	return configuration, nil
}

func decodeNAL(encoded string, expectedType byte) ([]byte, error) {
	nal, err := base64.StdEncoding.DecodeString(encoded)
	if err != nil || len(nal) == 0 || len(nal) > 256 {
		return nil, errors.New("invalid H.264 configuration")
	}
	if len(nal) >= 4 && string(nal[:4]) == "\x00\x00\x00\x01" {
		nal = nal[4:]
	} else if len(nal) >= 3 && string(nal[:3]) == "\x00\x00\x01" {
		nal = nal[3:]
	}
	if len(nal) == 0 || nal[0]&0x1f != expectedType {
		return nil, errors.New("invalid H.264 configuration")
	}
	return nal, nil
}

func forwardStreamFrame(publisher streaming.Publisher, payload []byte) error {
	if len(payload) <= streamFrameHeader {
		return errors.New("empty stream frame")
	}
	microseconds := binary.BigEndian.Uint64(payload[1:streamFrameHeader])
	if microseconds > uint64(math.MaxInt64/int64(time.Microsecond)) {
		return errors.New("invalid stream timestamp")
	}
	timestamp := time.Duration(microseconds) * time.Microsecond
	frame := payload[streamFrameHeader:]
	switch payload[0] {
	case streamVideoFrame:
		var units h264.AVCC
		if err := units.Unmarshal(frame); err != nil {
			var annexB h264.AnnexB
			if annexBErr := annexB.Unmarshal(frame); annexBErr != nil {
				return err
			}
			units = h264.AVCC(annexB)
		}
		return publisher.WriteVideo(timestamp, [][]byte(units))
	case streamAudioFrame:
		return publisher.WriteAudio(timestamp, frame)
	default:
		return errors.New("unknown stream frame type")
	}
}

func writeStreamError(connection *websocket.Conn) {
	_ = connection.WriteJSON(map[string]string{"type": "error"})
}
