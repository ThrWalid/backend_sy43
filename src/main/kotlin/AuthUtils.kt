import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun ApplicationCall.getUserIdFromToken(): Int {
    val principal = this.principal<JWTPrincipal>()
    return principal?.payload?.getClaim("userId")?.asInt()
        ?: throw IllegalStateException("UserId not found in token")
}
