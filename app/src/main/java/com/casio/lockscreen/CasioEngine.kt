package com.casio.lockscreen

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

enum class Mode { TIME, STOPWATCH, ALARM_SET }

data class CasioState(
    val mode: Mode = Mode.TIME,
    val timeHour: Int = 0,
    val timeMinute: Int = 0,
    val timeSecond: Int = 0,
    val dayOfWeek: String = "MON",
    val dateDay: Int = 1,
    val dateMonth: Int = 1,
    val stopwatchRunning: Boolean = false,
    val stopwatchElapsedMs: Long = 0L,
    val alarmHour: Int = 7,
    val alarmMinute: Int = 0,
    val alarmEnabled: Boolean = false,
    val is24Hour: Boolean = true,
    val aodMode: Boolean = false
)

private val DAY_NAMES = arrayOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")

class CasioEngine {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _state = MutableStateFlow(CasioState())
    val state: StateFlow<CasioState> = _state.asStateFlow()

    private var tickJob: Job? = null
    private var stopwatchStartMs: Long = 0L
    private var stopwatchAccumulatedMs: Long = 0L

    init {
        startTicker()
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                tick()
                val interval = if (_state.value.aodMode) 5_000L else 1_000L
                delay(interval)
            }
        }
    }

    private fun tick() {
        val cal = Calendar.getInstance()
        val current = _state.value

        val stopwatchElapsed = if (current.stopwatchRunning) {
            stopwatchAccumulatedMs + (System.currentTimeMillis() - stopwatchStartMs)
        } else {
            stopwatchAccumulatedMs
        }

        _state.value = current.copy(
            timeHour = cal.get(Calendar.HOUR_OF_DAY),
            timeMinute = cal.get(Calendar.MINUTE),
            timeSecond = cal.get(Calendar.SECOND),
            dayOfWeek = DAY_NAMES[cal.get(Calendar.DAY_OF_WEEK) - 1],
            dateDay = cal.get(Calendar.DAY_OF_MONTH),
            dateMonth = cal.get(Calendar.MONTH) + 1,
            stopwatchElapsedMs = stopwatchElapsed
        )
    }

    fun setAodMode(aod: Boolean) {
        _state.value = _state.value.copy(aodMode = aod)
        // Restart ticker with the new interval
        startTicker()
    }

    fun onLeftTap() {
        val next = when (_state.value.mode) {
            Mode.TIME -> Mode.STOPWATCH
            Mode.STOPWATCH -> Mode.ALARM_SET
            Mode.ALARM_SET -> Mode.TIME
        }
        _state.value = _state.value.copy(mode = next)
    }

    fun onRightTap() {
        val current = _state.value
        when (current.mode) {
            Mode.TIME -> {
                _state.value = current.copy(is24Hour = !current.is24Hour)
            }
            Mode.STOPWATCH -> {
                if (current.stopwatchRunning) {
                    stopwatchAccumulatedMs += System.currentTimeMillis() - stopwatchStartMs
                    _state.value = current.copy(stopwatchRunning = false)
                } else if (stopwatchAccumulatedMs > 0L) {
                    // Reset
                    stopwatchAccumulatedMs = 0L
                    _state.value = current.copy(stopwatchRunning = false, stopwatchElapsedMs = 0L)
                } else {
                    stopwatchStartMs = System.currentTimeMillis()
                    _state.value = current.copy(stopwatchRunning = true)
                }
            }
            Mode.ALARM_SET -> {
                val newHour = (current.alarmHour + 1) % 24
                _state.value = current.copy(alarmHour = newHour)
            }
        }
    }

    fun onMiddleTap() {
        val current = _state.value
        when (current.mode) {
            Mode.STOPWATCH -> {
                if (!current.stopwatchRunning && stopwatchAccumulatedMs > 0L) {
                    // Start from where it was stopped
                    stopwatchStartMs = System.currentTimeMillis()
                    _state.value = current.copy(stopwatchRunning = true)
                }
            }
            Mode.ALARM_SET -> {
                val newMinute = (current.alarmMinute + 1) % 60
                _state.value = current.copy(alarmMinute = newMinute)
            }
            Mode.TIME -> {}
        }
    }

    fun toggleAlarm() {
        val current = _state.value
        _state.value = current.copy(alarmEnabled = !current.alarmEnabled)
    }

    fun destroy() {
        tickJob?.cancel()
    }
}
