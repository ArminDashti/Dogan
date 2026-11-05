package com.dogan

/** Dispatches motion and audio events to the active operating mode engine(s). */
class ModeController(
    private val watcherEngine: WatcherEngine,
) {
    fun onBump(settings: AppSettings, peakMps2: Float) {
        if (settings.operatingMode == OperatingMode.WATCHER ||
            settings.operatingMode == OperatingMode.SPOTTER
        ) {
            watcherEngine.onBump(settings, peakMps2)
        }
    }

    fun onSoundSpike(rms: Double, settings: AppSettings) {
        if (settings.operatingMode == OperatingMode.WATCHER ||
            settings.operatingMode == OperatingMode.SPOTTER
        ) {
            watcherEngine.onSoundSpike(rms, settings)
        }
    }
}
