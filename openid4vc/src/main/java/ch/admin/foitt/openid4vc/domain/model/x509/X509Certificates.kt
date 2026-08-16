package ch.admin.foitt.openid4vc.domain.model.x509

import ch.admin.foitt.openid4vc.domain.model.jwk.Jwk
import ch.admin.foitt.openid4vc.domain.model.jwt.Jwt
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.X509CertUtils
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey

private const val UriSubjectAlternativeName = 6
private const val DnsSubjectAlternativeName = 2
private const val DidPrefix = "did:"

fun Jwt.x5cLeafCertificate(): X509Certificate {
    val encodedLeafCertificate = checkNotNull(signedJwt.header.x509CertChain?.firstOrNull()) { "x5c header is missing" }
    return X509CertUtils.parseWithException(encodedLeafCertificate.decode()).also(X509Certificate::checkValidity)
}

fun X509Certificate.sha256Thumbprint(): String = X509CertUtils.computeSHA256Thumbprint(this).toString()

fun X509Certificate.toP256Jwk(): Jwk {
    val ecPublicKey = publicKey as? ECPublicKey ?: error("x5c certificate must contain an EC public key")
    val curve = Curve.forECParameterSpec(ecPublicKey.params)
    check(curve == Curve.P_256) { "x5c certificate must use P-256" }
    val key = ECKey.Builder(curve, ecPublicKey).build()
    return Jwk(
        x = key.x.toString(),
        y = key.y.toString(),
        crv = key.curve.name,
        kty = key.keyType.value,
    )
}

fun X509Certificate.didSubjectAlternativeName(): String? = subjectAlternativeNameValues(UriSubjectAlternativeName)
    .filter { it.startsWith(DidPrefix) }
    .singleOrNull()

fun X509Certificate.dnsSubjectAlternativeNames(): Set<String> =
    subjectAlternativeNameValues(DnsSubjectAlternativeName).toSet()

private fun X509Certificate.subjectAlternativeNameValues(type: Int): List<String> = subjectAlternativeNames.orEmpty()
    .mapNotNull { alternativeName ->
        (alternativeName.getOrNull(1) as? String)?.takeIf { alternativeName.getOrNull(0) == type }
    }
