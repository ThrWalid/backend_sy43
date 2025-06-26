import org.jetbrains.exposed.sql.Table

object MessagesTable : Table("messages") {
    val id = integer("id").autoIncrement()
    val senderId = integer("sender_id")
    val receiverId = integer("receiver_id")
    val content = varchar("content", 1000)
    val timestamp = varchar("timestamp", 100)
    val isRead = bool("is_read").default(false)

    override val primaryKey = PrimaryKey(id)
}
