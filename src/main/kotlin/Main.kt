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
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

//  Import database + JWT
import DatabaseFactory
import UsersTable
import JwtConfig

//  Import routes
import configureUserRoutes
import configureFriendRoutes
import configureHomeRoutes
import configureEventsRoutes
import configureMessageRoutes


fun main() {
    DatabaseFactory.init()

    embeddedServer(Netty, port = 8080) {
        install(Authentication) {
            jwt("auth-jwt") {
                JwtConfig.configureKtorFeature(this)
            }
        }

        install(ContentNegotiation) {
            json()
        }

        install(DefaultHeaders)
        install(CallLogging)
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
        }

        configureUserRoutes()
        configureFriendRoutes()
        configureHomeRoutes()
        configureEventsRoutes()
        configureMessageRoutes()

        routing {
            get("/") {
                call.respondText("Ktor backend running perfectly with PostgreSQL!")
            }
        }
    }.start(wait = true)
}

