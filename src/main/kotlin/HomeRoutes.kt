import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

//  1. Data class structurée pour la réponse du /home
@Serializable
data class HomeResponse(
    val name: String,
    val friends: List<String>
)

//  2. La route sécurisée
fun Route.homeRoute() {
    authenticate("auth-jwt") {
        get("/home") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()

            if (email == null) {
                call.respond(HttpStatusCode.Unauthorized, "Token invalide")
                return@get
            }

            val userData = transaction {
                val user = UsersTable.select { UsersTable.email eq email }.singleOrNull()
                val userId = user?.get(UsersTable.id)?.value

                if (user != null && userId != null) {
                    val name = user[UsersTable.name]

                    val friends = FriendsTable
                        .select {
                            ((FriendsTable.sender eq userId) or (FriendsTable.receiver eq userId)) and
                                    (FriendsTable.status eq "accepted")
                        }
                        .map {
                            val friendId =
                                if (it[FriendsTable.sender].value == userId) it[FriendsTable.receiver].value
                                else it[FriendsTable.sender].value

                            UsersTable.select { UsersTable.id eq friendId }.single()[UsersTable.name]
                        }

                    HomeResponse(name = name, friends = friends)
                } else null
            }

            if (userData == null) {
                call.respond(HttpStatusCode.NotFound, "User not found")
            } else {
                call.respond(HttpStatusCode.OK, userData)
            }
        }
    }
}

//  3. Fonction pour enregistrer la route
fun Application.configureHomeRoutes() {
    routing {
        homeRoute()
    }
}
