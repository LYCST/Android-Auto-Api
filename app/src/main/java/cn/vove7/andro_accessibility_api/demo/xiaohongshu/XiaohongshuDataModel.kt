package cn.vove7.andro_accessibility_api.demo.xiaohongshu

import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * 小红书数据模型
 */

/**
 * 笔记数据模型
 */
data class XhsNote(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val author: XhsUser = XhsUser(),
    val publishTime: String = "",
    val location: String = "",
    val tags: List<String> = emptyList(),
    val images: List<XhsImage> = emptyList(),
    val interactions: XhsInteraction = XhsInteraction(),
    val comments: List<XhsComment> = emptyList(),
    val type: NoteType = NoteType.UNKNOWN,
    val extractTime: Date = Date(),
    val url: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("content", content)
            put("author", author.toJson())
            put("publishTime", publishTime)
            put("location", location)
            put("tags", JSONArray(tags))
            put("images", JSONArray(images.map { it.toJson() }))
            put("interactions", interactions.toJson())
            put("comments", JSONArray(comments.map { it.toJson() }))
            put("type", type.name)
            put("extractTime", extractTime.time)
            put("url", url)
        }
    }
}

/**
 * 用户数据模型
 */
data class XhsUser(
    val id: String = "",
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val bio: String = "",
    val level: String = "",
    val isVerified: Boolean = false,
    val verifyInfo: String = "",
    val stats: XhsUserStats = XhsUserStats(),
    val location: String = "",
    val extractTime: Date = Date()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("username", username)
            put("nickname", nickname)
            put("avatar", avatar)
            put("bio", bio)
            put("level", level)
            put("isVerified", isVerified)
            put("verifyInfo", verifyInfo)
            put("stats", stats.toJson())
            put("location", location)
            put("extractTime", extractTime.time)
        }
    }
}

/**
 * 用户统计数据模型
 */
data class XhsUserStats(
    val following: Int = 0,
    val followers: Int = 0,
    val totalLikes: Int = 0,
    val totalNotes: Int = 0,
    val totalCollects: Int = 0
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("following", following)
            put("followers", followers)
            put("totalLikes", totalLikes)
            put("totalNotes", totalNotes)
            put("totalCollects", totalCollects)
        }
    }
}

/**
 * 互动数据模型
 */
data class XhsInteraction(
    val likes: Int = 0,
    val collects: Int = 0,
    val comments: Int = 0,
    val shares: Int = 0,
    val views: Int = 0
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("likes", likes)
            put("collects", collects)
            put("comments", comments)
            put("shares", shares)
            put("views", views)
        }
    }
}

/**
 * 评论数据模型
 */
data class XhsComment(
    val id: String = "",
    val content: String = "",
    val author: XhsUser = XhsUser(),
    val publishTime: String = "",
    val likes: Int = 0,
    val replies: List<XhsComment> = emptyList(),
    val isAuthorReply: Boolean = false,
    val location: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("content", content)
            put("author", author.toJson())
            put("publishTime", publishTime)
            put("likes", likes)
            put("replies", JSONArray(replies.map { it.toJson() }))
            put("isAuthorReply", isAuthorReply)
            put("location", location)
        }
    }
}

/**
 * 图片数据模型
 */
data class XhsImage(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val description: String = "",
    val index: Int = 0
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("url", url)
            put("width", width)
            put("height", height)
            put("description", description)
            put("index", index)
        }
    }
}

/**
 * 搜索结果数据模型
 */
data class XhsSearchResult(
    val keyword: String = "",
    val resultType: SearchResultType = SearchResultType.ALL,
    val notes: List<XhsNote> = emptyList(),
    val users: List<XhsUser> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val extractTime: Date = Date()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("keyword", keyword)
            put("resultType", resultType.name)
            put("notes", JSONArray(notes.map { it.toJson() }))
            put("users", JSONArray(users.map { it.toJson() }))
            put("totalCount", totalCount)
            put("hasMore", hasMore)
            put("extractTime", extractTime.time)
        }
    }
}

/**
 * 信息流数据模型
 */
data class XhsFeedData(
    val feedType: FeedType = FeedType.RECOMMEND,
    val notes: List<XhsNote> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val extractTime: Date = Date()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("feedType", feedType.name)
            put("notes", JSONArray(notes.map { it.toJson() }))
            put("totalCount", totalCount)
            put("hasMore", hasMore)
            put("extractTime", extractTime.time)
        }
    }
}

/**
 * 抓取结果数据模型
 */
data class XhsExtractionResult(
    val taskId: String = "",
    val taskType: TaskType = TaskType.SINGLE_NOTE,
    val status: ExtractionStatus = ExtractionStatus.IDLE,
    val pageType: PageType = PageType.UNKNOWN,
    val config: XiaohongshuConfig = XiaohongshuConfig(),
    val note: XhsNote? = null,
    val user: XhsUser? = null,
    val feedData: XhsFeedData? = null,
    val searchResult: XhsSearchResult? = null,
    val error: String = "",
    val extractTime: Date = Date(),
    val duration: Long = 0
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("taskId", taskId)
            put("taskType", taskType.name)
            put("status", status.name)
            put("pageType", pageType.name)
            put("config", JSONObject(config.toString()))
            note?.let { put("note", it.toJson()) }
            user?.let { put("user", it.toJson()) }
            feedData?.let { put("feedData", it.toJson()) }
            searchResult?.let { put("searchResult", it.toJson()) }
            put("error", error)
            put("extractTime", extractTime.time)
            put("duration", duration)
        }
    }
}

/**
 * 笔记类型枚举
 */
enum class NoteType {
    UNKNOWN,
    NORMAL,         // 普通笔记
    VIDEO,          // 视频笔记
    SHOPPING,       // 购物笔记
    LIVE,           // 直播笔记
    COLLECTION      // 合集笔记
}

/**
 * 搜索结果类型枚举
 */
enum class SearchResultType {
    ALL,            // 全部
    NOTES,          // 笔记
    USERS,          // 用户
    SHOPS,          // 商店
    PRODUCTS        // 商品
}

/**
 * 信息流类型枚举
 */
enum class FeedType {
    RECOMMEND,      // 推荐
    FOLLOWING,      // 关注
    NEARBY,         // 附近
    DISCOVERY       // 发现
}