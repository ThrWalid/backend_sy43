// Main.kt

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.*
import io.ktor.http.*

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

import DatabaseFactory
import configureUserRoutes
import JwtConfig
import UsersTable


import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.cors.routing.*


fun main() {
    // Initialize the PostgreSQL connection and create tables
    DatabaseFactory.init()

    // Start embedded Ktor server on port 8080
    embeddedServer(Netty, port = 8080) {

        // ✅ JWT Authentication setup
        install(Authentication) {
            jwt("auth-jwt") {
                JwtConfig.configureKtorFeature(this)
            }
        }

        // ✅ Enable JSON serialization
        install(ContentNegotiation) {
            json()
        }

        //  Add headers or logging if needed
        install(DefaultHeaders)
        install(CallLogging)

        //  Custom route file for user registration/login
        configureUserRoutes()
        //
        configureFriendRoutes()

        // Define the routes
        routing {

            // 🔓 Test route (public)
            get("/") {
                call.respondText("✅ Ktor is working perfectly and connected to PostgreSQL!")
            }

            // 🔐 Protected route using JWT authentication
            // ✅ Protected route with JWT
            authenticate("auth-jwt") {
                get("/me") {
                    val principal = call.principal<JWTPrincipal>()
                    val email = principal?.payload?.getClaim("email")?.asString()

                    println("EMAIL FROM TOKEN => $email") // 🔍 Debug log

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
