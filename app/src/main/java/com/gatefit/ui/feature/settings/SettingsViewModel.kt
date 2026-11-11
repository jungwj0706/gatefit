package com.gatefit.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatefit.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<com.gatefit.domain.repository.AppSettings> = settings.settings

    fun setDemoDataEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.update { current -> current.copy(demoDataEnabled = enabled) }
        }
    }
}
