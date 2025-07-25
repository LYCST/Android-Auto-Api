package cn.vove7.andro_accessibility_api.demo.xiaohongshu

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import cn.vove7.andro_accessibility_api.demo.toast
import cn.vove7.auto.core.AutoApi
import cn.vove7.auto.core.api.findAllWith
import cn.vove7.auto.core.api.swipe
import cn.vove7.auto.core.viewnode.ViewNode
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * 改进的小红书评论抓取器
 */
class XiaohongshuCommentExtractor {

    companion object {
        private const val TAG = "XhsCommentExtractor"
        private const val MAX_SCROLL_ATTEMPTS = 20
        private const val OUTPUT_DIR = "/mnt/shared/Misc"
    }

    /**
     * 主要的评论抓取入口方法
     */
    suspend fun extractComments(): JSONObject {
        val result = JSONObject()
        val commentsArray = JSONArray()

        Log.i(TAG, "=== 开始改进版小红书评论抓取 ===")

        try {
            // 1. 自动滑动到评论区域
            val scrollResult = autoScrollToCommentSection()
            if (!scrollResult) {
                Log.w(TAG, "无法滑动到评论区域")
            }

            // 2. 设置基础信息
            result.put("extractTime", System.currentTimeMillis())
            result.put("pageType", "xiaohongshu_note_detail")
            result.put("status", "success")

            // 3. 查找评论容器 (RecyclerView)
            val commentContainer = findCommentContainer()
            if (commentContainer == null) {
                Log.w(TAG, "未找到评论容器")
                result.put("comments", commentsArray)
                result.put("error", "未找到评论容器")
                result.put("actualCount", 0)
                saveResultToFile(result)
                return result
            }

            Log.i(TAG, "找到评论容器: ${commentContainer.className}, 子元素数量: ${commentContainer.childCount}")
            commentContainer.children.forEach { node ->
                node?.let {
                    Log.i(TAG, "--子评论: ${it.className}, 子元素数量: ${it.childCount}, text:${it.text}")
                    it.children.forEach{ child ->
                        child?.let { c ->
                            Log.i(TAG, "----子评论: ${c.className}, 子元素数量: ${c.childCount}, text:${c.text}")
                            c.children.forEach{ child1 ->
                                child1?.let { c1 ->
                                    Log.i(TAG, "------子评论: ${c1.className}, 子元素数量: ${c1.childCount}, text:${c1.text}")
                                    c1.children.forEach{ child2 ->
                                        child2?.let { c2 ->
                                            Log.i(TAG, "------子评论: ${c2.className}, 子元素数量: ${c2.childCount}, text:${c2.text}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. 循环抓取所有评论
            val allComments = extractAllComments(commentContainer)

            // 5. 处理评论层次结构
            val structuredComments = structureComments(allComments)

            // 6. 转换为JSON格式
            for (comment in structuredComments) {
                commentsArray.put(comment.toDetailedJson())
            }

            result.put("comments", commentsArray)
            result.put("actualCount", structuredComments.size)
            result.put("totalExtracted", allComments.size)

            Log.i(TAG, "评论抓取完成，共 ${structuredComments.size} 条主评论，总计 ${allComments.size} 条评论和回复")

            // 7. 保存结果到文件
            saveResultToFile(result)

        } catch (e: Exception) {
            Log.e(TAG, "评论抓取失败", e)
            result.put("error", e.message)
            result.put("comments", commentsArray)
            result.put("actualCount", 0)
            result.put("status", "failed")
            saveResultToFile(result)
        }

        return result
    }

    /**
     * 自动滑动到评论区域
     */
    private suspend fun autoScrollToCommentSection(): Boolean {
        Log.i(TAG, "开始自动滑动到评论区域...")

        var attempts = 0
        val maxAttempts = 8

        while (attempts < maxAttempts) {
            attempts++
            Log.i(TAG, "第 $attempts 次尝试滑动到评论区")

            // 检查是否已经到达评论区
            if (isInCommentSection()) {
                Log.i(TAG, "已到达评论区域")
                return true
            }

            // 执行向下滑动
            performDownwardScroll(0.8, 0.2)
            delay(2000) // 等待内容加载
        }

        Log.w(TAG, "经过 $maxAttempts 次滑动仍未找到评论区")
        return false
    }

    /**
     * 检查当前是否在评论区域
     */
    private suspend fun isInCommentSection(): Boolean {
        val rootNode = ViewNode.getRoot()

        // 查找RecyclerView，深度11是根据UI结构确定的
        val recyclerViews = findWithDepth(rootNode, 0, 11, "RecyclerView")

        if (recyclerViews.isNotEmpty()) {
            Log.i(TAG, "找到 ${recyclerViews.size} 个RecyclerView")
            return true
        }

        // 也可以通过查找评论相关文本来判断
        val commentIndicators = findAllWith { node ->
            val text = node.text?.toString() ?: ""
            text.contains("条评论") || text.contains("暂无评论") || text.contains("写评论")
        }

        return commentIndicators.isNotEmpty()
    }

    /**
     * 按指定深度查找特定类型的节点
     */
    private fun findWithDepth(node: ViewNode, currentDepth: Int, targetDepth: Int, className: String): List<ViewNode> {
        val results = mutableListOf<ViewNode>()

        if (currentDepth == targetDepth) {
            if (node.className?.contains(className) == true) {
                results.add(node)
            }
        } else if (currentDepth < targetDepth) {
            for (i in 0 until node.childCount) {
                val child = node.childAt(i)
                if (child != null) {
                    results.addAll(findWithDepth(child, currentDepth + 1, targetDepth, className))
                }
            }
        }

        return results
    }

    /**
     * 执行向下滑动
     */
    private suspend fun performDownwardScroll(startWeight:Double = 0.7, endWeight:Double = 0.3) {
        val (screenWidth, screenHeight) = getScreenSize()
        val centerX = screenWidth / 2
        val startY = screenHeight * startWeight
        val endY = screenHeight * endWeight

        Log.i(TAG, "执行向下滑动: ($centerX, ${startY.toInt()}) -> ($centerX, ${endY.toInt()})")
        swipe(centerX, startY.toInt(), centerX, endY.toInt(), 1000)
    }

    /**
     * 获取屏幕尺寸
     */
    private fun getScreenSize(): Pair<Int, Int> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val display = AutoApi.appCtx.display
                if (display != null) {
                    val metrics = DisplayMetrics()
                    display.getRealMetrics(metrics)
                    Pair(metrics.widthPixels, metrics.heightPixels)
                } else {
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
     * 查找评论容器
     */
    private suspend fun findCommentContainer(): ViewNode? {
        val rootNode = ViewNode.getRoot()
        val recyclerViews = findWithDepth(rootNode, 0, 11, "RecyclerView")

        return recyclerViews
            .filter { it.childCount > 0 }
            .maxByOrNull {
                // 选择子元素最多、位置最靠下的RecyclerView
                val positionScore = it.bounds.top * 0.001
                val childrenScore = it.childCount * 10.0
                val heightScore = it.bounds.height() * 0.01
                positionScore + childrenScore + heightScore
            }
    }

    /**
     * 抓取所有评论
     */
    private suspend fun extractAllComments(container: ViewNode): List<CommentItem> {
        val allComments = mutableListOf<CommentItem>()
        val processedSignatures = mutableSetOf<String>()
        var scrollAttempt = 0
        var consecutiveNoNewComments = 0

        while (scrollAttempt < MAX_SCROLL_ATTEMPTS && consecutiveNoNewComments < 3) {
            Log.i(TAG, "第 ${scrollAttempt + 1} 轮扫描评论")

            // 1. 先尝试展开所有可展开的内容
            expandAllExpandableContent(container)
            delay(1500)

            // 2. 提取当前可见的评论
            val currentComments = extractCurrentVisibleComments(container)
            var newCommentsCount = 0

            for (comment in currentComments) {
                val signature = generateCommentSignature(comment)
                if (!processedSignatures.contains(signature)) {
                    allComments.add(comment)
                    processedSignatures.add(signature)
                    newCommentsCount++
                }
            }

            Log.i(TAG, "本轮新发现 $newCommentsCount 条评论，总计 ${allComments.size} 条")

            if (newCommentsCount == 0) {
                consecutiveNoNewComments++
                Log.i(TAG, "连续 $consecutiveNoNewComments 轮未发现新评论")
            } else {
                consecutiveNoNewComments = 0
            }

            // 3. 检查是否到底
            if (checkReachedBottom(container)) {
                Log.i(TAG, "已到达评论底部")
                break
            }

            // 4. 滚动加载更多
            performDownwardScroll()
            delay(2500)
            scrollAttempt++
        }

        return allComments.sortedBy { it.position.split("-")[0].toIntOrNull() ?: 0 }
    }

    /**
     * 展开所有可展开的内容
     */
    private suspend fun expandAllExpandableContent(container: ViewNode) {
        val expandableElements = findExpandableElements(container)

        Log.i(TAG, "找到 ${expandableElements.size} 个可展开元素")

        for (element in expandableElements) {
            try {
                val text = element.text?.toString() ?: ""
                Log.i(TAG, "尝试展开: $text")

                if (element.tryClick()) {
                    Log.i(TAG, "成功点击展开: $text")
                    delay(1000)
                }
            } catch (e: Exception) {
                Log.e(TAG, "展开失败: ${e.message}")
            }
        }
    }

    /**
     * 查找可展开的元素
     */
    private suspend fun findExpandableElements(container: ViewNode): Array<ViewNode> {
        val expandTexts = listOf("展开", "条回复", "查看", "更多回复", "回复")

        return findAllWith { node ->
            val text = node.text?.toString() ?: ""
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val containerBounds = container.bounds

            // 确保在容器范围内
            val isInContainer = containerBounds.intersects(bounds.left, bounds.top, bounds.right, bounds.bottom)

            isInContainer && node.isClickable() && (
                    expandTexts.any { expandText -> text.contains(expandText) } ||
                            text.matches(Regex(".*\\d+.*条.*回复.*")) ||
                            text.matches(Regex("展开.*\\d+.*条.*")) ||
                            text.matches(Regex("查看.*\\d+.*条.*"))
                    )
        }
    }

    /**
     * 提取当前可见的评论
     */
    private suspend fun extractCurrentVisibleComments(container: ViewNode): List<CommentItem> {
        val comments = mutableListOf<CommentItem>()

        // 遍历RecyclerView下的所有FrameLayout
        for (i in 0 until container.childCount) {
            val frameLayout = container.childAt(i)
            if (frameLayout != null &&
                frameLayout.className?.contains("FrameLayout") == true &&
                frameLayout.isVisibleToUser &&
                frameLayout.bounds.height() > 50) {

                // 在FrameLayout下查找LinearLayout（评论项）
                val linearLayouts = findLinearLayoutsInFrame(frameLayout)

                for ((index, linearLayout) in linearLayouts.withIndex()) {
                    val commentItem = parseCommentFromLinearLayout(linearLayout, i, index)
                    if (commentItem != null) {
                        comments.add(commentItem)
                    }
                }
            }
        }

        return comments
    }

    /**
     * 在FrameLayout中查找LinearLayout
     */
    private fun findLinearLayoutsInFrame(frameLayout: ViewNode): List<ViewNode> {
        val linearLayouts = mutableListOf<ViewNode>()

        fun findRecursively(node: ViewNode, depth: Int) {
            if (depth > 5) return // 限制递归深度

            if (node.className?.contains("LinearLayout") == true &&
                node.isVisibleToUser &&
                node.bounds.height() > 30) {
                linearLayouts.add(node)
            }

            for (i in 0 until node.childCount) {
                val child = node.childAt(i)
                if (child != null) {
                    findRecursively(child, depth + 1)
                }
            }
        }

        findRecursively(frameLayout, 0)

        // 按位置排序（从上到下）
        return linearLayouts.sortedBy { it.bounds.top }
    }

    /**
     * 从LinearLayout解析评论
     */
    private suspend fun parseCommentFromLinearLayout(linearLayout: ViewNode, frameIndex: Int, linearIndex: Int): CommentItem? {
        try {
            val textNodes = collectAllTextNodes(linearLayout)

            if (textNodes.isEmpty()) {
                return null
            }

            val username = extractUsername(textNodes)
            val content = extractContent(textNodes)
            val timeInfo = extractTimeInfo(textNodes)
            val likes = extractLikes(textNodes)
            val isAuthorReply = checkAuthorReply(textNodes)

            // 如果没有用户名或内容，可能不是评论
            if (username.isBlank() && content.isBlank()) {
                return null
            }

            // 通过左边距判断是否为回复（回复评论通常有缩进）
            val isReply = isReplyComment(linearLayout)

            return CommentItem(
                username = username,
                content = content,
                time = timeInfo.time,
                timeOrder = timeInfo.order,
                likes = likes,
                isAuthorReply = isAuthorReply,
                isReply = isReply,
                position = "${linearLayout.bounds.top}-${linearLayout.bounds.left}",
                bounds = linearLayout.bounds,
                frameIndex = frameIndex,
                linearIndex = linearIndex,
                nodeReference = linearLayout
            )

        } catch (e: Exception) {
            Log.e(TAG, "解析评论失败: ${e.message}")
            return null
        }
    }

    /**
     * 判断是否为回复评论（通过左边距判断）
     */
    private fun isReplyComment(linearLayout: ViewNode): Boolean {
        // 回复评论通常会有更大的左边距（缩进）
        val leftMargin = linearLayout.bounds.left
        return leftMargin > 150 // 根据实际情况调整这个阈值
    }

    /**
     * 生成评论签名用于去重
     */
    private fun generateCommentSignature(comment: CommentItem): String {
        return "${comment.username}-${comment.content.take(20)}-${comment.time}-${comment.position}"
    }

    /**
     * 检查是否到达底部
     */
    private suspend fun checkReachedBottom(container: ViewNode): Boolean {
        // 查找"到底了"等结束标识
        val endIndicators = findAllWith { node ->
            val text = node.text?.toString() ?: ""
            text.contains("到底了") ||
                    text.contains("没有更多") ||
                    text.contains("已经到底") ||
                    text.contains("- 到底了 -")
        }

        if (endIndicators.isNotEmpty()) {
            Log.i(TAG, "发现结束标识: ${endIndicators.first().text}")
            return true
        }

        return false
    }

    /**
     * 滚动评论容器
     */
    private suspend fun scrollCommentContainer(container: ViewNode) {
        val bounds = container.bounds
        val startY = bounds.bottom - 200
        val endY = bounds.top + 400
        val centerX = bounds.centerX()

        Log.i(TAG, "滚动评论容器: ($centerX, $startY) -> ($centerX, $endY)")
        swipe(centerX, startY, centerX, endY, 1200)
    }

    /**
     * 将评论列表结构化（区分主评论和回复）
     */
    private fun structureComments(allComments: List<CommentItem>): List<StructuredComment> {
        val structuredComments = mutableListOf<StructuredComment>()
        val mainComments = allComments.filter { !it.isReply }

        for (mainComment in mainComments) {
            val replies = allComments.filter {
                it.isReply && isReplyToMainComment(it, mainComment, allComments)
            }.sortedBy { it.timeOrder }

            structuredComments.add(
                StructuredComment(
                    mainComment = mainComment,
                    replies = replies
                )
            )
        }

        return structuredComments.sortedByDescending { it.mainComment.timeOrder }
    }

    /**
     * 判断是否为指定主评论的回复
     */
    private fun isReplyToMainComment(reply: CommentItem, mainComment: CommentItem, allComments: List<CommentItem>): Boolean {
        val replyTop = reply.bounds.top
        val mainCommentTop = mainComment.bounds.top

        // 回复应该在主评论之后
        if (replyTop <= mainCommentTop) return false

        // 查找下一个主评论
        val nextMainComment = allComments
            .filter { !it.isReply && it.bounds.top > mainCommentTop }
            .minByOrNull { it.bounds.top }

        // 如果有下一个主评论，回复应该在下一个主评论之前
        return if (nextMainComment != null) {
            replyTop < nextMainComment.bounds.top
        } else {
            true // 如果没有下一个主评论，则认为是当前主评论的回复
        }
    }

    /**
     * 收集所有文本节点
     */
    private fun collectAllTextNodes(node: ViewNode): List<TextNodeInfo> {
        val textNodes = mutableListOf<TextNodeInfo>()

        fun collectRecursively(currentNode: ViewNode, depth: Int) {
            if (depth > 6) return

            val text = currentNode.text?.toString()
            if (!text.isNullOrBlank() && text.length > 0) {
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
    private fun extractUsername(textNodes: List<TextNodeInfo>): String {
        val candidates = textNodes.filter { textNode ->
            val text = textNode.text
            text.length in 1..30 &&
                    !text.contains("回复") &&
                    !text.contains("点赞") &&
                    !text.contains("作者") &&
                    !text.matches(Regex("\\d{2,4}-\\d{2}-\\d{2}")) &&
                    !text.matches(Regex("^\\d+$")) &&
                    !text.contains("条") &&
                    !text.contains("展开") &&
                    !text.contains("更多") &&
                    !text.contains("前") &&
                    text != "翻译"
        }.sortedBy { it.bounds.top }

        return candidates.firstOrNull()?.text ?: ""
    }

    /**
     * 提取评论内容
     */
    private fun extractContent(textNodes: List<TextNodeInfo>): String {
        val candidates = textNodes.filter { textNode ->
            val text = textNode.text
            text.length > 1 &&
                    !text.contains("回复") &&
                    !text.contains("点赞") &&
                    !text.matches(Regex("\\d{2,4}-\\d{2}-\\d{2}")) &&
                    !text.matches(Regex("^\\d+$")) &&
                    !text.contains("作者") &&
                    !text.contains("条") &&
                    !text.contains("展开") &&
                    !text.contains("更多") &&
                    !text.contains("查看") &&
                    text != "翻译"
        }.sortedByDescending { it.text.length }

        return candidates.firstOrNull()?.text ?: ""
    }

    /**
     * 提取时间信息
     */
    private fun extractTimeInfo(textNodes: List<TextNodeInfo>): TimeInfo {
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
                now - 86400000L * 30 // 假设是一个月前
            }
            else -> now - 3600000L // 默认一小时前
        }
    }

    /**
     * 提取点赞数
     */
    private fun extractLikes(textNodes: List<TextNodeInfo>): Int {
        val likeCandidates = textNodes.filter { textNode ->
            val text = textNode.text
            text.matches(Regex("^\\d+$")) && text.toIntOrNull() != null && text.length < 6
        }

        return likeCandidates.lastOrNull()?.text?.toIntOrNull() ?: 0
    }

    /**
     * 检查是否为作者回复
     */
    private fun checkAuthorReply(textNodes: List<TextNodeInfo>): Boolean {
        return textNodes.any { it.text.contains("作者") }
    }

    /**
     * 保存结果到文件
     */
    private fun saveResultToFile(result: JSONObject) {
        try {
            val outputDir = File(OUTPUT_DIR)
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "xiaohongshu_comments_$timestamp.json"
            val file = File(outputDir, filename)

            file.writeText(result.toString(2))

            Log.i(TAG, "结果已保存到: ${file.absolutePath}")
            toast("评论数据已保存到: $filename")

        } catch (e: Exception) {
            Log.e(TAG, "保存文件失败", e)
            toast("保存文件失败: ${e.message}")
        }
    }

    // ================== 数据类定义 ==================

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
        val bounds: Rect,
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
        val isReply: Boolean,
        val position: String,
        val bounds: Rect,
        val frameIndex: Int,
        val linearIndex: Int,
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
                put("isReply", isReply)
                put("extractTime", System.currentTimeMillis())
            }
        }
    }

    /**
     * 结构化评论数据类
     */
    data class StructuredComment(
        val mainComment: CommentItem,
        val replies: List<CommentItem>
    ) {
        fun toDetailedJson(): JSONObject {
            return JSONObject().apply {
                put("username", mainComment.username)
                put("content", mainComment.content)
                put("time", mainComment.time)
                put("timeOrder", mainComment.timeOrder)
                put("likes", mainComment.likes)
                put("isAuthorReply", mainComment.isAuthorReply)
                put("extractTime", System.currentTimeMillis())

                val repliesArray = JSONArray()
                replies.forEach { reply ->
                    repliesArray.put(JSONObject().apply {
                        put("username", reply.username)
                        put("content", reply.content)
                        put("time", reply.time)
                        put("timeOrder", reply.timeOrder)
                        put("likes", reply.likes)
                        put("isAuthorReply", reply.isAuthorReply)
                    })
                }
                put("replies", repliesArray)
                put("replyCount", repliesArray.length())
            }
        }
    }
}