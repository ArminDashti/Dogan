package com.dogan

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Focused settings screen for one section; auto-saves and pushes to server. */
class SettingsActivity : AppCompatActivity() {
    private val settingsStore by lazy { SettingsStore(this) }

    private lateinit var section: String
    private var suppressAutoSave = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        section = intent.getStringExtra(EXTRA_SECTION) ?: SECTION_GENERAL

        findViewById<MaterialToolbar>(R.id.settingsToolbar).apply {
            title = sectionTitle(section)
            setNavigationOnClickListener { finish() }
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        }

        val content = findViewById<FrameLayout>(R.id.settingsContent)
        val layoutId = when (section) {
            SECTION_CONNECTIVITY -> R.layout.section_connectivity
            SECTION_COPILOT -> R.layout.section_copilot
            SECTION_WATCHMAN -> R.layout.section_watchman
            else -> R.layout.section_general
        }
        LayoutInflater.from(this).inflate(layoutId, content, true)

        lifecycleScope.launch {
            val settings = settingsStore.settingsFlow.first()
            suppressAutoSave = true
            bindSection(settings)
            suppressAutoSave = false
        }
    }

    private fun sectionTitle(section: String): String = when (section) {
        SECTION_CONNECTIVITY -> getString(R.string.settings_section_connectivity)
        SECTION_COPILOT -> getString(R.string.settings_section_copilot)
        SECTION_WATCHMAN -> getString(R.string.settings_section_watchman)
        else -> getString(R.string.settings_title)
    }

    private fun bindSection(settings: AppSettings) {
        when (section) {
            SECTION_CONNECTIVITY -> bindConnectivity(settings)
            SECTION_COPILOT -> bindCopilot(settings)
            SECTION_WATCHMAN -> bindWatchman(settings)
            else -> bindGeneral(settings)
        }
    }

    private fun bindConnectivity(settings: AppSettings) {
        val connectionBtn = findViewById<MaterialButton>(R.id.connectionBtn)
        val pingStatusTxt = findViewById<TextView>(R.id.pingStatusTxt)
        val apiStatusTxt = findViewById<TextView>(R.id.apiStatusTxt)
        val liveKitStatusTxt = findViewById<TextView>(R.id.liveKitStatusTxt)
        val connectionErrorTxt = findViewById<TextView>(R.id.connectionErrorTxt)
        val username = findViewById<TextInputEditText>(R.id.usernameInput)
        val password = findViewById<TextInputEditText>(R.id.passwordInput)
        val apiUrl = findViewById<TextInputEditText>(R.id.apiUrlInput)
        val streamUrl = findViewById<TextInputEditText>(R.id.streamUrlInput)

        username.setText(settings.username)
        password.setText(settings.password)
        apiUrl.setText(formatHostPort(settings.apiEndpoint, settings.apiPort))
        streamUrl.setText(formatHostPort(settings.streamEndpoint, settings.streamPort))

        fun refreshStatusUi() {
            updateConnectionButton(connectionBtn, ServerConnectionManager.status.value)
            updateConnectivityStatusPanel(pingStatusTxt, apiStatusTxt, liveKitStatusTxt, connectionErrorTxt)
        }

        connectionBtn.setOnClickListener { toggleConnection() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServerConnectionManager.status.collect { refreshStatusUi() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PingLatencyCache.latencyMs.collect { refreshStatusUi() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                LiveKitStatusCache.streaming.collect { refreshStatusUi() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                LiveKitStatusCache.activeSessionCount.collect { refreshStatusUi() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    if (ServerConnectionManager.isConnected()) {
                        val current = settingsStore.settingsFlow.first()
                        ServerConnectionManager.refreshPing(this@SettingsActivity, current)
                        ServerConnectionManager.refreshLiveKitConnections(this@SettingsActivity, current)
                    }
                    delay(30_000L)
                }
            }
        }
        refreshStatusUi()

        bindText(username) { text ->
            persist(mapOf("username" to text)) { it.copy(username = text) }
        }
        bindText(password) { text ->
            persist(mapOf("password" to text)) { it.copy(password = text) }
        }
        bindText(apiUrl) { text ->
            val parsed = parseHostPort(text, SettingsStore.DEFAULT_API_ENDPOINT, SettingsStore.DEFAULT_API_PORT)
            persist(
                mapOf("api_endpoint" to parsed.host, "api_port" to parsed.port),
            ) { it.copy(apiEndpoint = parsed.host, apiPort = parsed.port) }
        }
        bindText(streamUrl) { text ->
            val parsed = parseHostPort(text, SettingsStore.DEFAULT_STREAM_ENDPOINT, SettingsStore.DEFAULT_STREAM_PORT)
            persist(
                mapOf("stream_endpoint" to parsed.host, "stream_port" to parsed.port),
            ) { it.copy(streamEndpoint = parsed.host, streamPort = parsed.port) }
        }
    }

    private fun updateConnectivityStatusPanel(
        pingStatusTxt: TextView,
        apiStatusTxt: TextView,
        liveKitStatusTxt: TextView,
        connectionErrorTxt: TextView,
    ) {
        val latency = PingLatencyCache.latencyMs.value
        pingStatusTxt.text = if (latency >= 0) {
            getString(R.string.ping_status_ms, latency)
        } else {
            getString(R.string.ping_status_unknown)
        }
        val apiConnected = ServerConnectionManager.isConnected()
        apiStatusTxt.text = getString(
            R.string.api_connection_status,
            getString(if (apiConnected) R.string.connected_label else R.string.disconnected_label),
        )
        val liveKitConnected = LiveKitStatusCache.isConnected
        val sessions = LiveKitStatusCache.activeSessionCount.value
        liveKitStatusTxt.text = if (sessions > 0) {
            getString(
                R.string.livekit_connection_status_sessions,
                getString(if (liveKitConnected) R.string.connected_label else R.string.disconnected_label),
                sessions,
            )
        } else {
            getString(
                R.string.livekit_connection_status,
                getString(if (liveKitConnected) R.string.connected_label else R.string.disconnected_label),
            )
        }
        val error = ServerConnectionManager.lastError ?: LiveKitStatusCache.lastError.value
        if (error.isNullOrBlank()) {
            connectionErrorTxt.visibility = View.GONE
            connectionErrorTxt.text = ""
        } else {
            connectionErrorTxt.visibility = View.VISIBLE
            connectionErrorTxt.text = error
        }
    }

    private fun bindCopilot(settings: AppSettings) {
        val alert = findViewById<SwitchMaterial>(R.id.copilotAlertSwitch)
        val distance = findViewById<SwitchMaterial>(R.id.copilotDistanceSwitch)
        val alertDuration = findViewById<AutoCompleteTextView>(R.id.alertDurationInput)

        alert.isChecked = settings.copilotAlertsEnabled
        distance.isChecked = settings.copilotDistanceControlEnabled
        setupDropdown(alertDuration, AlertDuration.all.map { it.displayName })
        alertDuration.setText(settings.alertDuration.displayName, false)

        alert.setOnCheckedChangeListener { _, checked ->
            if (suppressAutoSave) return@setOnCheckedChangeListener
            persist(mapOf("copilot_alerts_enabled" to checked)) { it.copy(copilotAlertsEnabled = checked) }
        }
        distance.setOnCheckedChangeListener { _, checked ->
            if (suppressAutoSave) return@setOnCheckedChangeListener
            persist(mapOf("copilot_distance_control_enabled" to checked)) {
                it.copy(copilotDistanceControlEnabled = checked)
            }
        }
        alertDuration.setOnItemClickListener { _, _, position, _ ->
            val duration = AlertDuration.all.getOrElse(position) { AlertDuration.THREE }
            persist(mapOf("alert_duration" to duration.toStoredValue())) { it.copy(alertDuration = duration) }
        }
    }

    private fun bindWatchman(settings: AppSettings) {
        val jolt = findViewById<AutoCompleteTextView>(R.id.joltSensitivityInput)
        val sound = findViewById<AutoCompleteTextView>(R.id.soundSensitivityInput)
        val customJoltLayout = findViewById<View>(R.id.customJoltScaleLayout)
        val customSoundLayout = findViewById<View>(R.id.customSoundThresholdLayout)
        val customJoltInput = findViewById<TextInputEditText>(R.id.customJoltScaleInput)
        val customSoundInput = findViewById<TextInputEditText>(R.id.customSoundThresholdInput)

        setupDropdown(jolt, SensitivityLevel.all.map { it.displayName })
        jolt.setText(settings.joltSensitivity.displayName, false)
        setupDropdown(sound, SensitivityLevel.all.map { it.displayName })
        sound.setText(settings.soundSensitivity.displayName, false)
        customJoltInput.setText(settings.customJoltScale.toString())
        customSoundInput.setText(settings.customSoundThreshold.toString())
        fun refreshCustomVisibility() {
            customJoltLayout.visibility =
                if (settings.joltSensitivity == SensitivityLevel.CUSTOM ||
                    jolt.text.toString() == SensitivityLevel.CUSTOM.displayName
                ) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            customSoundLayout.visibility =
                if (settings.soundSensitivity == SensitivityLevel.CUSTOM ||
                    sound.text.toString() == SensitivityLevel.CUSTOM.displayName
                ) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }
        refreshCustomVisibility()

        jolt.setOnItemClickListener { _, _, position, _ ->
            val level = SensitivityLevel.all.getOrElse(position) { SensitivityLevel.MEDIUM }
            customJoltLayout.visibility = if (level == SensitivityLevel.CUSTOM) View.VISIBLE else View.GONE
            persist(mapOf("jolt_sensitivity" to level.toStoredValue())) { it.copy(joltSensitivity = level) }
        }
        sound.setOnItemClickListener { _, _, position, _ ->
            val level = SensitivityLevel.all.getOrElse(position) { SensitivityLevel.MEDIUM }
            customSoundLayout.visibility = if (level == SensitivityLevel.CUSTOM) View.VISIBLE else View.GONE
            persist(mapOf("sound_sensitivity" to level.toStoredValue())) { it.copy(soundSensitivity = level) }
        }
        bindText(customJoltInput) { text ->
            val value = SettingsStore.normalizeCustomJoltScale(
                text.toFloatOrNull() ?: SettingsStore.DEFAULT_CUSTOM_JOLT_SCALE,
            )
            persist(mapOf("custom_jolt_scale" to value)) { it.copy(customJoltScale = value) }
        }
        bindText(customSoundInput) { text ->
            val value = SettingsStore.normalizeCustomSoundThreshold(
                text.toDoubleOrNull() ?: SettingsStore.DEFAULT_CUSTOM_SOUND_THRESHOLD,
            )
            persist(mapOf("custom_sound_threshold" to value)) { it.copy(customSoundThreshold = value) }
        }
    }

    private fun bindGeneral(settings: AppSettings) {
        val sync = findViewById<TextInputEditText>(R.id.syncIntervalInput)
        val keepAlive = findViewById<TextInputEditText>(R.id.screenOnIntervalInput)

        sync.setText(settings.settingsSyncIntervalSec.toString())
        keepAlive.setText(settings.screenOnIntervalMin.toString())

        bindText(sync) { text ->
            val value = SettingsStore.normalizeSettingsSyncIntervalSec(
                text.toIntOrNull() ?: SettingsStore.DEFAULT_SETTINGS_SYNC_INTERVAL_SEC,
            )
            persist(mapOf("settings_sync_interval_sec" to value)) { it.copy(settingsSyncIntervalSec = value) }
        }
        bindText(keepAlive) { text ->
            val value = SettingsStore.normalizeScreenOnIntervalMin(
                text.toIntOrNull() ?: SettingsStore.DEFAULT_SCREEN_ON_INTERVAL_MIN,
            )
            persist(mapOf("screen_on_interval_min" to value)) { it.copy(screenOnIntervalMin = value) }
        }
    }

    private fun setupDropdown(view: AutoCompleteTextView, labels: List<String>) {
        view.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels))
    }

    private var textJob: Job? = null

    private fun bindText(edit: TextInputEditText, onCommit: (String) -> Unit) {
        edit.doAfterTextChanged { editable ->
            if (suppressAutoSave) return@doAfterTextChanged
            textJob?.cancel()
            textJob = lifecycleScope.launch {
                delay(400)
                onCommit(editable?.toString().orEmpty())
            }
        }
    }

    private fun persist(keys: Map<String, Any?>, mutate: (AppSettings) -> AppSettings) {
        if (suppressAutoSave) return
        lifecycleScope.launch {
            SettingsPublisher.saveAndPush(this@SettingsActivity, mutate, keys)
        }
    }

    private fun toggleConnection() {
        lifecycleScope.launch {
            val settings = settingsStore.settingsFlow.first()
            if (ServerConnectionManager.isConnected()) {
                ServerConnectionManager.disconnect()
                return@launch
            }
            val connected = withContext(Dispatchers.IO) {
                ServerConnectionManager.connect(this@SettingsActivity, settings)
            }
            val error = ServerConnectionManager.lastError
            if (connected) {
                val message = if (error.isNullOrBlank()) {
                    getString(R.string.server_connected)
                } else {
                    getString(R.string.server_connected_with_warning, error)
                }
                Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    this@SettingsActivity,
                    error ?: getString(R.string.server_connect_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun updateConnectionButton(button: MaterialButton, status: ConnectionStatus) {
        val connected = status == ConnectionStatus.CONNECTED
        button.text = getString(if (connected) R.string.connected_label else R.string.disconnected_label)
        button.setBackgroundColor(
            Color.parseColor(if (connected) "#2E7D32" else "#C62828"),
        )
    }

    private fun formatHostPort(host: String, port: Int): String = "$host:$port"

    private fun parseHostPort(raw: String, defaultHost: String, defaultPort: Int): HostPort {
        val trimmed = raw.trim().removePrefix("http://").removePrefix("https://")
        if (trimmed.isEmpty()) return HostPort(defaultHost, defaultPort)
        val slash = trimmed.indexOf('/')
        val hostPortPart = if (slash >= 0) trimmed.substring(0, slash) else trimmed
        val colon = hostPortPart.lastIndexOf(':')
        if (colon <= 0) {
            return HostPort(hostPortPart.ifBlank { defaultHost }, defaultPort)
        }
        val host = hostPortPart.substring(0, colon).ifBlank { defaultHost }
        val port = hostPortPart.substring(colon + 1).toIntOrNull() ?: defaultPort
        return HostPort(host, port)
    }

    private data class HostPort(val host: String, val port: Int)

    companion object {
        const val EXTRA_SECTION = "section"
        const val SECTION_CONNECTIVITY = "connectivity"
        const val SECTION_COPILOT = "copilot"
        const val SECTION_WATCHMAN = "watchman"
        const val SECTION_GENERAL = "general"
    }
}
