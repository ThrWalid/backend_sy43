import org.jetbrains.exposed.dao.id.IntIdTable

object FriendsTable : IntIdTable("friends") {
    val sender = reference("sender", UsersTable)
    val receiver = reference("receiver", UsersTable)
    val status = varchar("status", 50) // pending / accepted / rejected
}
