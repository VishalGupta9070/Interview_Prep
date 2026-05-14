package com.vishal.interviewprepai.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vishal.interviewprepai.domain.model.Feature
import com.vishal.interviewprepai.domain.repository.FeatureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val features: List<Feature> = emptyList(),
)

class HomeViewModel(
    private val featureRepository: FeatureRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val items = featureRepository.getHomeFeatures()
            _state.value = HomeUiState(isLoading = false, features = items)
        }
    }
}

