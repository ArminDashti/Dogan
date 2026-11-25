package metricsaudio

import "math"

func CabinNoiseRmsToDb(rms float64) float64 {
	if rms <= 0 {
		return 0
	}
	const fullScale = 32768.0
	const offsetDb = 94.0
	return 20*math.Log10(rms/fullScale) + offsetDb
}