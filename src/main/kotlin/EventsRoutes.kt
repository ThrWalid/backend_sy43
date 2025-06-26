
import EventsDao
import Event
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Application.configureEventsRoutes() {
    routing {
        authenticate("auth-jwt") {
            route("/events") {

                post("/create") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = UUID.fromString(principal.payload.getClaim("userId").asString())
                    val request = call.receive<Event>()

                    val newEvent = request.copy(id = UUID.randomUUID(), creatorId = userId)
                    val success = EventsDao.createEvent(newEvent)

                    if (success)
                        call.respond(mapOf("status" to "Event created successfully"))
                    else
                        call.respond(mapOf("error" to "Failed to create event"))
                }

                get("/my-events") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = UUID.fromString(principal.payload.getClaim("userId").asString())

                    val events = EventsDao.getEventsByUser(userId)
                    call.respond(events)
                }
            }
        }
    }
}
