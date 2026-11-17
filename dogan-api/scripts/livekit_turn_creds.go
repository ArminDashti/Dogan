//go:build ignore

// One-off LiveKit embedded-TURN credentials (matches pkg/service/turn.go).
// Usage: go run scripts/livekit_turn_creds.go [apiKey] [apiSecret] [participantID]
package main

import (
	"crypto/sha256"
	"fmt"
	"os"
	"time"

	"github.com/jxskiss/base62"
)

func main() {
	apiKey, secret, pid := "devkey", "secret", "dogan-turn-verify"
	if len(os.Args) >= 2 {
		apiKey = os.Args[1]
	}
	if len(os.Args) >= 3 {
		secret = os.Args[2]
	}
	if len(os.Args) >= 4 {
		pid = os.Args[3]
	}
	ttl := 300
	expiry := time.Now().Add(time.Duration(ttl) * time.Second).Unix()
	username := base62.EncodeToString(fmt.Appendf(nil, "%s|%s|%d", apiKey, pid, expiry))
	keyInput := fmt.Sprintf("%s|%s|%d", secret, pid, expiry)
	sum := sha256.Sum256([]byte(keyInput))
	password := base62.EncodeToString(sum[:])
	fmt.Println(username)
	fmt.Println(password)
}