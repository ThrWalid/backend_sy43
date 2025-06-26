import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.*
import io.ktor.server.auth.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere

@Serializable
data class RegisterRequest(val name: String, val email: String, val password: String)

@Serializable
data class UpdateProfileRequest(val name: String)

fun Application.configureUserRoutes() {
    routing {

        post("/auth/register") {
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

            call.respond(HttpStatusCode.Created, mapOf("message" to "Account created successfully"))
        }

        post("/auth/login") {
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

            val userId = user[UsersTable.id].value

            val token = JwtConfig.generateToken(userId, body.email)

            call.respond(HttpStatusCode.OK, mapOf("token" to token))
        }


        authenticate("auth-jwt") {

            route("/user") {
                put("/profile") {
                    val userId = call.getUserIdFromToken()

                    val body = call.receive<UpdateProfileRequest>()

                    val updated = transaction {
                        UsersTable.update({ UsersTable.id eq userId }) {
                            it[name] = body.name
                        }
                    }

                    if (updated == 1) {
                        call.respond(HttpStatusCode.OK, "Profile successfully updated")
                    } else {
                        call.respond(HttpStatusCode.NotFound, "User not found")
                    }
                }

                delete("/delete") {
                    val userId = call.getUserIdFromToken()

                    val deleted = transaction {
                        UsersTable.deleteWhere { UsersTable.id eq userId }
                    }

                    if (deleted == 1) {
                        call.respond(HttpStatusCode.OK, "User successfully deleted")
                    } else {
                        call.respond(HttpStatusCode.NotFound, "User not found")
                    }
                }
            }

            get("/auth/logout") {
                call.respond(HttpStatusCode.OK, "Logged out successfully")
            }
        }
    }
}
