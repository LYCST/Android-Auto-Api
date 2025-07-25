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
import cn.vove7.auto.core.viewfinder.AcsNode
import cn.vove7.auto.core.viewnode.ViewNode
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * 修复的小红书评论抓取器 - 针对三种评论类型优化
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

        Log.i(TAG, "=== 开始修复版小红书评论抓取 ===")

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

            // 4. 循环抓取所有评论
            val allComments = extractAllComments(commentContainer)
            // 6. 转换为JSON格式
            for (comment in allComments) {
                commentsArray.put(comment.toJson())
            }

//            // 5. 处理评论层次结构
//            val structuredComments = structureComments(allComments)
//
//            // 6. 转换为JSON格式
//            for (comment in structuredComments) {
//                commentsArray.put(comment.toDetailedJson())
//            }

            result.put("comments", commentsArray)
            //result.put("actualCount", structuredComments.size)
            result.put("totalExtracted", allComments.size)

            Log.i(TAG, "评论抓取完成，共 ${allComments.size} 条主评论，总计 ${allComments.size} 条评论和回复")

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
     * 按指定深度查找特定类型的节点
     */
    private fun findWithDepthText(node: ViewNode, currentDepth: Int, targetDepth: Int, className: String): List<ViewNode> {
        val results = mutableListOf<ViewNode>()

        if (currentDepth == targetDepth) {
            if (node.className?.contains(className) == true) {
                if (node.text?.isNotEmpty() == true){
                    results.add(node)
                }
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

        while (scrollAttempt < MAX_SCROLL_ATTEMPTS) {
            container.refresh()
            Log.i(TAG, "第 ${scrollAttempt + 1} 轮扫描评论")
            // 1. 先尝试展开所有可展开的内容
            val expandedCount = expandAllExpandableContent(container)
            if (expandedCount > 0) {
                Log.i(TAG, "extractAllComments: 点击了展开！！")
                delay(2000) // 展开后等待更长时间
                continue
            }

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

            // 3. 检查是否到底或没有评论
            if (newCommentsCount == 0 && (checkReachedBottom(container) || checkNoComments())) {
                Log.i(TAG, "已到达评论底部或没有评论")
                break
            }

            // 4. 如果已经有评论了，连续3轮没新评论就停止
            if (allComments.isNotEmpty() && consecutiveNoNewComments >= 3) {
                Log.i(TAG, "已有评论且连续3轮无新内容，停止抓取")
                break
            }
            // 5. 滚动加载更多
            performDownwardScroll()
            delay(2500)
            scrollAttempt++
        }

        return allComments
    }

    /**
     * 检查是否没有评论
     */
    private suspend fun checkNoComments(): Boolean {
        val noCommentIndicators = findAllWith { node ->
            val text = node.text?.toString() ?: ""
            text.contains("还没有评论哦") ||
                    text.contains("暂无评论") ||
                    text.contains("没有评论") ||
                    text.contains("快来抢沙发")
        }

        if (noCommentIndicators.isNotEmpty()) {
            Log.i(TAG, "发现无评论标识: ${noCommentIndicators.first().text}")
            return true
        }

        return false
    }

    /**
     * 展开所有可展开的内容 - 查找FrameLayout下的展开按钮
     */
    private suspend fun expandAllExpandableContent(container: ViewNode): Int {

        container.children.forEach{ child ->
            if (child != null) {
                if (child.className?.contains("FrameLayout") == true)
                {
                    val nodes = findWithDepth(child, 0, 3, "TextView")
                    if (nodes.isNotEmpty() && nodes.first().text?.contains("展开") == true){
                        // 处理展开
                        Log.i(TAG, "expandAllExpandableContent: 点击${nodes.first().text}")
                        nodes.first().parent?.click()
                    }
                }
            }
        }
        return -1

/*        val expandableElements = findExpandableElements(container)

        Log.i(TAG, "找到 ${expandableElements.size} 个可展开元素")

        var expandedCount = 0
        for (element in expandableElements) {
            try {
                val text = element.text?.toString() ?: ""
                Log.i(TAG, "尝试展开: $text")

                val bounds = element.bounds
                if (bounds.width() > 0 && bounds.height() > 0) {
                    element.tryClick()
                    Log.i(TAG, "成功点击展开: $text")
                    expandedCount++
                    delay(1500) // 每次展开后等待
                }
            } catch (e: Exception) {
                Log.e(TAG, "展开失败: ${e.message}")
            }
        }

        return expandedCount*/
    }

    /**
     * 查找可展开的元素 - 修正：展开按钮实际在LinearLayout结构中
     */
    private suspend fun findExpandableElements(container: ViewNode): Array<ViewNode> {
        return findAllWith { node ->
            val text = node.text?.toString() ?: ""
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val containerBounds = container.bounds

            // 确保在容器范围内
            val isInContainer = containerBounds.intersects(bounds.left, bounds.top, bounds.right, bounds.bottom)

            // 查找展开元素
            val isExpandable = node.isClickable() && (
                    text.contains("展开") && text.contains("条回复") ||
                            text.matches(Regex("展开\\s*\\d+\\s*条回复")) ||
                            text.matches(Regex("查看\\s*\\d+\\s*条回复")) ||
                            text == "展开全文" ||
                            text.startsWith("展开") && text.contains("回复")
                    )

            // 修正：展开按钮可能在LinearLayout或FrameLayout的子结构中
            val isInValidStructure = checkParentContainsType(node, listOf("FrameLayout", "LinearLayout"), 4)

            if (isInContainer && isExpandable && isInValidStructure) {
                Log.i(TAG, "找到可展开元素: \"$text\", bounds=${bounds}, 父容器路径: ${getNodePath(node)}")
            }

            isInContainer && isExpandable && isInValidStructure && bounds.width() > 50 && bounds.height() > 20
        }
    }

    /**
     * 检查父容器是否包含指定类型的容器
     */
    private fun checkParentContainsType(node: AcsNode, containerTypes: List<String>, maxDepth: Int): Boolean {
        var current = node.parent
        var depth = 0

        while (current != null && depth < maxDepth) {
            containerTypes.forEach { type ->
                if (current.className?.contains(type) == true) {
                    return true
                }
            }
            current = current.parent
            depth++
        }

        return false
    }

    /**
     * 在FrameLayout中查找展开按钮
     */
    private fun findExpandButtonInFrameLayoutAndClick(frameLayout: ViewNode): ViewNode? {

        val nodes = findWithDepth(frameLayout, 0, 3, "TextView")
        if (nodes.isNotEmpty() && nodes.first().text?.contains("展开") == true){
            Log.i(TAG, "findExpandButtonInFrameLayoutAndClick: 点击${nodes.first().text}")
            nodes.first().parent?.click()
            return nodes.first().parent
        }

        return null
    }

    /**
     * 获取节点路径（用于调试）
     */
    private fun getNodePath(node: AcsNode): String {
        val path = mutableListOf<String>()
        var current: AcsNode? = node
        var depth = 0

        while (current != null && depth < 6) {
            val className = current.className.toString().substringAfterLast('.') ?: "Unknown"
            val text = current.text?.toString()
            if (!text.isNullOrBlank() && text.length < 20) {
                path.add("$className(\"$text\")")
            } else {
                path.add(className)
            }
            current = current.parent
            depth++
        }

        return path.reversed().joinToString(" -> ")
    }

    /**
     * 提取当前可见的评论 - 区分三种评论类型
     */
    private suspend fun extractCurrentVisibleComments(container: ViewNode): List<CommentItem> {
        val comments = mutableListOf<CommentItem>()

        Log.i(TAG, "开始扫描RecyclerView的 ${container.childCount} 个子元素")

        // 遍历RecyclerView下的所有子元素
        for (i in 0 until container.childCount) {
            val child = container.childAt(i)
            if (child != null && child.isVisibleToUser && child.bounds.height() > 50) {

                when (child.className) {
                    // 处理LinearLayout类型的评论（主要评论容器）
                    "android.widget.LinearLayout" -> {
                        val commentItem = parseCommentFromLinearLayout(child, i, 0)
                        if (commentItem != null) {
                            comments.add(commentItem)
                            Log.i(TAG, "从LinearLayout解析到评论: ${commentItem.username} - ${commentItem.content}")
                        }
                    }

                    // 处理FrameLayout类型 - 可能包含展开按钮或其他内容
                    "android.widget.FrameLayout" -> {
                        // 检查是否包含展开按钮
                        val expandButton = findExpandButtonInFrameLayoutAndClick(child)
                        if (expandButton != null) {
                            Log.d(TAG, "在FrameLayout中发现展开按钮: ${expandButton.text}, 点击展开后跳过本次")
                            break
                        } else {
                            // 如果不是展开按钮，可能包含其他评论内容
                            Log.d(TAG, "在FrameLayout中发现不属于展开按钮的内容: ${expandButton}")
                        }
                    }

                    // 处理其他可能包含评论的容器
                    else -> {
                        // 递归查找LinearLayout
                        val linearLayouts = findLinearLayoutsInContainer(child)
                        for ((index, linearLayout) in linearLayouts.withIndex()) {
                            val commentItem = parseCommentFromLinearLayout(linearLayout, i, index)
                            if (commentItem != null) {
                                comments.add(commentItem)
                                Log.i(TAG, "从其他容器中的LinearLayout解析到评论: ${commentItem.username} - ${commentItem.content}")
                            }
                        }
                    }
                }
            }
        }

        Log.i(TAG, "本轮共解析到 ${comments.size} 条评论")
        return comments
    }

    /**
     * 在容器中查找LinearLayout
     */
    private fun findLinearLayoutsInContainer(container: ViewNode): List<ViewNode> {
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

        findRecursively(container, 0)

        // 按位置排序（从上到下）
        return linearLayouts.sortedBy { it.bounds.top }
    }

    /**
     * 从LinearLayout解析评论 - 支持三种评论类型
     */
    private suspend fun parseCommentFromLinearLayout(linearLayout: ViewNode, frameIndex: Int, linearIndex: Int): CommentItem? {
        try {
            // 1. 分析LinearLayout的结构
            val structure = analyzeLinearLayoutStructure(linearLayout)

           // Log.i(TAG, "分析LinearLayout结构: ${structure}")

            // 3. 根据结构类型解析评论
            return when (structure.type) {
                CommentType.TEXT_ONLY -> parseTextOnlyComment(structure, frameIndex, linearIndex, linearLayout)
                CommentType.IMAGE_ONLY -> parseImageOnlyComment(structure, frameIndex, linearIndex, linearLayout)
                CommentType.TEXT_AND_IMAGE -> parseTextAndImageComment(structure, frameIndex, linearIndex, linearLayout)
                CommentType.UNKNOWN -> null
            }

        } catch (e: Exception) {
            Log.e(TAG, "解析评论失败: ${e.message}")
            return null
        }
    }

    /**
     * 分析LinearLayout的结构
     */
    private fun analyzeLinearLayoutStructure(linearLayout: ViewNode): CommentStructure {
        val allChildren = mutableListOf<ViewNode>()
        val userNodes = findWithDepthText(linearLayout,0,4, "TextView").filter { it.bounds.left < 500 }.sortedBy {  it.bounds.left  }
        val imageNodes = findWithDepth(linearLayout,0,5, "ImageView").filter { it.bounds.left < 500 }.sortedBy {  it.bounds.top * 1000 + it.bounds.left  }
        val textNodes =  mutableListOf<ViewNode>()
        textNodes.addAll(findWithDepthText(linearLayout,0,3, "TextView").filter { it.bounds.left < 500 }.sortedBy {  it.bounds.top * 1000 + it.bounds.left  })
        if (imageNodes.isNotEmpty()){
            textNodes.addAll(findWithDepthText(linearLayout,0,4, "TextView").filter {  it.bounds.left < 500 && it.hashCode() != userNodes.first().hashCode() }.sortedBy {  it.bounds.top * 1000 + it.bounds.left  })
        }

        textNodes.forEach { viewNode -> Log.i(TAG, "   analyzeLinearLayoutStructure: ${viewNode.text}") }
        // 判断评论类型
        val type = when {
            textNodes.isNotEmpty() && imageNodes.isEmpty() -> CommentType.TEXT_ONLY
            textNodes.size == 1 && imageNodes.isNotEmpty() -> CommentType.IMAGE_ONLY
            textNodes.size >=1  && imageNodes.isNotEmpty() -> CommentType.TEXT_AND_IMAGE
            else -> CommentType.UNKNOWN
        }
        Log.i(TAG, "userNodes:${userNodes.size}  textNodes: ${textNodes.size}   imageNodes:${imageNodes.size} type:${type.name}")
        allChildren.addAll(userNodes)
        allChildren.addAll(textNodes)
        allChildren.addAll(imageNodes)

        return CommentStructure(
            type = type,
            userNodes = userNodes,
            textNodes = textNodes,
            imageNodes = imageNodes,
            allChildren = allChildren
        )
    }

    /**
     * 解析纯文字评论（类型1）
     */
    private fun parseTextOnlyComment(structure: CommentStructure, frameIndex: Int, linearIndex: Int, linearLayout: ViewNode): CommentItem? {
        val textNodes = structure.textNodes
        return parseFromMultipleTextNodes(structure.userNodes, textNodes, frameIndex, linearIndex, linearLayout)
    }

    /**
     * 解析纯图片评论（类型2）
     */
    private fun parseImageOnlyComment(structure: CommentStructure, frameIndex: Int, linearIndex: Int, linearLayout: ViewNode): CommentItem? {
        val textNodes = structure.textNodes
        // 类型2：ImageView + TextView，TextView只有日期+地区+回复按钮
        val timeAndLocationText = textNodes[0].text.toString()

        val time= findTimeFromTextNodes(textNodes)
        return CommentItem(
            username = findUsernameFromTextNodes(structure.userNodes).ifEmpty { "用户" },
            content = "[图片评论]", // 图片评论标识
            time = findTimeFromTextNodes(textNodes),
            timeOrder = parseTimeToOrder(time),
            likes = 0, // 图片评论通常没有显示点赞数
            isAuthorReply = timeAndLocationText.contains("作者"),
            isReply = isReplyComment(linearLayout),
            position = "${linearLayout.bounds.top}-${linearLayout.bounds.left}",
            bounds = linearLayout.bounds,
            frameIndex = frameIndex,
            linearIndex = linearIndex,
            nodeReference = linearLayout,
            commentType = CommentType.IMAGE_ONLY,
            imageCount = structure.imageNodes.size
        )

        return null
    }

    /**
     * 解析文本+图片评论（类型3）
     */
    private fun parseTextAndImageComment(structure: CommentStructure, frameIndex: Int, linearIndex: Int, linearLayout: ViewNode): CommentItem? {
        val textNodes = structure.textNodes

        // 类型3：TextView + ImageView + TextView，回复消息和日期是分开的
        if (textNodes.size >= 2) {
            // 第一个通常是用户名或回复内容
            // 最后一个通常是时间+地区+回复按钮
            val username = findUsernameFromTextNodes(structure.userNodes).ifEmpty { "用户" }
            val content = findContentFromTextAndImageComment(textNodes, username)
            val timeText = textNodes.last().text.toString()
            val (time, location) = parseTimeAndLocation(timeText)
            val likes = findLikesFromTextNodes(textNodes)

            return CommentItem(
                username = username,
                content = if (content.isNotEmpty()) "$content [包含图片]" else "[图片评论]",
                time = time,
                timeOrder = parseTimeToOrder(time),
                likes = likes,
                isAuthorReply = timeText.contains("作者"),
                isReply = isReplyComment(linearLayout),
                position = "${linearLayout.bounds.top}-${linearLayout.bounds.left}",
                bounds = linearLayout.bounds,
                frameIndex = frameIndex,
                linearIndex = linearIndex,
                nodeReference = linearLayout,
                commentType = CommentType.TEXT_AND_IMAGE,
                imageCount = structure.imageNodes.size
            )
        }

        return null
    }

    /**
     * 从合并文本中解析评论信息
     */
    private fun parseFromCombinedText(userNodes: List<ViewNode>,fullText: String, frameIndex: Int, linearIndex: Int, linearLayout: ViewNode): CommentItem? {
        Log.i(TAG, "解析合并文本: \"$fullText\"")
        val username = findUsernameFromTextNodes(userNodes)
        // 移除末尾的"回复"
        var cleanText = fullText.replace(Regex("\\s+回复$"), "")

        // 提取时间信息
        val timePattern = Regex("(\\d{2}-\\d{2}|\\d+[天小时分钟]前)")
        val timeMatch = timePattern.find(cleanText)
        val time = timeMatch?.value ?: "未知时间"

        // 移除时间
        if (timeMatch != null) {
            cleanText = cleanText.replace(timeMatch.value, "").trim()
        }

        // 提取地区信息（通常是2-4个中文字符）
        val locationPattern = Regex("\\s+([\\u4e00-\\u9fff]{2,4})$")
        val locationMatch = locationPattern.find(cleanText)
        if (locationMatch != null) {
            cleanText = cleanText.replace(locationMatch.value, "").trim()
        }

        // 剩余的就是评论内容
        val content = cleanText.trim()

        if (content.isEmpty()) return null

        return CommentItem(
            username = username.ifEmpty { "用户" },
            content = content,
            time = time,
            timeOrder = parseTimeToOrder(time),
            likes = 0,
            isAuthorReply = fullText.contains("作者"),
            isReply = isReplyComment(linearLayout),
            position = "${linearLayout.bounds.top}-${linearLayout.bounds.left}",
            bounds = linearLayout.bounds,
            frameIndex = frameIndex,
            linearIndex = linearIndex,
            nodeReference = linearLayout,
            commentType = CommentType.TEXT_ONLY,
            imageCount = 0
        )
    }

    /**
     * 从多个文本节点解析评论
     */
    private fun parseFromMultipleTextNodes(userNodes: List<ViewNode>, textNodes: List<ViewNode>, frameIndex: Int, linearIndex: Int, linearLayout: ViewNode): CommentItem? {
        val username = findUsernameFromTextNodes(userNodes)
        val content = findContentFromTextNodes(textNodes, username)
        val time = findTimeFromTextNodes(textNodes)
        val likes = findLikesFromTextNodes(textNodes)

        if (username.isEmpty() && content.isEmpty()) return null

        return CommentItem(
            username = username.ifEmpty { "用户" },
            content = content,
            time = time,
            timeOrder = parseTimeToOrder(time),
            likes = likes,
            isAuthorReply = textNodes.any { it.text.toString().contains("作者") },
            isReply = isReplyComment(linearLayout),
            position = "${linearLayout.bounds.top}-${linearLayout.bounds.left}",
            bounds = linearLayout.bounds,
            frameIndex = frameIndex,
            linearIndex = linearIndex,
            nodeReference = linearLayout,
            commentType = CommentType.TEXT_ONLY,
            imageCount = 0
        )
    }

    /**
     * 从图片评论中查找用户名
     */
    private fun findUsernameFromImageComment(structure: CommentStructure): String {
        // 在图片评论中，用户名通常在头像或其他地方，这里先返回默认值
        return "用户"
    }

    /**
     * 从文本+图片评论中查找用户名
     */
    private fun findUsernameFromTextAndImageComment(textNodes: List<ViewNode>): String {
        // 用户名通常是第一个较短的文本
        val candidates = textNodes.filter { node ->
            val text = node.text.toString()
            text.length in 1..30 &&
                    !text.contains("回复") &&
                    !text.contains("天前") &&
                    !text.contains("小时前") &&
                    !text.contains("分钟前") &&
                    !text.matches(Regex(".*\\d{2}-\\d{2}.*")) &&
                    !text.matches(Regex("^\\d+$")) &&
                    !text.contains("作者") &&
                    text.trim().isNotEmpty()
        }.sortedBy { it.bounds.top * 1000 + it.bounds.left }

        return candidates.firstOrNull()?.text.toString().trim() ?: "用户"
    }

    /**
     * 从文本+图片评论中查找内容
     */
    private fun findContentFromTextAndImageComment(textNodes: List<ViewNode>, username: String): String {
        // 查找评论内容，排除用户名和时间信息
        val candidates = textNodes.filter { node ->
            val text = node.text.toString()
            text != username &&
                    text.length > 1 &&
                    !text.matches(Regex("^\\d+$")) &&
                    !text.matches(Regex(".*\\d{2}-\\d{2}.*")) &&
                    !text.contains("天前") &&
                    !text.contains("小时前") &&
                    !text.contains("分钟前") &&
                    !text.contains("回复") &&
                    !text.contains("作者") &&
                    text.trim().isNotEmpty()
        }

        return candidates.firstOrNull()?.text.toString().trim() ?: ""
    }

    /**
     * 解析时间和地区信息
     */
    private fun parseTimeAndLocation(text: String): Pair<String, String> {
        // 提取时间
        val timePattern = Regex("(\\d{2}-\\d{2}|\\d+[天小时分钟]前)")
        val timeMatch = timePattern.find(text)
        val time = timeMatch?.value ?: "未知时间"

        // 提取地区（移除时间后的中文字符）
        var remainingText = text
        if (timeMatch != null) {
            remainingText = remainingText.replace(timeMatch.value, "")
        }
        remainingText = remainingText.replace("回复", "").trim()

        val locationPattern = Regex("([\\u4e00-\\u9fff]{2,4})")
        val locationMatch = locationPattern.find(remainingText)
        val location = locationMatch?.value ?: ""

        return Pair(time, location)
    }

    /**
     * 从文本节点查找用户名
     */
    private fun findUsernameFromTextNodes(textNodes: List<ViewNode>): String {
        val candidates = textNodes.filter { node ->
            val text = node.text.toString()
            text.length in 1..30 &&
                    !text.contains("回复") &&
                    !text.contains("天前") &&
                    !text.contains("小时前") &&
                    !text.contains("分钟前") &&
                    !text.matches(Regex(".*\\d{2}-\\d{2}.*")) &&
                    !text.matches(Regex("^\\d+$")) &&
                    !text.contains("作者") &&
                    text.trim().isNotEmpty()
        }.sortedBy { it.bounds.top * 1000 + it.bounds.left }

        return candidates.firstOrNull()?.text.toString().trim() ?: ""
    }

    /**
     * 从文本节点查找评论内容
     */
    private fun
            findContentFromTextNodes(textNodes: List<ViewNode>, username: String): String {
        // 寻找包含"回复"的节点，从中提取内容
        val replyNode = textNodes.find { it.text.toString().contains("回复") }
        if (replyNode != null) {
            return extractContentFromReplyText(replyNode.text.toString())
        }

        // 如果没有"回复"节点，寻找最有可能是内容的文本
        val contentNode = textNodes
            .filter { !it.text.toString().matches(Regex(".*\\d{2}-\\d{2}.*")) }
            .filter { !it.text.toString().contains("天前") && !it.text.toString().contains("小时前") && !it.text.toString().contains("分钟前") }
            .maxByOrNull { it.text.toString().length }

        return contentNode?.text.toString().trim() ?: ""
    }

    /**
     * 从文本节点查找时间
     */
    private fun findTimeFromTextNodes(textNodes: List<ViewNode>): String {
        for (node in textNodes) {
            val text = node.text.toString()

            // 匹配各种时间格式
            when {
                text.contains("小时前") -> {
                    val match = Regex("(\\d+小时前)").find(text)
                    if (match != null) return match.groupValues[1]
                }
                text.contains("分钟前") -> {
                    val match = Regex("(\\d+分钟前)").find(text)
                    if (match != null) return match.groupValues[1]
                }
                text.contains("天前") -> {
                    val match = Regex("(\\d+天前)").find(text)
                    if (match != null) return match.groupValues[1]
                }
                text.matches(Regex(".*\\d{2}-\\d{2}.*")) -> {
                    val match = Regex("(\\d{2}-\\d{2})").find(text)
                    if (match != null) return match.groupValues[1]
                }
            }
        }
        return "未知时间"
    }

    /**
     * 从文本节点查找点赞数
     */
    private fun findLikesFromTextNodes(textNodes: List<ViewNode>): Int {
        // 查找纯数字的文本节点，通常在右侧
        val numberNodes = textNodes.filter { node ->
            val text = node.text.toString()
            text.matches(Regex("^\\d+$")) && text.toIntOrNull() != null && text.length < 6
        }.sortedByDescending { it.bounds.right }

        return numberNodes.firstOrNull()?.text.toString().toIntOrNull() ?: 0
    }

    /**
     * 从包含"回复"的文本中提取评论内容
     */
    private fun extractContentFromReplyText(text: String): String {
        Log.i(TAG, "extractContentFromReplyText 输入: \"$text\"")
        
        // 移除末尾的"翻译"
        var content = text.replace(Regex("\\s*翻译\\s*$"), "")
        // 移除末尾的"回复"
        content = content.replace(Regex("\\s*回复\\s*$"), "")

        // 移除地点信息（如"广东"、"浙江"等）- 更宽松的匹配
        content = content.replace(Regex("\\s*[\\u4e00-\\u9fff]{2,4}\\s*$"), " ")

        // 移除时间信息 - 更宽松的匹配
        content = content.replace(Regex("\\s*\\d+小时前\\s*$"), " ")
        content = content.replace(Regex("\\s*\\d+分钟前\\s*$"), " ")
        content = content.replace(Regex("\\s*\\d+天前\\s*$"), " ")
        content = content.replace(Regex("\\s*\\d{1,2}-\\d{1,2}\\s*$"), " ")

        
        Log.i(TAG, "extractContentFromReplyText 输出: \"${content.trim()}\"")
        // 移除多余的空白字符
        return content.trim()
    }

    /**
     * 判断是否为回复评论（通过左边距判断）
     */
    private fun isReplyComment(linearLayout: ViewNode, left:Int = 100): Boolean {
        // 回复评论通常会有更大的左边距（缩进）
        val leftMargin = linearLayout.bounds.left
        return leftMargin > left // 根据实际情况调整这个阈值
    }

    /**
     * 生成评论签名用于去重
     */
    private fun generateCommentSignature(comment: CommentItem): String {
        return "${comment.username}-${comment.content.take(20)}-${comment.time}-${comment.bounds.width()*comment.bounds.height()}"
        //return "${comment.hashCode()}"
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
     * 将评论列表结构化（区分主评论和回复）
     */
    private fun structureComments(allComments: List<CommentItem>): List<StructuredComment> {
        val structuredComments = mutableListOf<StructuredComment>()

        // 按位置排序所有评论
        val sortedComments = allComments

        var i = 0
        while (i < sortedComments.size) {
            val currentComment = sortedComments[i]

            // 如果不是回复，则作为主评论处理
            if (!currentComment.isReply) {
                val replies = mutableListOf<CommentItem>()

                // 查找该主评论的所有回复
                var j = i + 1
                while (j < sortedComments.size) {
                    val nextComment = sortedComments[j]

                    // 如果是回复且在合理范围内，则归属于当前主评论
                    if (nextComment.isReply && isReplyToComment(nextComment, currentComment, sortedComments)) {
                        replies.add(nextComment)
                        j++
                    } else if (!nextComment.isReply) {
                        // 遇到下一个主评论，停止查找
                        break
                    } else {
                        j++
                    }
                }

                structuredComments.add(
                    StructuredComment(
                        mainComment = currentComment,
                        replies = replies.sortedBy { it.timeOrder }
                    )
                )
            }
            i++
        }

        return structuredComments.sortedByDescending { it.mainComment.timeOrder }
    }

    /**
     * 判断是否为指定评论的回复
     */
    private fun isReplyToComment(reply: CommentItem, mainComment: CommentItem, allComments: List<CommentItem>): Boolean {
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
            timeText.matches(Regex("\\d{2}-\\d{2}")) -> {
                // 假设是今年的日期，根据月日计算
                val (month, day) = timeText.split("-").map { it.toInt() }
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.MONTH, month - 1)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                calendar.timeInMillis
            }
            else -> now - 3600000L // 默认一小时前
        }
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

    // ================== 数据类和枚举定义 ==================

    /**
     * 评论类型枚举
     */
    enum class CommentType {
        TEXT_ONLY,      // 纯文字评论
        IMAGE_ONLY,     // 纯图片评论
        TEXT_AND_IMAGE, // 文字+图片评论
        UNKNOWN         // 未知类型
    }

    /**
     * 评论结构数据类
     */
    data class CommentStructure(
        val type: CommentType,
        val userNodes: List<ViewNode>,
        val textNodes: List<ViewNode>,
        val imageNodes: List<ViewNode>,
        val allChildren: List<ViewNode>
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
     * 评论项数据类 - 增加评论类型和图片数量字段
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
        val nodeReference: ViewNode,
        val commentType: CommentType = CommentType.TEXT_ONLY,
        val imageCount: Int = 0
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
                put("commentType", commentType.name)
                put("imageCount", imageCount)
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
                put("commentType", mainComment.commentType.name)
                put("imageCount", mainComment.imageCount)
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
                        put("commentType", reply.commentType.name)
                        put("imageCount", reply.imageCount)
                    })
                }
                put("replies", repliesArray)
                put("replyCount", repliesArray.length())
            }
        }
    }
}