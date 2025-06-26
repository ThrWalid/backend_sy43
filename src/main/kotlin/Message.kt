import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import java.time.Instant

@Serializable
data class Message(
    val id: Int,
    val senderId: Int,
    val receiverId: Int,
    val content: String,
    @Contextual val timestamp: Instant,
    val isRead: Boolean = false
)
