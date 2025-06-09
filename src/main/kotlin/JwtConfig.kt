import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.*
import io.ktor.server.auth.jwt.JWTPrincipal

object JwtConfig {

    private const val secret = "super_secret_key" // ⚠️
    private const val issuer = "ktor.io"
    private const val audience = "ktor_audience"
    private const val validityInMs = 36_000_00 * 24 // 24h

    private val algorithm = Algorithm.HMAC256(secret)

    fun generateToken(email: String): String = JWT.create()
        .withSubject("Authentication")
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("email", email)
        .withExpiresAt(Date(System.currentTimeMillis() + validityInMs))
        .sign(algorithm)

    fun configureKtorFeature(config: io.ktor.server.auth.jwt.JWTAuthenticationProvider.Config) {
        config.verifier(
            JWT
                .require(algorithm)
                .withIssuer(issuer)
                .withAudience(audience)
                .build()
        )
        config.validate { credential ->
            if (credential.payload.getClaim("email").asString().isNotEmpty()) JWTPrincipal(credential.payload) else null
        }
    }
}
