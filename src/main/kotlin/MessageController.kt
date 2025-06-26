import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object MessageController {

    private val formatter = DateTimeFormatter.ISO_DATE_TIME

    fun sendMessage(senderId: Int, request: MessageRequest): MessageResponse {
        val currentTimestamp = LocalDateTime.now().format(formatter)

        val messageId = transaction {
            MessagesTable.insert {
                it[MessagesTable.senderId] = senderId
                it[MessagesTable.receiverId] = request.receiverId
                it[MessagesTable.content] = request.content
                it[MessagesTable.timestamp] = currentTimestamp
                it[MessagesTable.isRead] = false
            } get MessagesTable.id
        }

        return MessageResponse(
            id = messageId,
            senderId = senderId,
            receiverId = request.receiverId,
            content = request.content,
            timestamp = currentTimestamp,
            isRead = false
        )
    }

    fun getMessagesBetweenUsers(userId1: Int, userId2: Int): List<MessageResponse> {
        return transaction {
            MessagesTable.select {
                ((MessagesTable.senderId eq userId1) and (MessagesTable.receiverId eq userId2)) or
                        ((MessagesTable.senderId eq userId2) and (MessagesTable.receiverId eq userId1))
            }
                .orderBy(MessagesTable.timestamp to SortOrder.ASC)
                .map {
                    MessageResponse(
                        id = it[MessagesTable.id],
                        senderId = it[MessagesTable.senderId],
                        receiverId = it[MessagesTable.receiverId],
                        content = it[MessagesTable.content],
                        timestamp = it[MessagesTable.timestamp],
                        isRead = it[MessagesTable.isRead]
                    )
                }
        }
    }

    fun markMessageAsRead(messageId: Int) {
        transaction {
            MessagesTable.update({ MessagesTable.id eq messageId }) {
                it[isRead] = true
            }
        }
    }
}
