// ✅ DatabaseFactory.kt
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.dotenv
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction


object DatabaseFactory {
    fun init() {
        val dotenv = dotenv {
            directory = System.getProperty("user.dir")
            ignoreIfMalformed = true
            ignoreIfMissing = true
        }
        val url = dotenv["DB_URL"]
        val user = dotenv["DB_USER"]
        val password = dotenv["DB_PASSWORD"]

        val config = HikariConfig().apply {
            this.jdbcUrl = url
            this.username = user
            this.password = password
            this.driverClassName = "org.postgresql.Driver"
        }

        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)

        transaction {
            SchemaUtils.create(
                UsersTable,
                FriendsTable,
                EventsTable,
                MessagesTable
            )
            println("All tables created.")
        }



    }
}
