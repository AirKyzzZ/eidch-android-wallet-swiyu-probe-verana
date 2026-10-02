package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation

import ch.admin.foitt.openid4vc.domain.model.jwk.Jwk
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAccreditation
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaDidKeyBinding
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustResolution
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustResolution.Companion.unresolved
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustStatus
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaUntrustedReason
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.CheckVeranaAccreditation
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.EvaluateVeranaTrust
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.ResolveVeranaTrust
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.VerifyVeranaDidKeyBinding
import ch.admin.foitt.wallet.platform.veranaTrust.domain.util.VeranaDids
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import javax.inject.Inject

class EvaluateVeranaTrustImpl @Inject constructor(
    private val verifyVeranaDidKeyBinding: VerifyVeranaDidKeyBinding,
    private val resolveVeranaTrust: ResolveVeranaTrust,
    private val checkVeranaAccreditation: CheckVeranaAccreditation,
) : EvaluateVeranaTrust {
    override suspend fun invoke(
        role: VeranaTrustRole,
        did: String,
        vct: String?,
        certificateKey: Jwk?,
    ): VeranaTrustEvidence {
        fun evidence(resolution: VeranaTrustResolution, accreditation: VeranaAccreditation?) = VeranaTrustEvidence(
            role = role,
            did = did,
            vct = vct,
            resolution = resolution,
            accreditation = accreditation,
        )

        return try {
            val binding = if (certificateKey != null && VeranaDids.hasDidDocument(did)) {
                verifyVeranaDidKeyBinding(did = did, role = role, certificateKey = certificateKey)
            } else {
                VeranaDidKeyBinding.PROVEN
            }
            when (binding) {
                VeranaDidKeyBinding.NOT_PROVEN -> evidence(
                    resolution = unresolved(did, VeranaTrustStatus.UNTRUSTED, VeranaUntrustedReason.DID_NOT_PROVEN),
                    accreditation = null,
                )

                VeranaDidKeyBinding.UNAVAILABLE -> evidence(
                    resolution = unresolved(did, VeranaTrustStatus.UNVERIFIED),
                    accreditation = null,
                )

                VeranaDidKeyBinding.PROVEN -> coroutineScope {
                    val resolution = async { resolveVeranaTrust(did) }
                    val accreditation = async { checkVeranaAccreditation(did = did, role = role, vct = vct) }
                    evidence(resolution = resolution.await(), accreditation = accreditation.await())
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Timber.w(error, "Verana trust evaluation failed")
            evidence(resolution = unresolved(did, VeranaTrustStatus.UNVERIFIED), accreditation = null)
        }
    }
}
