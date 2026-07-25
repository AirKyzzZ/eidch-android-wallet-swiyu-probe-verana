package ch.admin.foitt.wallet.platform.veranaTrust.di

import ch.admin.foitt.wallet.platform.veranaTrust.data.VeranaTrustResolverRepositoryImpl
import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VeranaTrustResolverRepository
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.EvaluateVeranaTrust
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.FetchVeranaTrustDetails
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation.EvaluateVeranaTrustImpl
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation.FetchVeranaTrustDetailsImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped

@Module
@InstallIn(ActivityRetainedComponent::class)
internal interface VeranaTrustModule {
    @Binds
    fun bindEvaluateVeranaTrust(useCase: EvaluateVeranaTrustImpl): EvaluateVeranaTrust

    @Binds
    fun bindFetchVeranaTrustDetails(useCase: FetchVeranaTrustDetailsImpl): FetchVeranaTrustDetails

    @Binds
    @ActivityRetainedScoped
    fun bindVeranaTrustResolverRepository(
        repository: VeranaTrustResolverRepositoryImpl,
    ): VeranaTrustResolverRepository
}
