package handlers

import (
	"testing"
	"time"

	"github.com/dogan/dogan-server/internal/store"
)

func TestMetricsRangeBoundsToday(t *testing.T) {
	from, to := metricsRangeBounds("today")
	today := store.TehranStartOfDay(time.Now())
	if !from.Equal(today) {
		t.Fatalf("from=%v want %v", from, today)
	}
	if !to.Equal(today.AddDate(0, 0, 1)) {
		t.Fatalf("to=%v want %v", to, today.AddDate(0, 0, 1))
	}
}

func TestMetricsRangeBoundsYesterday(t *testing.T) {
	from, to := metricsRangeBounds("yesterday")
	today := store.TehranStartOfDay(time.Now())
	if !from.Equal(today.AddDate(0, 0, -1)) || !to.Equal(today) {
		t.Fatalf("yesterday bounds from=%v to=%v", from, to)
	}
}

func TestMetricsRangeBoundsTodayYesterday(t *testing.T) {
	from, to := metricsRangeBounds("today_yesterday")
	today := store.TehranStartOfDay(time.Now())
	if !from.Equal(today.AddDate(0, 0, -1)) {
		t.Fatalf("from=%v want yesterday start %v", from, today.AddDate(0, 0, -1))
	}
	if !to.Equal(today.AddDate(0, 0, 1)) {
		t.Fatalf("to=%v want tomorrow start %v", to, today.AddDate(0, 0, 1))
	}
}

func TestTehranStartOfDayMatchesLocalMidnight(t *testing.T) {
	cases := []struct {
		name     string
		utc      time.Time
		wantDate time.Time
	}{
		{
			name:     "after local midnight",
			utc:      time.Date(2026, 11, 26, 21, 0, 0, 0, time.UTC),
			wantDate: time.Date(2026, 11, 27, 0, 0, 0, 0, time.UTC),
		},
		{
			name:     "before local midnight",
			utc:      time.Date(2026, 11, 26, 20, 0, 0, 0, time.UTC),
			wantDate: time.Date(2026, 11, 26, 0, 0, 0, 0, time.UTC),
		},
	}
	for _, testCase := range cases {
		got := store.TehranStartOfDay(testCase.utc)
		want := testCase.wantDate.Add(-store.TehranOffset)
		if !got.Equal(want) {
			t.Fatalf("%s: start of day=%v want %v", testCase.name, got, want)
		}
	}
}
