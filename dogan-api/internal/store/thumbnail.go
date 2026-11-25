package store

import (
	"bytes"
	"fmt"
	"image"
	_ "image/jpeg"
	"image/jpeg"
	"os"
	"path/filepath"

	"golang.org/x/image/draw"
)

const thumbnailLongEdge = 256

func ThumbnailPath(framePath string) string {
	ext := filepath.Ext(framePath)
	base := framePath[:len(framePath)-len(ext)]
	return base + "_thumb" + ext
}

func EnsureThumbnail(framePath string) (string, error) {
	thumbPath := ThumbnailPath(framePath)
	if info, err := os.Stat(thumbPath); err == nil && info.Size() > 0 {
		return thumbPath, nil
	}
	source, err := os.ReadFile(framePath)
	if err != nil {
		return "", fmt.Errorf("read frame: %w", err)
	}
	decoded, _, err := image.Decode(bytes.NewReader(source))
	if err != nil {
		return "", fmt.Errorf("decode frame: %w", err)
	}
	bounds := decoded.Bounds()
	width := bounds.Dx()
	height := bounds.Dy()
	longEdge := width
	if height > longEdge {
		longEdge = height
	}
	targetW, targetH := width, height
	if longEdge > thumbnailLongEdge {
		scale := float64(thumbnailLongEdge) / float64(longEdge)
		targetW = int(float64(width) * scale)
		targetH = int(float64(height) * scale)
		if targetW < 1 {
			targetW = 1
		}
		if targetH < 1 {
			targetH = 1
		}
	}
	dst := image.NewRGBA(image.Rect(0, 0, targetW, targetH))
	draw.CatmullRom.Scale(dst, dst.Bounds(), decoded, bounds, draw.Over, nil)
	var encoded bytes.Buffer
	if err := jpeg.Encode(&encoded, dst, &jpeg.Options{Quality: 72}); err != nil {
		return "", fmt.Errorf("encode thumbnail: %w", err)
	}
	if err := os.WriteFile(thumbPath, encoded.Bytes(), 0o644); err != nil {
		return "", fmt.Errorf("write thumbnail: %w", err)
	}
	return thumbPath, nil
}