package handlers

import (
	"testing"
	"time"
)

func TestMetricsRangeBoundsToday(t *testing.T) {
	from, to := metricsRangeBounds("today")
	now := time.Now().UTC()
	today := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, time.UTC)
	if !from.Equal(today) {
		t.Fatalf("from=%v want %v", from, today)
	}
	if !to.Equal(today.AddDate(0, 0, 1)) {
		t.Fatalf("to=%v want %v", to, today.AddDate(0, 0, 1))
	}
}

func TestMetricsRangeBoundsYesterday(t *testing.T) {
	from, to := metricsRangeBounds("yesterday")
	now := time.Now().UTC()
	today := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, time.UTC)
	if !from.Equal(today.AddDate(0, 0, -1)) || !to.Equal(today) {
		t.Fatalf("yesterday bounds from=%v to=%v", from, to)
	}
}
