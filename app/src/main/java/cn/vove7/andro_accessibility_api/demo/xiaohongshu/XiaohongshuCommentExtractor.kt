package cn.vove7.andro_accessibility_api.demo.xiaohongshu

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import cn.vove7.andro_accessibility_api.demo.actions.XiaohongshuUserSearchAction
import cn.vove7.andro_accessibility_api.demo.actions.XiaohongshuUserSearchAction.Companion
import cn.vove7.andro_accessibility_api.demo.toast
import cn.vove7.auto.core.AutoApi
import cn.vove7.auto.core.api.findAllWith
import cn.vove7.auto.core.api.swipe
import cn.vove7.auto.core.api.withDepths
import cn.vove7.auto.core.viewnode.ViewNode
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * 小红书评论抓取器
 * 独立的评论抓取功能类
 */
class XiaohongshuCommentExtractor {

    companion object {
        private const val TAG = "XhsCommentExtractor"
        private const val MAX_SCROLL_ATTEMPTS = 8
        private const val MAX_COMMENT_SCROLL_ATTEMPTS = 6
    }

    /**
     * 主要的评论抓取入口方法
     * @return 包含完整评论数据的JSON对象
     */
    suspend fun extractComments(): JSONObject {
        val result = JSONObject()
        val commentsArray = JSONArray()

        Log.i(TAG, "=== 开始小红书评论抓取 ===")

        try {
            // 1. 自动滑动到评论区域
            val count = autoScrollToCommentSection()
            if (count < 0) {
                Log.w(TAG, "无法滑动到评论区域")
            }

            // 2. 获取评论基础信息
            result.put("totalCount", count)
            result.put("extractTime", System.currentTimeMillis())
            result.put("pageType", "xiaohongshu_note_detail")

            // 3. 查找评论容器
            val commentContainer = findCommentContainer()
            if (commentContainer == null) {
                Log.w(TAG, "未找到评论容器，尝试再次滑动")
                val count = autoScrollToCommentSection()
                delay(2000)
                val retryContainer = findCommentContainer()
                if (retryContainer == null) {
                    result.put("comments", commentsArray)
                    result.put("error", "未找到评论容器")
                    result.put("actualCount", 0)
                    return result
                } else {
                    return extractCommentsFromContainer(retryContainer, result, commentsArray)
                }
            }

            // 4. 从容器中抓取评论
            return extractCommentsFromContainer(commentContainer, result, commentsArray)

        } catch (e: Exception) {
            Log.e(TAG, "评论抓取失败", e)
            result.put("error", e.message)
            result.put("comments", commentsArray)
            result.put("actualCount", 0)
        }

        return result
    }

    /**
     * 从容器中抓取评论的核心逻辑
     */
    private suspend fun extractCommentsFromContainer(
        commentContainer: ViewNode,
        result: JSONObject,
        commentsArray: JSONArray
    ): JSONObject {
        // 抓取所有顶级评论
        val topLevelComments = extractTopLevelComments(commentContainer)

        // 处理每个顶级评论的回复
        for (topComment in topLevelComments) {
            val processedComment = processCommentWithReplies(topComment, commentContainer)
            commentsArray.put(processedComment)
        }

        // 按时间排序
        val sortedComments = sortCommentsByTime(commentsArray)
        result.put("comments", sortedComments)
        result.put("actualCount", sortedComments.length())

        Log.i(TAG, "评论抓取完成，共 ${sortedComments.length()} 条顶级评论")
        return result
    }

    /**
     * 自动滑动到评论区域
     */
    private suspend fun autoScrollToCommentSection(): Int {
        Log.i(TAG, "开始自动滑动到评论区域...")

        var attempts = 0
        val maxAttempts = 6

        while (attempts < maxAttempts) {
            attempts++
            Log.i(TAG, "第 $attempts 次尝试滑动到评论区")

            // 检查是否已经到达评论区
            val count = isInCommentSection()
            if (count >= 0) {
                Log.i(TAG, "已到达评论区域")

                return count
            }

            // 执行向下滑动
            performDownwardScroll()
            delay(2000) // 等待内容加载
        }

        Log.w(TAG, "经过 $maxAttempts 次滑动仍未找到评论区")
        return -1
    }

    // 数据类，用于存储深度和节点信息
    data class DepthTextInfo(
        val text: String?,
        val bounds: android.graphics.Rect,
        val depth: Int,
        val className: String?
    )

