import kotlinx.serialization.Serializable

@Serializable
data class MessageResponse(
    val id: Int,
    val senderId: Int,
    val receiverId: Int,
    val content: String,
    val timestamp: String,
    val isRead: Boolean
)
