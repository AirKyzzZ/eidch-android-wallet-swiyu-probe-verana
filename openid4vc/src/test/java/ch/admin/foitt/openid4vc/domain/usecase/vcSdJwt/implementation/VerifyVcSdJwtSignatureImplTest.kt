package ch.admin.foitt.openid4vc.domain.usecase.vcSdJwt.implementation

import ch.admin.foitt.openid4vc.domain.model.credentialoffer.metadata.CredentialFormat
import ch.admin.foitt.openid4vc.domain.model.jwk.Jwk
import ch.admin.foitt.openid4vc.domain.model.jwk.Jwks
import ch.admin.foitt.openid4vc.domain.model.jwt.JwtError
import ch.admin.foitt.openid4vc.domain.model.vcSdJwt.JwtVcIssuerMetadata
import ch.admin.foitt.openid4vc.domain.model.vcSdJwt.VcSdJwtError
import ch.admin.foitt.openid4vc.domain.repository.CredentialOfferRepository
import ch.admin.foitt.openid4vc.domain.usecase.jwt.VerifyJwtSignature
import ch.admin.foitt.openid4vc.domain.usecase.jwt.VerifyJwtSignatureFromDid
import ch.admin.foitt.openid4vc.util.assertErrorType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.X509CertUtils
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.security.interfaces.ECPublicKey
import java.util.Base64

class VerifyVcSdJwtSignatureImplTest {

    @MockK
    private lateinit var mockVerifyJwtSignatureFromDid: VerifyJwtSignatureFromDid

    @MockK
    private lateinit var mockCredentialOfferRepository: CredentialOfferRepository

    @MockK
    private lateinit var mockVerifyJwtSignature: VerifyJwtSignature

    private lateinit var useCase: VerifyVcSdJwtSignatureImpl

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        useCase = VerifyVcSdJwtSignatureImpl(
            verifyJwtSignatureFromDid = mockVerifyJwtSignatureFromDid,
            credentialOfferRepository = mockCredentialOfferRepository,
            verifyJwtSignature = mockVerifyJwtSignature,
        )

