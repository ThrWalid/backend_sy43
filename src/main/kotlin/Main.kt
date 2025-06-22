import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.http.*
import io.ktor.server.routing.*

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

import DatabaseFactory
import configureUserRoutes
import configureFriendRoutes
import configureHomeRoutes
import UsersTable

fun main() {
    // Init DB
    DatabaseFactory.init()

    // Start Ktor Server
    embeddedServer(Netty, port = 8080) {

        // JWT Authentication
        install(Authentication) {
            jwt("auth-jwt") {
                JwtConfig.configureKtorFeature(this)
            }
        }

        // JSON
        install(ContentNegotiation) {
            json()
        }

        // Logging & headers
        install(DefaultHeaders)
        install(CallLogging)

        // Custom route configs
        configureUserRoutes()
        configureFriendRoutes()
        configureHomeRoutes()



        routing {



            // Test public
            get("/") {
                call.respondText("Ktor is working perfectly and connected to PostgreSQL!")
            }

            // Protected /me route
            authenticate("auth-jwt") {
                get("/me") {
                    val principal = call.principal<JWTPrincipal>()
                    val email = principal?.payload?.getClaim("email")?.asString()

                    if (email == null) {
                        call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                        return@get
                    }

                    val user = transaction {
                        UsersTable.select { UsersTable.email eq email }.singleOrNull()
                    }

                    if (user == null) {
                        call.respond(HttpStatusCode.NotFound, "User not found")
                    } else {
                        call.respond(
                            mapOf(
                                "name" to user[UsersTable.name],
                                "email" to user[UsersTable.email]
                            )
                        )
                    }
                }
            }
        }

    }.start(wait = true)
}
