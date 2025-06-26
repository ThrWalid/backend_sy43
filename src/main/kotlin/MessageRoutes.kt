import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

fun Application.configureMessageRoutes() {
    routing {
        authenticate("auth-jwt") {

            post("/messages/send") {
                val senderId = call.getUserIdFromToken()
                val request = call.receive<MessageRequest>()
                val message = MessageController.sendMessage(senderId, request)
                call.respond(HttpStatusCode.Created, message)
            }

            get("/messages/{userId}") {
                val senderId = call.getUserIdFromToken()
                val receiverId = call.parameters["userId"]?.toIntOrNull()

                if (receiverId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid userId")
                    return@get
                }

                val messages = MessageController.getMessagesBetweenUsers(senderId, receiverId)
                call.respond(messages)
            }

            patch("/messages/{messageId}/read") {
                val messageId = call.parameters["messageId"]?.toIntOrNull()

                if (messageId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid messageId")
                    return@patch
                }

                MessageController.markMessageAsRead(messageId)
                call.respond(HttpStatusCode.OK, "Message marked as read")
            }
        }
    }
}
