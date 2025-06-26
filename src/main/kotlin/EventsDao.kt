import EventsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

data class Event(
    val id: UUID,
    val title: String,
    val description: String,
    val creatorId: UUID,
    val isShared: Boolean,
    val date: String
)

object EventsDao {
    fun createEvent(event: Event): Boolean = transaction {
        EventsTable.insert {
            it[id] = event.id
            it[title] = event.title
            it[description] = event.description
            it[creatorId] = event.creatorId
            it[isShared] = event.isShared
            it[date] = event.date
        }.insertedCount > 0
    }

    fun getEventsByUser(userId: UUID): List<Event> = transaction {
        EventsTable.select { EventsTable.creatorId eq userId }
            .map {
                Event(
                    id = it[EventsTable.id],
                    title = it[EventsTable.title],
                    description = it[EventsTable.description],
                    creatorId = it[EventsTable.creatorId],
                    isShared = it[EventsTable.isShared],
                    date = it[EventsTable.date]
                )
            }
    }
}
