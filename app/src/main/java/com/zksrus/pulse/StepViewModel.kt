package com.zksrus.pulse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StepUiState(
    val stepData: StepData? = null,
    val weeklySteps: List<DailySteps> = emptyList(),
    val isLoading: Boolean = true,
    val isSignedIn: Boolean = false,
    val isDemoMode: Boolean = false,
    val error: String? = null
)

class StepViewModel(application: Application) : AndroidViewModel(application) {

    private val healthManager = HuaweiHealthManager(application)

    private val _uiState = MutableStateFlow(StepUiState())
    val uiState: StateFlow<StepUiState> = _uiState.asStateFlow()

    init {
        initializeHealthKit()
    }

    private fun initializeHealthKit() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val initResult = healthManager.initialize()
                initResult.onSuccess { signedIn ->
                    _uiState.value = _uiState.value.copy(isSignedIn = signedIn)
                    if (!signedIn) {
                        // HMS not available or sign-in needed, use demo mode
                        _uiState.value = _uiState.value.copy(isDemoMode = true)
                    }
                }.onFailure {
                    _uiState.value = _uiState.value.copy(isDemoMode = true)
                }
                loadTodaySteps()
                loadWeeklySteps()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isDemoMode = true
                )
                loadTodaySteps()
            }
        }
    }

    fun loadTodaySteps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            healthManager.getTodaySteps()
                .onSuccess { data ->
                    _uiState.value = _uiState.value.copy(
                        stepData = data,
                        isLoading = false,
                        isDemoMode = data.isDemo,
                        error = null
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
        }
    }

    fun loadWeeklySteps() {
        viewModelScope.launch {
            healthManager.getWeeklySteps()
                .onSuccess { weeklyData ->
                    _uiState.value = _uiState.value.copy(
                        weeklySteps = weeklyData
                    )
                }
                .onFailure {
                    // Keep existing weekly data on failure
                }
        }
    }

    fun refresh() {
        loadTodaySteps()
        loadWeeklySteps()
    }

    fun signOut() {
        healthManager.signOut()
        _uiState.value = StepUiState()
        initializeHealthKit()
    }
}
