import kotlinx.serialization.Serializable

@Serializable
data class MessageRequest(
    val receiverId: Int,
    val content: String
)
