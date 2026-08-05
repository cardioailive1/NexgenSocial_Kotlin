package com.corverxis.nexgensocial.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Field names mirror the API's JSON exactly, so no custom serial names are
// needed. Nearly everything is nullable on purpose: the API omits fields
// depending on endpoint and viewer permissions, and a non-null field here
// means a decode exception that blanks a whole screen over one missing value.

@Serializable
data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val occupation: String? = null,
    val city: String? = null,
    val country: String? = null,
)

@Serializable
data class AuthResponse(val token: String, val user: User)

@Serializable
data class MeResponse(val user: User)

@Serializable
data class MediaItem(
    val id: String,
    val url: String,
    val kind: String,          // "PHOTO" | "VIDEO"
    val position: Int = 0,
    val caption: String? = null,
) {
    val isVideo: Boolean get() = kind == "VIDEO"
}

@Serializable
data class Post(
    val id: String,
    val body: String? = null,
    val type: String? = null,
    val mediaUrl: String? = null,
    val media: List<MediaItem>? = null,
    val createdAt: String? = null,
    val author: User? = null,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val likedByViewer: Boolean = false,
    val audience: String? = null,
    val reason: String? = null,
) {
    /**
     * Posts created before the media table existed only carry [mediaUrl].
     * Falling back keeps them rendering rather than showing an empty card,
     * and the kind is inferred from [type] first, extension second --
     * assuming PHOTO here is exactly the bug that hid old videos on web.
     */
    val displayMedia: List<MediaItem>
        get() {
            media?.takeIf { it.isNotEmpty() }?.let { return it }
            val url = mediaUrl?.takeIf { it.isNotEmpty() } ?: return emptyList()
            val kind = when {
                type == "VIDEO" -> "VIDEO"
                url.substringAfterLast('.', "").lowercase() in setOf("webm", "mp4", "mov", "m4v") -> "VIDEO"
                else -> "PHOTO"
            }
            return listOf(MediaItem(id = "legacy-$id", url = url, kind = kind))
        }
}

@Serializable data class FeedResponse(val posts: List<Post>)

@Serializable
data class Reel(
    val id: String,
    val videoUrl: String,
    val thumbnailUrl: String? = null,
    val caption: String? = null,
    val durationSec: Double? = null,
    val soundName: String? = null,
    val isOriginalAudio: Boolean = true,
    val author: User? = null,
    val hashtags: List<String> = emptyList(),
    val viewCount: Int = 0,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val likedByViewer: Boolean = false,
)

@Serializable data class ReelsResponse(val reels: List<Reel>)

@Serializable
data class Message(
    val id: String,
    val body: String? = null,
    val createdAt: String? = null,
    val sender: User? = null,
    val attachments: List<MediaItem> = emptyList(),
)

@Serializable
data class Conversation(
    val id: String,
    val otherUser: User? = null,
    val lastMessage: Message? = null,
    val lastMessageAt: String? = null,
    val unreadCount: Int = 0,
)

@Serializable
data class Call(
    val id: String,
    val callerId: String,
    val calleeId: String,
    val kind: String,
    val status: String,
    val caller: User? = null,
    val callee: User? = null,
)

@Serializable
data class JobPosting(
    val id: String,
    val title: String,
    val companyName: String,
    val description: String? = null,
    val location: String? = null,
    val arrangement: String? = null,
    val employmentType: String? = null,
    val salaryMin: Int? = null,
    val salaryMax: Int? = null,
    val salaryCurrency: String? = "USD",
    val salaryPeriod: String? = "YEAR",
    val appliedByViewer: Boolean = false,
) {
    val salaryText: String?
        get() {
            if (salaryMin == null && salaryMax == null) return null
            val per = when (salaryPeriod) {
                "MONTH" -> "/mo"; "HOUR" -> "/hr"; else -> "/yr"
            }
            val cur = salaryCurrency ?: "USD"
            return if (salaryMin != null && salaryMax != null)
                "$cur ${"%,d".format(salaryMin)}–${"%,d".format(salaryMax)}$per"
            else "$cur ${"%,d".format(salaryMin ?: salaryMax)}$per"
        }
}

@Serializable
data class MarketListing(
    val id: String,
    val title: String,
    val description: String,
    val priceCents: Int,
    val condition: String? = null,
    val location: String? = null,
    val seller: User? = null,
    val media: List<MediaItem> = emptyList(),
    val coverUrl: String? = null,
) {
    val priceText: String get() = "$%.2f".format(priceCents / 100.0)
}

@Serializable data class UsersResponse(val users: List<User>)
@Serializable data class ConversationsResponse(val conversations: List<Conversation>)
@Serializable data class MessagesResponse(val messages: List<Message>)
@Serializable data class JobsResponse(val jobs: List<JobPosting>)
@Serializable data class ListingsResponse(val listings: List<MarketListing>)
@Serializable data class CallResponse(val call: Call)
@Serializable data class IncomingCallResponse(val call: Call? = null)
@Serializable data class ApiError(val error: String? = null)

/** For endpoints that return 204 or an empty body. */
@Serializable
class EmptyResponse
