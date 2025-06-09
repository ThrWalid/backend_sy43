import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

@Serializable
data class FriendRequest(val receiverEmail: String)

fun Application.configureFriendRoutes() {
    routing {
        authenticate("auth-jwt") {
            route("/friends") {

                // Helper function
                fun getUserIdByEmail(email: String): Int? = transaction {
                    UsersTable.select { UsersTable.email eq email }.singleOrNull()?.get(UsersTable.id)?.value
                }

                // ✅ Envoyer une demande d'ami
                post("/send-request") {
                    val principal = call.principal<JWTPrincipal>()
                    val senderEmail = principal?.payload?.getClaim("email")?.asString() ?: return@post call.respond(HttpStatusCode.Unauthorized)

                    val request = call.receive<FriendRequest>()

                    val senderId = getUserIdByEmail(senderEmail)
                    val receiverId = getUserIdByEmail(request.receiverEmail)

                    if (senderId == null || receiverId == null) {
                        call.respond(HttpStatusCode.NotFound, "Utilisateur non trouvé")
                        return@post
                    }
                    if (senderId == receiverId) {
                        call.respond(HttpStatusCode.BadRequest, "Impossible d'envoyer une demande à soi-même")
                        return@post
                    }

                    transaction {
                        FriendsTable.insertIgnore {
                            it[sender] = senderId
                            it[receiver] = receiverId
                            it[status] = "pending"
                        }
                    }

                    call.respond(HttpStatusCode.OK, "Demande d'ami envoyée")
                }

                // ✅ Accepter une demande
                post("/accept-request") {
                    val principal = call.principal<JWTPrincipal>()
                    val receiverEmail = principal?.payload?.getClaim("email")?.asString() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val body = call.receive<FriendRequest>()

                    val senderId = getUserIdByEmail(body.receiverEmail)
                    val receiverId = getUserIdByEmail(receiverEmail)

                    if (senderId == null || receiverId == null) {
                        call.respond(HttpStatusCode.NotFound, "Utilisateur non trouvé")
                        return@post
                    }

                    val updated = transaction {
                        FriendsTable.update({
                            (FriendsTable.sender eq senderId) and (FriendsTable.receiver eq receiverId)
                        }) {
                            it[status] = "accepted"
                        }
                    }

                    if (updated == 1) call.respond(HttpStatusCode.OK, "Demande acceptée")
                    else call.respond(HttpStatusCode.NotFound, "Demande non trouvée")
                }

                // ✅ Supprimer un ami
                delete("/remove/{friendId}") {
                    val friendId = call.parameters["friendId"]?.toIntOrNull()
                    if (friendId == null) {
                        call.respond(HttpStatusCode.BadRequest, "ID invalide")
                        return@delete
                    }

                    val principal = call.principal<JWTPrincipal>()
                    val email = principal?.payload?.getClaim("email")?.asString() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                    val userId = getUserIdByEmail(email)

                    transaction {
                        FriendsTable.deleteWhere {
                            ((sender eq userId) and (receiver eq friendId)) or
                                    ((sender eq friendId) and (receiver eq userId))
                        }
                    }

                    call.respond(HttpStatusCode.OK, "Ami supprimé")
                }

                // ✅ Lister les amis
                get {
                    val principal = call.principal<JWTPrincipal>()
                    val email = principal?.payload?.getClaim("email")?.asString() ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    val userId = getUserIdByEmail(email)

                    val friends = transaction {
                        FriendsTable
                            .select {
                                ((FriendsTable.sender eq userId) or (FriendsTable.receiver eq userId)) and
                                        (FriendsTable.status eq "accepted")
                            }
                            .map {
                                val friendId = if (it[FriendsTable.sender].value == userId) it[FriendsTable.receiver].value else it[FriendsTable.sender].value
                                val friendEmail = UsersTable.select { UsersTable.id eq friendId }.single()[UsersTable.email]
                                friendEmail
                            }
                    }

                    call.respond(HttpStatusCode.OK, friends)
                }

                // ✅ Pending requests received
                get("/pending") {
                    val principal = call.principal<JWTPrincipal>()
                    val receiverEmail = principal?.payload?.getClaim("email")?.asString() ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    val receiverId = getUserIdByEmail(receiverEmail)

                    val pending = transaction {
                        FriendsTable
                            .select { (FriendsTable.receiver eq receiverId) and (FriendsTable.status eq "pending") }
                            .map {
                                val senderId = it[FriendsTable.sender].value
                                UsersTable.select { UsersTable.id eq senderId }.single()[UsersTable.email]
                            }
                    }

                    call.respond(HttpStatusCode.OK, pending)
                }

                // ✅ Pending requests sent
                get("/sent") {
                    val principal = call.principal<JWTPrincipal>()
                    val senderEmail = principal?.payload?.getClaim("email")?.asString() ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    val senderId = getUserIdByEmail(senderEmail)

                    val sent = transaction {
                        FriendsTable
                            .select { (FriendsTable.sender eq senderId) and (FriendsTable.status eq "pending") }
                            .map {
                                val receiverId = it[FriendsTable.receiver].value
                                UsersTable.select { UsersTable.id eq receiverId }.single()[UsersTable.email]
                            }
                    }

                    call.respond(HttpStatusCode.OK, sent)
                }
            }
        }
    }
}
