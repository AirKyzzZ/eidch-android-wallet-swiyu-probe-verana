package ch.admin.foitt.wallet.platform.veranaTrust.di

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ch.admin.foitt.wallet.platform.navigation.domain.model.Destination
import ch.admin.foitt.wallet.platform.navigation.domain.model.EntryProviderInstaller
import ch.admin.foitt.wallet.platform.scaffold.presentation.SyncedScaffoldScreen
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.VeranaTrustDetailsScreen
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.VeranaTrustDetailsViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(ActivityRetainedComponent::class)
object EntryProviderInstallerModule {
    @IntoSet
    @Provides
    fun provideEntryProviderInstaller(): EntryProviderInstaller = {
        entry<Destination.VeranaTrustDetailsScreen> { navKey ->
            val viewModel = hiltViewModel<VeranaTrustDetailsViewModel, VeranaTrustDetailsViewModel.Factory>(
                creationCallback = { factory ->
                    factory.create(evidence = navKey.evidence)
                }
            )
            SyncedScaffoldScreen(viewModel = viewModel) {
                VeranaTrustDetailsScreen(viewModel = viewModel)
            }
        }
    }
}
