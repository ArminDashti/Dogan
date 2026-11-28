package store

import (
	"log"
	"os"
	"time"
)

const retentionCleanupInterval = time.Hour

type RetentionCleaner struct {
	store           RetentionStore
	framesDir       string
	retentionPeriod time.Duration
}

func NewRetentionCleaner(store RetentionStore, framesDir string, retentionPeriod time.Duration) *RetentionCleaner {
	return &RetentionCleaner{
		store:           store,
		framesDir:       framesDir,
		retentionPeriod: retentionPeriod,
	}
}

func (cleaner *RetentionCleaner) Start() {
	go cleaner.run()
}

func (cleaner *RetentionCleaner) run() {
	cleaner.cleanupOnce()

	ticker := time.NewTicker(retentionCleanupInterval)
	defer ticker.Stop()

	for range ticker.C {
		cleaner.cleanupOnce()
	}
}

func (cleaner *RetentionCleaner) cleanupOnce() {
	now := time.Now().UTC()
	frameCutoff := laterCutoff(now.Add(-cleaner.retentionPeriod), TehranStartOfDay(now))
	metricsCutoff := laterCutoff(now.Add(-cleaner.retentionPeriod), TehranStartOfYesterday(now))

	framePaths, err := cleaner.store.PurgeExpiredFrames(frameCutoff)
	if err != nil {
		log.Printf("retention cleanup failed for frames: %v", err)
	} else {
		cleaner.removeFrameFiles(framePaths)
	}

	if err := cleaner.store.PurgeExpiredMetrics(metricsCutoff); err != nil {
		log.Printf("retention cleanup failed for metrics: %v", err)
	}
}

// laterCutoff keeps whatever older data must be dropped first: the retention
// window and the product's "today only" (frames) or "today and yesterday"
// (metrics) rule.
func laterCutoff(byRetention, byProductRule time.Time) time.Time {
	if byProductRule.After(byRetention) {
		return byProductRule
	}
	return byRetention
}

func (cleaner *RetentionCleaner) removeFrameFiles(framePaths []string) {
	for _, framePath := range framePaths {
		if err := os.Remove(framePath); err != nil && !os.IsNotExist(err) {
			log.Printf("retention cleanup failed to remove frame file %s: %v", framePath, err)
		}
	}
}
