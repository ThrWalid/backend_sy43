import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal // ✅ Import manquant !
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere

// ✅ Data class for registration
@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)

// ✅ Data class for profile update
@Serializable
data class UpdateProfileRequest(
    val name: String
)



// ✅ All user-related routes: register, login, logout, profile
fun Application.configureUserRoutes() {
    routing {

        // 🔐 Register
        post("/auth/register") {
            try {
                val body = call.receive<RegisterRequest>()

                val exists = transaction {
                    UsersTable.select { UsersTable.email eq body.email }.count() > 0
                }

                if (exists) {
                    call.respond(HttpStatusCode.Conflict, "Email already used")
                    return@post
                }

                val hashedPassword = BCrypt.hashpw(body.password, BCrypt.gensalt())

                transaction {
                    UsersTable.insert {
                        it[name] = body.name
                        it[email] = body.email
                        it[password] = hashedPassword
                    }
                }

                call.respond(
                    HttpStatusCode.Created,
                    mapOf("message" to "Account created successfully")
                )


            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.BadRequest, "Erreur: ${e.localizedMessage}")
            }
        }

        // 🔐 Login
        post("/auth/login") {
            try {
                val body = call.receive<LoginRequest>()

                val user = transaction {
                    UsersTable.select { UsersTable.email eq body.email }.singleOrNull()
                }

                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Incorrect email")
                    return@post
                }

                val isPasswordCorrect = BCrypt.checkpw(body.password, user[UsersTable.password])

                if (!isPasswordCorrect) {
                    call.respond(HttpStatusCode.Unauthorized, "Incorrect password")
                    return@post
                }

                val token = JwtConfig.generateToken(body.email)
                call.respond(HttpStatusCode.OK, mapOf("token" to token))

            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.BadRequest, "Erreur: ${e.localizedMessage}")
            }
        }

        // ✅ Protected Routes
        authenticate("auth-jwt") {

            // 🧑‍💻 Update Profile
            route("/user") {
                put("/profile") {
                    val principal = call.principal<JWTPrincipal>()
                    val email = principal?.payload?.getClaim("email")?.asString()

                    if (email == null) {
                        call.respond(HttpStatusCode.Unauthorized, "Token invalide")
                        return@put
                    }

                    val updateRequest = call.receive<UpdateProfileRequest>()

                    val updated = transaction {
                        UsersTable.update({ UsersTable.email eq email }) {
                            it[name] = updateRequest.name
                        }
                    }

                    if (updated == 1) {
                        call.respond(HttpStatusCode.OK, "Profile successfully updated")
                    } else {
                        call.respond(HttpStatusCode.NotFound, "User not found")
                    }
                }
            }

            // 🚪 Logout (simple feedback)
            get("/auth/logout") {
                call.respond(HttpStatusCode.OK, "Logged out successfully")
            }
            delete("/delete") {
                val principal = call.principal<JWTPrincipal>()
                val email = principal?.payload?.getClaim("email")?.asString()

                if (email == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Token invalide")
                    return@delete
                }

                val deleted = transaction {
                    UsersTable.deleteWhere { UsersTable.email eq email }
                }

                if (deleted == 1) {
                    call.respond(HttpStatusCode.OK, "User successfully deleted")
                } else {
                    call.respond(HttpStatusCode.NotFound, "User not found")
                }
            }

        }
    }
}