    private fun findWithDepth(node: ViewNode, currentDepth: Int, targetDepth: Int, className:String): List<ViewNode> {
        val results = mutableListOf<ViewNode>()

        // 如果当前深度等于目标深度，检查是否是TextView
        if (currentDepth == targetDepth) {
            if (node.className?.contains(className) == true) {
                Log.i(TAG, " ${node.toString()} ")
                results.add(node)
            }
        } else if (currentDepth < targetDepth) {
            // 继续递归查找子节点
            for (i in 0 until node.childCount) {
                val child = node.childAt(i)
                if (child != null) {
                    results.addAll(findWithDepth(child, currentDepth + 1, targetDepth, className))
                }
            }
        }

        return results
    }

    private fun findAllMatching(node: ViewNode?, match: (ViewNode) -> Boolean): List<ViewNode> {
        val result = mutableListOf<ViewNode>()
        if (node == null) return result
        if (match(node)) result.add(node)
        for (i in 0 until node.childCount) {
            result.addAll(findAllMatching(node.childAt(i), match))
        }
        Log.i(TAG, "findAllMatching: ${result}")
        return result
    }
    /**
     * 检查当前是否在评论区域
     */
    private suspend fun isInCommentSection(): Int {
        val rootNode = ViewNode.getRoot()
        val nearbyRecyclerViews = findWithDepth(rootNode, 0, 11, "RecyclerView")
        Log.i(TAG, "Depth(11):  ${nearbyRecyclerViews.size} ")



  /*      val rootNode = ViewNode.getRoot()
        val CommentNodes = findAllMatching(rootNode) { n ->
            val text = n.text?.toString() ?: ""
            text.contains("条评论")
        }.sortedWith(compareBy(
            { it.bounds.top },  // 按位置从上到下
            { it.text?.length ?: 0 }  // 然后按长度从短到长
        ))
        Log.i(TAG, "isInCommentSection: ${CommentNodes}")
        if (CommentNodes.isNotEmpty()) {
            Log.i(TAG, "isInCommentSection: ${CommentNodes.first().bounds.height()}")
            val countText = CommentNodes.firstOrNull()?.text?.toString() ?: ""
            val count = Regex("(\\d+)").find(countText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            return count;
        }*/
        return -1;
    }

    /**
     * 执行向下滑动
     */
    private suspend fun performDownwardScroll() {
        // 获取实际屏幕尺寸
        val (screenWidth, screenHeight) = getScreenSize()

        toast("屏幕尺寸: ${screenWidth}x${screenHeight}")

        val centerX = screenWidth / 2
        val startY = screenHeight * 0.7
        val endY = screenHeight * 0.3

        Log.i(TAG, "执行向下滑动: ($centerX, ${startY.toInt()}) -> ($centerX, ${endY.toInt()})")
        swipe(centerX, startY.toInt(), centerX, endY.toInt(), 1000)
    }

