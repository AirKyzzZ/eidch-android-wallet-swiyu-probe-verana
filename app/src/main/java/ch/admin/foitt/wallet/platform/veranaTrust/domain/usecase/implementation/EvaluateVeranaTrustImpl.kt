package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAuthorizationEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaResolverResult
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustSummary
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustVerdict
import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VeranaTrustResolverRepository
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.EvaluateVeranaTrust
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

class EvaluateVeranaTrustImpl @Inject constructor(
    private val repository: VeranaTrustResolverRepository,
) : EvaluateVeranaTrust {

    override suspend fun invoke(
        role: VeranaTrustRole,
        did: String,
        vcSchemaIds: Set<String>,
    ): VeranaTrustEvidence {
        val sortedSchemaIds = vcSchemaIds.sorted()
        val emptyEvidence = VeranaTrustEvidence(
            role = role,
            did = did,
            vcSchemaIds = sortedSchemaIds,
            verdict = VeranaTrustVerdict.UNTRUSTED,
            summary = null,
            authorizations = emptyList(),
        )

        val hasValidDid = DID_REGEX.matches(did)
        val hasValidSchemas = sortedSchemaIds.isNotEmpty() && sortedSchemaIds.none { schemaId ->
            schemaId.isBlank() || schemaId != schemaId.trim()
        }
        if (!hasValidDid || !hasValidSchemas) {
            return emptyEvidence
        }

        return try {
            withTimeout(EVALUATION_TIMEOUT_MILLIS) {
                evaluate(
                    emptyEvidence = emptyEvidence,
                    role = role,
                    did = did,
                    sortedSchemaIds = sortedSchemaIds,
                )
            }
        } catch (_: TimeoutCancellationException) {
            emptyEvidence.copy(verdict = VeranaTrustVerdict.RESOLVER_UNAVAILABLE)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            emptyEvidence.copy(verdict = VeranaTrustVerdict.RESOLVER_UNAVAILABLE)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun evaluate(
        emptyEvidence: VeranaTrustEvidence,
        role: VeranaTrustRole,
        did: String,
        sortedSchemaIds: List<String>,
    ): VeranaTrustEvidence {
        val summary = when (val result = repository.fetchSummary(did)) {
            is VeranaResolverResult.Success -> result.value
            VeranaResolverResult.NotFound -> return emptyEvidence
            VeranaResolverResult.Unavailable -> {
                return emptyEvidence.copy(verdict = VeranaTrustVerdict.RESOLVER_UNAVAILABLE)
            }
        }

        val evidenceWithSummary = emptyEvidence.copy(summary = summary)
        if (!summary.isTrustedProductionDid(did)) {
            return evidenceWithSummary
        }

        val authorizations = mutableListOf<VeranaAuthorizationEvidence>()
        for (schemaId in sortedSchemaIds) {
            val authorization = when (
                val result = repository.fetchAuthorization(
                    role = role,
                    did = did,
                    vcSchemaId = schemaId,
                )
            ) {
                is VeranaResolverResult.Success -> result.value
                VeranaResolverResult.NotFound,
                VeranaResolverResult.Unavailable -> {
                    return evidenceWithSummary.copy(
                        verdict = VeranaTrustVerdict.RESOLVER_UNAVAILABLE,
                        authorizations = authorizations,
                    )
                }
            }

            if (authorization.did != did || authorization.vcSchemaId != schemaId) {
                return evidenceWithSummary.copy(
                    verdict = VeranaTrustVerdict.RESOLVER_UNAVAILABLE,
                    authorizations = authorizations,
                )
            }
            authorizations += authorization
        }

        val verdict = if (authorizations.all { it.authorized }) {
            VeranaTrustVerdict.TRUSTED_AUTHORIZED
        } else {
            VeranaTrustVerdict.TRUSTED_NOT_AUTHORIZED
        }
        return evidenceWithSummary.copy(
            verdict = verdict,
            authorizations = authorizations,
        )
    }

    private fun VeranaTrustSummary.isTrustedProductionDid(expectedDid: String): Boolean =
        did == expectedDid && trustStatus == TRUSTED_STATUS && production

    private companion object {
        const val TRUSTED_STATUS = "TRUSTED"
        const val EVALUATION_TIMEOUT_MILLIS = 10_000L
        val DID_REGEX = """^did:[a-z0-9]+:(?:(?:[A-Za-z0-9._-]|%[0-9A-Fa-f]{2})*:)*(?:[A-Za-z0-9._-]|%[0-9A-Fa-f]{2})+$"""
            .toRegex()
    }
}
