package main

import (
	"log"
	"net/http"
	"os"

	"github.com/dogan/dogan-cv/internal/pont"
	"github.com/dogan/dogan-cv/internal/server"
)

func main() {
	listen := envOr("DOGAN_CV_LISTEN", ":8090")
	framesDir := envOr("DOGAN_FRAMES_DIR", "/data/frames")
	client := pont.NewClient(
		os.Getenv("OPENCODE_GO_BASE_URL"),
		os.Getenv("OPENCODE_GO_API_KEY"),
		os.Getenv("OPENCODE_GO_MODEL"),
		os.Getenv("OPENCODE_GO_PROMPT"),
	)
	log.Printf("dogan-cv listening on %s frames=%s vision=%t", listen, framesDir, client.Enabled())
	if err := http.ListenAndServe(listen, server.New(framesDir, client).Handler()); err != nil {
		log.Fatal(err)
	}
}

func envOr(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
