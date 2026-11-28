package store

import "time"

// Iran has observed UTC+03:30 year-round since it dropped daylight saving in 2022.
const TehranOffset = 3*time.Hour + 30*time.Minute

// TehranStartOfDay returns the UTC instant of local midnight in Tehran for the
// day that contains at.
func TehranStartOfDay(at time.Time) time.Time {
	local := at.UTC().Add(TehranOffset)
	startLocal := time.Date(local.Year(), local.Month(), local.Day(), 0, 0, 0, 0, time.UTC)
	return startLocal.Add(-TehranOffset)
}

// TehranStartOfYesterday returns the UTC instant of yesterday's local midnight
// in Tehran, which is the oldest reading the app keeps.
func TehranStartOfYesterday(at time.Time) time.Time {
	return TehranStartOfDay(at).AddDate(0, 0, -1)
}
