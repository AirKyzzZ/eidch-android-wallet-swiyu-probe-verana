package ch.admin.foitt.wallet.platform.veranaTrust.presentation

import ch.admin.foitt.wallet.R
import ch.admin.foitt.wallet.platform.navigation.NavigationManager
import ch.admin.foitt.wallet.platform.scaffold.domain.model.TopBarState
import ch.admin.foitt.wallet.platform.scaffold.domain.usecase.SetTopBarState
import ch.admin.foitt.wallet.platform.scaffold.presentation.ScreenViewModel
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.adapter.mapVeranaTrustUiState
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustUiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel(assistedFactory = VeranaTrustDetailsViewModel.Factory::class)
class VeranaTrustDetailsViewModel @AssistedInject constructor(
    private val navigationManager: NavigationManager,
    setTopBarState: SetTopBarState,
    @Assisted evidence: VeranaTrustEvidence,
) : ScreenViewModel(setTopBarState) {
    @AssistedFactory
    interface Factory {
        fun create(evidence: VeranaTrustEvidence): VeranaTrustDetailsViewModel
    }

    override val topBarState = TopBarState.Details(
        onUp = ::onBack,
        titleId = R.string.verana_trust_details_title,
    )

    val uiState: VeranaTrustUiState = mapVeranaTrustUiState(evidence)

    fun onBack() {
        navigationManager.popBackStack()
    }
}
