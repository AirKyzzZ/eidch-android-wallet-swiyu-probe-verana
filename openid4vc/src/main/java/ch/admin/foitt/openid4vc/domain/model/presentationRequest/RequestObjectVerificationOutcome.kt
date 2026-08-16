package ch.admin.foitt.openid4vc.domain.model.presentationRequest

enum class RequestObjectVerificationOutcome {
    DID_PATH,
    X509_HASH_PATH,
    ATTESTATION_TRUSTED,
    ATTESTATION_UNTRUSTED,
}
