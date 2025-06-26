import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.auth.jwt.JWTAuthenticationProvider
import io.ktor.server.auth.jwt.JWTPrincipal
import java.util.*

object JwtConfig {

    private const val secret = "super_secret_key"
    private const val issuer = "ktor.io"
    private const val audience = "ktor_audience"
    private const val validityInMs = 36_000_00 * 24 // 24 hours

    private val algorithm = Algorithm.HMAC256(secret)

    // Generate JWT token
    fun generateToken(userId: Int, email: String): String = JWT.create()
        .withSubject("Authentication")
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", userId) // Integer userId
        .withClaim("email", email)   // String email
        .withExpiresAt(Date(System.currentTimeMillis() + validityInMs)) //  Expiration 24h
        .sign(algorithm)

    //  Configure JWT verifier for Ktor
    fun configureKtorFeature(config: JWTAuthenticationProvider.Config) {
        config.verifier(
            JWT.require(algorithm)
                .withIssuer(issuer)
                .withAudience(audience)
                .build()
        )
        config.validate { credential ->
            val userId = credential.payload.getClaim("userId").asInt()
            if (userId != null) JWTPrincipal(credential.payload) else null
        }
    }
}
