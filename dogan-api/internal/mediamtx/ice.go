package mediamtx

import (
	"crypto/hmac"
	"crypto/sha1"
	"encoding/base64"
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/dogan/dogan-server/internal/models"
)

const defaultTurnTTL = 24 * time.Hour

type IceServerOptions struct {
	TurnDomain      string
	TurnExternalTLS bool
	TurnSecret      string
	TurnTTL         time.Duration
	ExtraURLs       []string
}

func IceServers(options IceServerOptions, identity string) []models.IceServerConfig {
	servers := []models.IceServerConfig{
		{URLs: []string{"stun:stun.l.google.com:19302"}},
	}

	turnDomain := strings.TrimSpace(options.TurnDomain)
	if turnDomain != "" {
		turnURL := fmt.Sprintf("turn:%s:5349?transport=tcp", turnDomain)
		if options.TurnExternalTLS {
			turnURL = fmt.Sprintf("turns:%s:443?transport=tcp", turnDomain)
		}

		turnServer := models.IceServerConfig{URLs: []string{turnURL}}
		if secret := strings.TrimSpace(options.TurnSecret); secret != "" {
			turnTTL := options.TurnTTL
			if turnTTL <= 0 {
				turnTTL = defaultTurnTTL
			}
			if strings.TrimSpace(identity) == "" {
				identity = "unknown"
			}

			expiry := time.Now().Add(turnTTL).Unix()
			username := strconv.FormatInt(expiry, 10) + ":" + identity
			mac := hmac.New(sha1.New, []byte(secret))
			_, _ = mac.Write([]byte(username))
			turnServer.Username = username
			turnServer.Credential = base64.StdEncoding.EncodeToString(mac.Sum(nil))
		}

		servers = append(servers, turnServer)
	}

	for _, extra := range options.ExtraURLs {
		extra = strings.TrimSpace(extra)
		if extra != "" {
			servers = append(servers, models.IceServerConfig{URLs: []string{extra}})
		}
	}

	return servers
}