    private fun getScreenSize(): Pair<Int, Int> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // API 30+ 使用 Context.getDisplay()
                val display = AutoApi.appCtx.display
                if (display != null) {
                    val metrics = DisplayMetrics()
                    display.getRealMetrics(metrics)
                    Pair(metrics.widthPixels, metrics.heightPixels)
                } else {
                    // 如果获取不到 display，回退到 WindowManager 方式
                    getScreenSizeFromWindowManager()
                }
            }
            else -> {
                getScreenSizeFromWindowManager()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getScreenSizeFromWindowManager(): Pair<Int, Int> {
        val windowManager = AutoApi.appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay

        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 -> {
                val metrics = DisplayMetrics()
                display.getRealMetrics(metrics)
                Pair(metrics.widthPixels, metrics.heightPixels)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2 -> {
                val size = Point()
                display.getRealSize(size)
                Pair(size.x, size.y)
            }
            else -> {
                Pair(display.width, display.height)
            }
        }
    }

    /**
     * 获取评论基础信息
     */
    private suspend fun extractCommentBasicInfo(): CommentBasicInfo {
        // 检查是否已经到达评论区
        val rootNode = ViewNode.getRoot()
        val commentCountNodes = findAllMatching(rootNode) { n ->
            val text = n.text?.toString() ?: ""
            text.contains("条评论")
        }.sortedWith(compareBy(
            { it.bounds.top },  // 按位置从上到下
            { it.text?.length ?: 0 }  // 然后按长度从短到长
        ))

        val allNode = findAllWith { node -> true}

        val countText = commentCountNodes.firstOrNull()?.text?.toString() ?: ""
        val count = Regex("(\\d+)").find(countText)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        Log.i(TAG, "评论总数: $count ($countText)")

        if (count == 0 && countText.isEmpty()) {
            Log.w(TAG, "未找到评论数量信息，可能还未到达评论区")
        }
        
        return CommentBasicInfo(count, countText)
    }

    /**
     * 查找评论容器
     */
    private suspend fun findCommentContainer(): ViewNode? {
        // 在指示器附近查找RecyclerView
        val rootNode = ViewNode.getRoot()
        val nearbyRecyclerViews = findWithDepth(rootNode, 0, 11, "RecyclerView")

        return nearbyRecyclerViews
            .filter { it.childCount > 0 }
            .maxByOrNull {
                val positionScore = it.bounds.top * 0.001
                val childrenScore = it.childCount * 10.0
                val heightScore = it.bounds.height() * 0.01
                positionScore + childrenScore + heightScore
            }
    }

    /**
     * 抓取顶级评论
     */
    private suspend fun extractTopLevelComments(container: ViewNode): List<CommentItem> {
        val topLevelComments = mutableListOf<CommentItem>()
        val processedItems = mutableSetOf<String>()

        var scrollAttempt = 0

        while (scrollAttempt < MAX_COMMENT_SCROLL_ATTEMPTS) {
            Log.i(TAG, "第 ${scrollAttempt + 1} 轮扫描评论")

            val currentComments = extractCurrentVisibleComments(container)
            var newCommentsCount = 0

            for (comment in currentComments) {
                val signature = "${comment.username}-${comment.content}-${comment.position}"
                if (!processedItems.contains(signature)) {
                    topLevelComments.add(comment)
                    processedItems.add(signature)
                    newCommentsCount++
                }
            }

            Log.i(TAG, "本轮新发现 $newCommentsCount 条评论，总计 ${topLevelComments.size} 条")

            if (newCommentsCount == 0) {
                Log.i(TAG, "未发现新评论，停止滚动")
                break
            }

            scrollCommentContainer(container)
            delay(2500)
            scrollAttempt++
        }

        return topLevelComments.sortedBy { it.timeOrder }
    }

    /**
     * 提取当前可见的评论
     */
    private suspend fun extractCurrentVisibleComments(container: ViewNode): List<CommentItem> {
        val comments = mutableListOf<CommentItem>()

        for (i in 0 until container.childCount) {
            val child = container.childAt(i)
            if (child != null && child.isVisibleToUser && child.bounds.height() > 50) {
                val commentItem = parseDetailedCommentItem(child, i)
                if (commentItem != null) {
                    comments.add(commentItem)
                }
            }
        }

        return comments
    }

    /**
     * 详细解析单个评论项
     */
    private suspend fun parseDetailedCommentItem(itemNode: ViewNode, index: Int): CommentItem? {
        try {
            val textNodes = collectAllTextNodes(itemNode)

            val username = extractUsername(textNodes, itemNode)
            val content = extractContent(textNodes, itemNode)
            val timeInfo = extractTimeInfo(textNodes, itemNode)
            val likes = extractLikes(textNodes, itemNode)
            val isAuthorReply = checkAuthorReply(textNodes, itemNode)

            if (username.isBlank() || content.isBlank()) {
                return null
            }

            return CommentItem(
                username = username,
                content = content,
                time = timeInfo.time,
                timeOrder = timeInfo.order,
                likes = likes,
                isAuthorReply = isAuthorReply,
                position = "${itemNode.bounds.top}-${itemNode.bounds.left}",
                nodeReference = itemNode
            )

        } catch (e: Exception) {
            Log.e(TAG, "解析评论项失败: ${e.message}")
            return null
        }
    }

    /**
     * 处理评论及其回复
     */
    private suspend fun processCommentWithReplies(topComment: CommentItem, container: ViewNode): JSONObject {
        val commentJson = topComment.toJson()
        val repliesArray = JSONArray()

        Log.i(TAG, "尝试展开评论回复: ${topComment.username} - ${topComment.content.take(20)}...")

        val expandSuccess = expandCommentReplies(topComment.nodeReference)
        if (expandSuccess) {
            delay(2000)

            val replies = extractRepliesAfterExpand(container, topComment)
            replies.forEach { reply ->
                repliesArray.put(reply.toJson())
            }

            Log.i(TAG, "评论 ${topComment.username} 展开后获得 ${replies.size} 条回复")
        }

        commentJson.put("replies", repliesArray)
        commentJson.put("replyCount", repliesArray.length())

        return commentJson
    }

    /**
     * 展开评论回复
     */
    private suspend fun expandCommentReplies(commentNode: ViewNode): Boolean {
        try {
            val expandTexts = listOf("展开", "条回复", "查看", "更多", "回复")

            val expandButtons = findAllWith { node ->
                val text = node.text?.toString() ?: ""
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                val commentBounds = commentNode.bounds

                val isInCommentArea = commentBounds.contains(bounds.centerX(), bounds.centerY()) ||
                        (abs(bounds.top - commentBounds.bottom) < 150 &&
                                abs(bounds.centerX() - commentBounds.centerX()) < 200)

                isInCommentArea && node.isClickable() && (
                        expandTexts.any { expandText -> text.contains(expandText) } ||
                                text.matches(Regex(".*\\d+.*条.*回复.*")) ||
                                text.matches(Regex("展开.*\\d+.*条.*"))
                        )
            }

            Log.i(TAG, "找到 ${expandButtons.size} 个可能的展开按钮")

            for (button in expandButtons.take(3)) {
                val buttonText = button.text?.toString() ?: ""
                Log.i(TAG, "尝试点击展开按钮: '$buttonText' 位置: ${button.bounds}")

                val clickSuccess = button.tryClick()
                if (clickSuccess) {
                    Log.i(TAG, "成功点击展开按钮: $buttonText")
                    delay(1500)
                    return true
                }
            }

            val clickableElements = findAllWith { node ->
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                val commentBounds = commentNode.bounds

                node.isClickable() &&
                        commentBounds.contains(bounds.centerX(), bounds.centerY()) &&
                        bounds.height() < 100
            }

            for (element in clickableElements.take(2)) {
                Log.i(TAG, "尝试点击可点击元素: ${element.text}")
                if (element.tryClick()) {
                    delay(1500)
                    return true
                }
            }

            Log.i(TAG, "未找到可展开的按钮")
            return false

        } catch (e: Exception) {
            Log.e(TAG, "展开回复失败", e)
            return false
        }
    }

    /**
     * 提取展开后的回复
     */
    private suspend fun extractRepliesAfterExpand(container: ViewNode, parentComment: CommentItem): List<CommentItem> {
        val allCurrentItems = extractCurrentVisibleComments(container)

        val potentialReplies = allCurrentItems.filter { item ->
            item.position != parentComment.position &&
                    isReplyToParent(item, parentComment)
        }

        return potentialReplies.sortedBy { it.timeOrder }
    }

    /**
     * 判断是否为父评论的回复
     */
    private fun isReplyToParent(item: CommentItem, parent: CommentItem): Boolean {
        val itemTop = item.position.split("-")[0].toIntOrNull() ?: 0
        val parentTop = parent.position.split("-")[0].toIntOrNull() ?: 0

        return itemTop > parentTop && (itemTop - parentTop) < 800
    }

    /**
     * 按时间排序评论
     */
    private fun sortCommentsByTime(commentsArray: JSONArray): JSONArray {
        val commentsList = mutableListOf<JSONObject>()

        for (i in 0 until commentsArray.length()) {
            val comment = commentsArray.optJSONObject(i)
            if (comment != null) {
                commentsList.add(comment)
            }
        }

        val sortedList = commentsList.sortedByDescending { comment ->
            comment.optLong("timeOrder", 0)
        }

        val sortedArray = JSONArray()
        sortedList.forEach { sortedArray.put(it) }

        return sortedArray
    }

    /**
     * 收集所有文本节点
     */
    private fun collectAllTextNodes(node: ViewNode): List<TextNodeInfo> {
        val textNodes = mutableListOf<TextNodeInfo>()

        fun collectRecursively(currentNode: ViewNode, depth: Int) {
            if (depth > 6) return

            val text = currentNode.text?.toString()
            if (!text.isNullOrBlank() && text.length > 1) {
                textNodes.add(TextNodeInfo(
                    text = text,
                    node = currentNode,
                    bounds = currentNode.bounds,
                    isClickable = currentNode.isClickable(),
                    depth = depth
                ))
            }

            for (i in 0 until currentNode.childCount) {
                val child = currentNode.childAt(i)
                if (child != null) {
                    collectRecursively(child, depth + 1)
                }
            }
        }

        collectRecursively(node, 0)
        return textNodes.sortedBy { it.bounds.top * 1000 + it.bounds.left }
    }

    /**
     * 提取用户名
     */
    private fun extractUsername(textNodes: List<TextNodeInfo>, itemNode: ViewNode): String {
        val candidates = textNodes.filter { textNode ->
            val text = textNode.text
            text.length in 1..25 &&
                    !text.contains("回复") &&
                    !text.contains("点赞") &&
                    !text.contains("作者") &&
                    !text.matches(Regex("\\d{2,4}-\\d{2}-\\d{2}")) &&
                    !text.matches(Regex("^\\d+$")) &&
                    !text.contains("条") &&
                    text != "翻译"
        }.sortedBy { it.bounds.top }

        return candidates.firstOrNull()?.text ?: ""
    }

    /**
     * 提取评论内容
     */
    private fun extractContent(textNodes: List<TextNodeInfo>, itemNode: ViewNode): String {
        val candidates = textNodes.filter { textNode ->
            val text = textNode.text
            text.length > 2 &&
                    !text.contains("回复") &&
                    !text.contains("点赞") &&
                    !text.matches(Regex("\\d{2,4}-\\d{2}-\\d{2}")) &&
                    !text.matches(Regex("^\\d+$")) &&
                    !text.contains("作者") &&
                    !text.contains("条") &&
                    text != "翻译" &&
                    !text.contains("展开")
        }.sortedByDescending { it.text.length }

        return candidates.firstOrNull()?.text ?: ""
    }

    /**
     * 提取时间信息
     */
    private fun extractTimeInfo(textNodes: List<TextNodeInfo>, itemNode: ViewNode): TimeInfo {
        val timeCandidates = textNodes.filter { textNode ->
            val text = textNode.text
            text.matches(Regex("\\d{2,4}-\\d{2}-\\d{2}")) ||
                    text.matches(Regex("\\d{2}-\\d{2}")) ||
                    text.contains("前") ||
                    text.matches(Regex("\\d+\\s*(分钟|小时|天|月|年)前"))
        }

        val timeText = timeCandidates.firstOrNull()?.text ?: ""
        val timeOrder = parseTimeToOrder(timeText)

        return TimeInfo(timeText, timeOrder)
    }

    /**
     * 将时间文本转换为排序用的数值
     */
    private fun parseTimeToOrder(timeText: String): Long {
        val now = System.currentTimeMillis()

        return when {
            timeText.matches(Regex("\\d+分钟前")) -> {
                val minutes = Regex("(\\d+)").find(timeText)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                now - minutes * 60 * 1000
            }
            timeText.matches(Regex("\\d+小时前")) -> {
                val hours = Regex("(\\d+)").find(timeText)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                now - hours * 60 * 60 * 1000
            }
            timeText.matches(Regex("\\d+天前")) -> {
                val days = Regex("(\\d+)").find(timeText)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                now - days * 24 * 60 * 60 * 1000
            }
            timeText.matches(Regex("\\d{2,4}-\\d{2}-\\d{2}")) -> {
                now - 86400000L
            }
            else -> now - 3600000L
        }
    }

    /**
     * 提取点赞数
     */
    private fun extractLikes(textNodes: List<TextNodeInfo>, itemNode: ViewNode): Int {
        val likeCandidates = textNodes.filter { textNode ->
            val text = textNode.text
            text.matches(Regex("^\\d+$")) && text.toIntOrNull() != null
        }

        return likeCandidates.firstOrNull()?.text?.toIntOrNull() ?: 0
    }

    /**
     * 检查是否为作者回复
     */
    private fun checkAuthorReply(textNodes: List<TextNodeInfo>, itemNode: ViewNode): Boolean {
        return textNodes.any { it.text.contains("作者") }
    }

    /**
     * 滚动评论容器
     */
    private suspend fun scrollCommentContainer(container: ViewNode) {
        val bounds = container.bounds
        val startY = bounds.bottom - 150
        val endY = bounds.top + 300
        val centerX = bounds.centerX()

        swipe(centerX, startY, centerX, endY, 1000)
    }

    // ================== 数据类定义 ==================

    /**
     * 评论基础信息数据类
     */
    data class CommentBasicInfo(
        val totalCount: Int = 0,
        val displayText: String = ""
    )

    /**
     * 时间信息数据类
     */
    data class TimeInfo(
        val time: String,
        val order: Long
    )

    /**
     * 文本节点信息数据类
     */
    data class TextNodeInfo(
        val text: String,
        val node: ViewNode,
        val bounds: android.graphics.Rect,
        val isClickable: Boolean,
        val depth: Int
    )

    /**
     * 评论项数据类
     */
    data class CommentItem(
        val username: String,
        val content: String,
        val time: String,
        val timeOrder: Long,
        val likes: Int,
        val isAuthorReply: Boolean,
        val position: String,
        val nodeReference: ViewNode
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("username", username)
                put("content", content)
                put("time", time)
                put("timeOrder", timeOrder)
                put("likes", likes)
                put("isAuthorReply", isAuthorReply)
                put("extractTime", System.currentTimeMillis())
            }
        }
    }
}