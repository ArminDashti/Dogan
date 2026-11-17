package livekit

import (
	"fmt"
	"strings"

	"github.com/dogan/dogan-server/internal/models"
)

// IceServerOptions lists optional TURN hints for clients. LiveKit still issues
// time-limited relay credentials over signaling; these URLs document the edge TURN host.
type IceServerOptions struct {
	TurnDomain        string
	TurnExternalTLS   bool
	ExtraURLs         []string
}

func DefaultIceServers() []models.IceServerConfig {
	return IceServers(IceServerOptions{})
}

func IceServers(options IceServerOptions) []models.IceServerConfig {
	servers := []models.IceServerConfig{
		{
			URLs: []string{"stun:stun.l.google.com:19302"},
		},
	}

	turnDomain := strings.TrimSpace(options.TurnDomain)
	if turnDomain != "" {
		var turnURL string
		if options.TurnExternalTLS {
			// Matches livekit.t3.yaml external_tls: HAProxy terminates TLS on :443.
			turnURL = fmt.Sprintf("turns:%s:443?transport=tcp", turnDomain)
		} else {
			turnURL = fmt.Sprintf("turn:%s:5349?transport=tcp", turnDomain)
		}
		servers = append(servers, models.IceServerConfig{
			URLs: []string{turnURL},
		})
	}

	for _, extra := range options.ExtraURLs {
		extra = strings.TrimSpace(extra)
		if extra == "" {
			continue
		}
		servers = append(servers, models.IceServerConfig{URLs: []string{extra}})
	}

	return servers
}
