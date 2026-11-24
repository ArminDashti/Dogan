package streaming

import (
	"context"
	"errors"
	"net/url"
	"time"

	"github.com/bluenviron/gortmplib"
	"github.com/bluenviron/gortmplib/pkg/codecs"
	"github.com/bluenviron/mediacommon/v2/pkg/codecs/mpeg4audio"
)

type PublishConfiguration struct {
	VideoEnabled bool
	AudioEnabled bool
	SPS          []byte
	PPS          []byte
	SampleRate   int
	Channels     int
}

type Publisher interface {
	WriteVideo(time.Duration, [][]byte) error
	WriteAudio(time.Duration, []byte) error
	Close()
}

type RTMPPublisher struct {
	client     *gortmplib.Client
	writer     *gortmplib.Writer
	videoTrack *gortmplib.Track
	audioTrack *gortmplib.Track
}

func NewRTMPPublisher(
	ctx context.Context,
	address string,
	path string,
	token string,
	configuration PublishConfiguration,
) (Publisher, error) {
	if address == "" || path == "" || token == "" {
		return nil, errors.New("incomplete RTMP publish settings")
	}

	streamURL := &url.URL{
		Scheme: "rtmp",
		Host:   address,
		Path:   "/" + path,
	}
	query := url.Values{}
	query.Set("token", token)
	streamURL.RawQuery = query.Encode()

	client := &gortmplib.Client{URL: streamURL, Publish: true}
	if err := client.Initialize(ctx); err != nil {
		return nil, err
	}

	publisher := &RTMPPublisher{client: client}
	tracks := make([]*gortmplib.Track, 0, 2)
	if configuration.VideoEnabled {
		publisher.videoTrack = &gortmplib.Track{
			Codec: &codecs.H264{SPS: configuration.SPS, PPS: configuration.PPS},
		}
		tracks = append(tracks, publisher.videoTrack)
	}
	if configuration.AudioEnabled {
		publisher.audioTrack = &gortmplib.Track{
			Codec: &codecs.MPEG4Audio{
				Config: &mpeg4audio.AudioSpecificConfig{
					Type:         mpeg4audio.ObjectTypeAACLC,
					SampleRate:   configuration.SampleRate,
					ChannelCount: configuration.Channels,
				},
			},
		}
		tracks = append(tracks, publisher.audioTrack)
	}

	publisher.writer = &gortmplib.Writer{Conn: client, Tracks: tracks}
	if err := publisher.writer.Initialize(); err != nil {
		client.Close()
		return nil, err
	}
	return publisher, nil
}

func (publisher *RTMPPublisher) WriteVideo(timestamp time.Duration, units [][]byte) error {
	return publisher.writer.WriteH264(publisher.videoTrack, timestamp, timestamp, units)
}

func (publisher *RTMPPublisher) WriteAudio(timestamp time.Duration, frame []byte) error {
	return publisher.writer.WriteMPEG4Audio(publisher.audioTrack, timestamp, frame)
}

func (publisher *RTMPPublisher) Close() {
	publisher.client.Close()
}
