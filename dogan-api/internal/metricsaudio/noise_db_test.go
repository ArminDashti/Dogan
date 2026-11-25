package metricsaudio

import "testing"

func TestCabinNoiseRmsToDbTypicalLevels(t *testing.T) {
	quiet := CabinNoiseRmsToDb(100)
	loud := CabinNoiseRmsToDb(2500)
	if quiet < 35 || quiet > 55 {
		t.Fatalf("quiet = %f", quiet)
	}
	if loud < 65 || loud > 80 {
		t.Fatalf("loud = %f", loud)
	}
}