        coEvery {
            mockVerifyJwtSignatureFromDid(kid = any(), jwt = any())
        } returns Ok(Unit)
        coEvery { mockCredentialOfferRepository.fetchJwtVcIssuerMetadata(ISSUER_URL) } returns Ok(
            JwtVcIssuerMetadata(
                issuer = ISSUER_URL.toString(),
                jwks = Jwks(keys = listOf(matchingCertificateIssuerKey())),
            )
        )
        coEvery { mockVerifyJwtSignature(jwt = any(), publicKey = any()) } returns Ok(Unit)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Verifying a valid vc sd jwt succeeds`() = runTest {
        useCase(
            keyBinding = null,
            payload = VALID_JWT,
            format = CREDENTIAL_FORMAT,
        )
    }

    @Test
    fun `Verifying a vc sd jwt credential that contains non-selectively disclosable claims returns an error`() = runTest {
        useCase(
            keyBinding = null,
            payload = JWT_WITH_NON_SELECTIVELY_DISCLOSABLE_CLAIM,
            format = CREDENTIAL_FORMAT,
        ).assertErrorType(VcSdJwtError.InvalidVcSdJwt::class)
    }

    @Test
    fun `Verifying a vc sd jwt credential that does not contain a keyId returns an error`() = runTest {
        useCase(
            keyBinding = null,
            payload = JWT_WITHOUT_KID,
            format = CREDENTIAL_FORMAT,
        ).assertErrorType(VcSdJwtError.InvalidVcSdJwt::class)
    }

    @Test
    fun `Verifying an x5c credential bound to issuer metadata and a same-origin DID succeeds`() = runTest {
        useCase(
            keyBinding = null,
            payload = createX5cVcSdJwt(TEST_CERTIFICATE),
            format = CREDENTIAL_FORMAT,
        )

        coVerify(exactly = 1) { mockCredentialOfferRepository.fetchJwtVcIssuerMetadata(ISSUER_URL) }
        coVerify(exactly = 1) { mockVerifyJwtSignature(jwt = any(), publicKey = any()) }
    }

    @Test
    fun `Verifying an x5c credential with a certificate key absent from issuer metadata fails`() = runTest {
        coEvery { mockCredentialOfferRepository.fetchJwtVcIssuerMetadata(ISSUER_URL) } returns Ok(
            JwtVcIssuerMetadata(
                issuer = ISSUER_URL.toString(),
                jwks = Jwks(keys = listOf(matchingCertificateIssuerKey().copy(x = "different"))),
            )
        )

        useCase(
            keyBinding = null,
            payload = createX5cVcSdJwt(TEST_CERTIFICATE),
            format = CREDENTIAL_FORMAT,
        ).assertErrorType(VcSdJwtError.IssuerValidationFailed::class)

        coVerify(exactly = 0) { mockVerifyJwtSignature(jwt = any(), publicKey = any()) }
    }

    @Test
    fun `Verifying an x5c credential with mismatched issuer metadata fails`() = runTest {
        coEvery { mockCredentialOfferRepository.fetchJwtVcIssuerMetadata(ISSUER_URL) } returns Ok(
            JwtVcIssuerMetadata(
                issuer = "https://other.example",
                jwks = Jwks(keys = listOf(matchingCertificateIssuerKey())),
            )
        )

        useCase(
            keyBinding = null,
            payload = createX5cVcSdJwt(TEST_CERTIFICATE),
            format = CREDENTIAL_FORMAT,
        ).assertErrorType(VcSdJwtError.IssuerValidationFailed::class)

        coVerify(exactly = 0) { mockVerifyJwtSignature(jwt = any(), publicKey = any()) }
    }

    @Test
    fun `Verifying an x5c credential whose issuer is not the DID origin fails`() = runTest {
        useCase(
            keyBinding = null,
            payload = createX5cVcSdJwt(TEST_CERTIFICATE, issuer = "https://other.example"),
            format = CREDENTIAL_FORMAT,
        ).assertErrorType(VcSdJwtError.IssuerValidationFailed::class)

        coVerify(exactly = 0) { mockCredentialOfferRepository.fetchJwtVcIssuerMetadata(any()) }
        coVerify(exactly = 0) { mockVerifyJwtSignature(jwt = any(), publicKey = any()) }
    }

    @Test
    fun `Issuer metadata network errors for x5c credentials are mapped`() = runTest {
        coEvery { mockCredentialOfferRepository.fetchJwtVcIssuerMetadata(ISSUER_URL) } returns Err(VcSdJwtError.NetworkError)

        useCase(
            keyBinding = null,
            payload = createX5cVcSdJwt(TEST_CERTIFICATE),
            format = CREDENTIAL_FORMAT,
        ).assertErrorType(VcSdJwtError.NetworkError::class)
    }

    @Test
    fun `Error from the jwt signature verification are mapped`() = runTest {
        val exception = Exception("invalid signature")
        coEvery {
            mockVerifyJwtSignatureFromDid(kid = any(), jwt = any())
        } returns Err(JwtError.Unexpected(exception))

        val error = useCase(
            keyBinding = null,
            payload = VALID_JWT,
            format = CREDENTIAL_FORMAT,
        ).assertErrorType(VcSdJwtError.Unexpected::class)

        assertEquals(exception, error.cause)
    }

    private companion object {
        val CREDENTIAL_FORMAT = CredentialFormat.DC_SD_JWT
        const val X5C_ISSUER_DID = "did:web:issuer.example"
        val ISSUER_URL = URI.create("https://issuer.example").toURL()
        const val TEST_CERTIFICATE =
            "MIIBwTCCAWigAwIBAgIUGXH44CstBMukT4p9p/L0/SrRBwYwCgYIKoZIzj0EAwIwHDEaMBgGA1UEAwwRU1dJWVUgVGVzdCBJc3N1ZXIwHhcNMjYwNzE4MTU1MjIwWhcNMzYwNzE1MTU1MjIwWjAcMRowGAYDVQQDDBFTV0lZVSBUZXN0IElzc3VlcjBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABGQmspXYSAla/4TBbp14+TNRdwupVh4h06UxMmZlc2Za5jGJKw7xNlwhRRrJfUCXDRc757ZapFS9D3v3pviHQrWjgYcwgYQwHQYDVR0OBBYEFCn+jiJ71MIv22yjA1mI3uvoRVeoMB8GA1UdIwQYMBaAFCn+jiJ71MIv22yjA1mI3uvoRVeoMA8GA1UdEwEB/wQFMAMBAf8wMQYDVR0RBCowKIYWZGlkOndlYjppc3N1ZXIuZXhhbXBsZYIOaXNzdWVyLmV4YW1wbGUwCgYIKoZIzj0EAwIDRwAwRAIgL9u7oDmxVLlfXvCoOW+TZ+yTLhb2KFzDdwIrW/Wz/8gCIB25GnmO4u7ijFtdkSj5JrpRkwrNIVuEdv6VKcjVaQ38"
        const val VALID_JWT =
            "eyJhbGciOiJFUzI1NiIsInR5cCI6InR5cGUiLCJraWQiOiJrZXlJZCJ9.eyJpc3MiOiJpc3N1ZXIiLCJleHAiOjE5MjQ5ODgzOTksImlhdCI6MCwibmJmIjoxLCJ2Y3QiOiJ2Y3QifQ.xHItSO9jil0yiltXr0WFVGxiogOsihsfX0k5INgcoC9k4oP69yTM_mNqujBM5DB0_x__ZXQF9Sc_1GU__5wZdg~"
        const val JWT_WITH_NON_SELECTIVELY_DISCLOSABLE_CLAIM =
            "eyJhbGciOiJFUzI1NiIsInR5cCI6InR5cGUiLCJraWQiOiJrZXlJZCJ9.eyJpc3MiOiJpc3N1ZXIiLCJleHAiOjE5MjQ5ODgzOTksImlhdCI6MCwibmJmIjoxLCJ2Y3QiOiJ2Y3QiLCJvdGhlciI6ImNsYWltIn0.B0OsUc5CukjhaBChyDrLJdx8paChpV3ghZxDMtn7bY1JT3IQrLu1I1WapetEE9_XgRJn9exz9Ms_HGLT1pDh6g~"
        const val JWT_WITHOUT_KID =
            "eyJhbGciOiJFUzI1NiIsInR5cCI6InR5cGUifQ.eyJpc3MiOiJpc3N1ZXIiLCJleHAiOjE5MjQ5ODgzOTksImlhdCI6MCwibmJmIjoxLCJ2Y3QiOiJ2Y3QifQ.VarWJBHA1ABRVYJOKxB3Vg_PFc6iGuAtCx20XrwRRaULoSLHnmmdhu3RrS2nfMCzg6ZpiQ-3krCwAgsqFuX45A~"

        fun createX5cVcSdJwt(
            certificate: String,
            issuer: String = ISSUER_URL.toString(),
        ): String {
            val encoder = Base64.getUrlEncoder().withoutPadding()
            val header = encoder.encodeToString(
                """{"alg":"ES256","typ":"dc+sd-jwt","x5c":["$certificate"]}""".toByteArray()
            )
            val payload = encoder.encodeToString(
                """{"iss":"$issuer","vct":"vct"}""".toByteArray()
            )
            return "$header.$payload.c2lnbmF0dXJl~"
        }

        fun matchingCertificateIssuerKey(): Jwk {
            val certificate = X509CertUtils.parseWithException(Base64.getDecoder().decode(TEST_CERTIFICATE))
            val certificatePublicKey = certificate.publicKey as ECPublicKey
            val publicKey = ECKey.Builder(com.nimbusds.jose.jwk.Curve.P_256, certificatePublicKey).build()
            return Jwk(
                alg = "ES256",
                kid = "$X5C_ISSUER_DID#key",
                kty = publicKey.keyType.value,
                crv = publicKey.curve.name,
                x = publicKey.x.toString(),
                y = publicKey.y.toString(),
            )
        }
    }
}
