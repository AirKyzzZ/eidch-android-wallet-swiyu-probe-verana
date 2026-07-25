package ch.admin.foitt.wallet.platform.veranaTrust.presentation

import androidx.lifecycle.viewModelScope
import ch.admin.foitt.wallet.R
import ch.admin.foitt.wallet.platform.navigation.NavigationManager
import ch.admin.foitt.wallet.platform.scaffold.domain.model.TopBarState
import ch.admin.foitt.wallet.platform.scaffold.domain.usecase.SetTopBarState
import ch.admin.foitt.wallet.platform.scaffold.presentation.ScreenViewModel
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaResolverResult
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.FetchVeranaTrustDetails
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.adapter.mapVeranaTrustUiState
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustDetailsLoadState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = VeranaTrustDetailsViewModel.Factory::class)
class VeranaTrustDetailsViewModel @AssistedInject constructor(
    private val fetchVeranaTrustDetails: FetchVeranaTrustDetails,
    private val navigationManager: NavigationManager,
    setTopBarState: SetTopBarState,
    @Assisted private val evidence: VeranaTrustEvidence,
) : ScreenViewModel(setTopBarState) {
    @AssistedFactory
    interface Factory {
        fun create(evidence: VeranaTrustEvidence): VeranaTrustDetailsViewModel
    }

    override val topBarState = TopBarState.Details(
        onUp = ::onBack,
        titleId = R.string.verana_trust_details_title,
    )

    private val summaryState = mapVeranaTrustUiState(evidence)
    private val _uiState = MutableStateFlow<VeranaTrustDetailsLoadState>(
        VeranaTrustDetailsLoadState.Loading(summaryState)
    )
    val uiState = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    fun onRetry() {
        loadDetails()
    }

    fun onBack() {
        navigationManager.popBackStack()
    }

    private fun loadDetails() {
        _uiState.value = VeranaTrustDetailsLoadState.Loading(summaryState)
        viewModelScope.launch {
            _uiState.value = when (val result = fetchVeranaTrustDetails(evidence)) {
                is VeranaResolverResult.Success -> {
                    if (result.value.summary == evidence.summary) {
                        VeranaTrustDetailsLoadState.Loaded(
                            mapVeranaTrustUiState(evidence, result.value)
                        )
                    } else {
                        VeranaTrustDetailsLoadState.Unavailable(summaryState)
                    }
                }

                VeranaResolverResult.NotFound,
                VeranaResolverResult.Unavailable -> VeranaTrustDetailsLoadState.Unavailable(summaryState)
            }
        }
    }
}
