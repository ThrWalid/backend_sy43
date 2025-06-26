import org.jetbrains.exposed.sql.Table
import java.util.*

object EventsTable : Table("events") {
    val id = uuid("id")
    val title = varchar("title", 255)
    val description = text("description")
    val creatorId = uuid("creator_id")
    val isShared = bool("is_shared")
    val date = varchar("date", 100)

    override val primaryKey = PrimaryKey(id)
}
