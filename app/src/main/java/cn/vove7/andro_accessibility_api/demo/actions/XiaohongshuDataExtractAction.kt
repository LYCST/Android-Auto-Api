package cn.vove7.andro_accessibility_api.demo.actions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import java.io.File
import java.io.FileWriter
import cn.vove7.andro_accessibility_api.demo.toast
import cn.vove7.auto.core.api.buildLayoutInfo
import cn.vove7.auto.core.api.containsText
import cn.vove7.auto.core.api.findAllWith
import cn.vove7.auto.core.api.swipe
import cn.vove7.auto.core.api.waitForApp
import cn.vove7.auto.core.api.withId
import cn.vove7.auto.core.api.withText
import cn.vove7.auto.core.api.withType
import cn.vove7.auto.core.requireAutoService
import cn.vove7.auto.core.viewfinder.SF
import cn.vove7.auto.core.viewfinder.ScreenTextFinder
import cn.vove7.auto.core.viewnode.ViewNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * 小红书数据抓取Action
 * 用于抓取小红书笔记内容、用户信息、评论等数据
 */
class XiaohongshuDataExtractAction : Action() {
    
    companion object {
        private const val TAG = "XiaohongshuExtract"
        private const val XIAOHONGSHU_PACKAGE = "com.xingin.xhs"
        private const val MAX_SCROLL_ATTEMPTS = 5
        private const val SCROLL_DELAY = 2000L
    }
    
    override val name: String = "小红书数据抓取"
    
    override suspend fun run(act: ComponentActivity) {
        requireAutoService()
        
        toast("启动小红书数据抓取...")
        act.startActivity(act.packageManager.getLaunchIntentForPackage(XIAOHONGSHU_PACKAGE))
        
        // 等待小红书应用启动
        if (!waitForApp(XIAOHONGSHU_PACKAGE, 8000)) {
            toast("小红书应用未启动，请先打开小红书")
            return
        }
        
        delay(2000) // 等待界面加载

//        toast("开始小红书抓取")
//        val extractedData = extractXiaohongshuData()
//
//        // 显示抓取结果
//        showDataResult(act, extractedData)
    }
    
    /**
     * 抓取小红书数据
     */
    private suspend fun extractXiaohongshuData(): JSONObject {
        val result = JSONObject()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        
        result.put("extractTime", timestamp)
        result.put("appPackage", XIAOHONGSHU_PACKAGE)
        
        try {
            // 分析当前页面类型
            val pageType = detectPageType()
            result.put("pageType", pageType)
            
            when (pageType) {
                "note_detail" -> {
                    result.put("noteData", extractNoteDetailData())
                }
                "user_profile" -> {
                    result.put("userData", extractUserProfileData())
                }
                "feed_list" -> {
                    result.put("feedData", extractFeedListData())
                }
                "search_result" -> {
                    result.put("searchData", extractSearchResultData())
                }
                else -> {
                    result.put("generalData", extractGeneralData())
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "数据抓取失败", e)
            result.put("error", e.message)
        }
        
        return result
    }
    
    /**
     * 检测当前页面类型
     */
    private suspend fun detectPageType(): String {
        // 通过界面元素判断页面类型
        return when {
            hasElement("点赞") && hasElement("收藏") && hasElement("评论") -> "note_detail"
            hasElement("关注") && hasElement("粉丝") && hasElement("获赞") -> "user_profile"
            hasElement("搜索") && hasElement("排序") -> "search_result"
            hasElement("推荐") && hasElement("关注") -> "feed_list"
            else -> "unknown"
        }
    }
    
    /**
     * 检查是否存在指定元素
     */
    private suspend fun hasElement(text: String): Boolean {
        return containsText(text).findFirst() != null
    }
    
    /**
     * 抓取笔记详情数据
     */
    private suspend fun extractNoteDetailData(): JSONObject {
        val noteData = JSONObject()
        
        // 抓取笔记标题
        val title = extractNoteTitle()
        noteData.put("title", title)
        
        // 抓取笔记内容
        val content = extractNoteContent()
        noteData.put("content", content)
        
        // 抓取用户信息
        val userInfo = extractUserInfo()
        noteData.put("author", userInfo)
        
        // 抓取互动数据
        val interactionData = extractInteractionData()
        noteData.put("interactions", interactionData)
        
        // 抓取评论数据
        val comments = extractComments()
        noteData.put("comments", comments)
        
        // 抓取标签
        val tags = extractTags()
        noteData.put("tags", tags)
        
        Log.i(TAG, "笔记详情数据抓取完成: $noteData")
        return noteData
    }
    
    /**
     * 抓取用户资料页数据
     */
    private suspend fun extractUserProfileData(): JSONObject {
        val userData = JSONObject()
        
        // 抓取用户基本信息
        val userInfo = extractUserInfo()
        userData.put("userInfo", userInfo)
        
        // 抓取关注、粉丝、获赞数
        val stats = extractUserStats()
        userData.put("stats", stats)
        
        // 抓取用户笔记列表
        val noteList = extractUserNoteList()
        userData.put("noteList", noteList)
        
        Log.i(TAG, "用户资料数据抓取完成: $userData")
        return userData
    }
    
    /**
     * 抓取信息流数据
     */
    private suspend fun extractFeedListData(): JSONObject {
        val feedData = JSONObject()
        val noteList = JSONArray()
        
        var scrollAttempts = 0
        val processedNotes = mutableSetOf<String>()
        
        while (scrollAttempts < MAX_SCROLL_ATTEMPTS) {
            // 获取当前屏幕上的笔记
            val currentNotes = extractCurrentFeedNotes()
            
            for (note in currentNotes) {
                val noteId = note.optString("id", "")
                if (noteId.isNotEmpty() && !processedNotes.contains(noteId)) {
                    noteList.put(note)
                    processedNotes.add(noteId)
                }
            }
            
            // 向下滑动加载更多
            swipeToLoadMore()
            delay(SCROLL_DELAY)
            scrollAttempts++
        }
        
        feedData.put("notes", noteList)
        feedData.put("totalCount", noteList.length())
        
        Log.i(TAG, "信息流数据抓取完成，共抓取${noteList.length()}条笔记")
        return feedData
    }
    
    /**
     * 抓取搜索结果数据
     */
    private suspend fun extractSearchResultData(): JSONObject {
        val searchData = JSONObject()
        
        // 抓取搜索关键词
        val keyword = extractSearchKeyword()
        searchData.put("keyword", keyword)
        
        // 抓取搜索结果
        val results = extractSearchResults()
        searchData.put("results", results)
        
        Log.i(TAG, "搜索结果数据抓取完成")
        return searchData
    }
    
    /**
     * 抓取通用数据（屏幕文本）
     */
    private suspend fun extractGeneralData(): JSONObject {
        val generalData = JSONObject()
        
        // 提取屏幕文本
        val screenText = ScreenTextFinder().find()
        generalData.put("screenText", JSONArray(screenText))
        
        // 提取可点击元素
        val clickableElements = extractClickableElements()
        generalData.put("clickableElements", clickableElements)
        
        // 提取布局信息
        val layoutInfo = buildLayoutInfo()
        generalData.put("layoutInfo", layoutInfo)
        
        Log.i(TAG, "通用数据抓取完成")
        return generalData
    }
    
    /**
     * 抓取笔记标题
     */
    private suspend fun extractNoteTitle(): String {
        // 尝试多种方式获取标题
        val titleNodes = listOf(
            withId("title").findFirst(),
            withType("TextView").findFirst(),
            containsText("标题").findFirst()
        )
        
        for (node in titleNodes) {
            node?.let { 
                val text = it.text?.toString()
                if (!text.isNullOrBlank() && text.length > 3) {
                    return text
                }
            }
        }
        
        return ""
    }
    
    /**
     * 抓取笔记内容
     */
    private suspend fun extractNoteContent(): String {
        val contentBuilder = StringBuilder()
        
        // 提取所有文本内容
        val textNodes = findAllWith { node ->
            node.text?.toString()?.let { text ->
                text.length > 10 && !text.contains("点赞") && !text.contains("收藏") && !text.contains("评论")
            } ?: false
        }
        
        for (node in textNodes) {
            val text = node.text?.toString()
            if (!text.isNullOrBlank()) {
                contentBuilder.append(text).append("\n")
            }
        }
        
        return contentBuilder.toString().trim()
    }
    
    /**
     * 抓取用户信息
     */
    private suspend fun extractUserInfo(): JSONObject {
        val userInfo = JSONObject()
        
        // 抓取用户名
        val username = extractUsername()
        userInfo.put("username", username)
        
        // 抓取用户简介
        val bio = extractUserBio()
        userInfo.put("bio", bio)
        
        // 抓取用户等级
        val level = extractUserLevel()
        userInfo.put("level", level)
        
        return userInfo
    }
    
    /**
     * 抓取互动数据
     */
    private suspend fun extractInteractionData(): JSONObject {
        val interactions = JSONObject()
        
        // 提取点赞数
        val likesCount = extractCount("点赞")
        interactions.put("likes", likesCount)
        
        // 提取收藏数
        val collectsCount = extractCount("收藏")
        interactions.put("collects", collectsCount)
        
        // 提取评论数
        val commentsCount = extractCount("评论")
        interactions.put("comments", commentsCount)
        
        // 提取分享数
        val sharesCount = extractCount("分享")
        interactions.put("shares", sharesCount)
        
        return interactions
    }
    
    /**
     * 抓取评论数据
     */
    private suspend fun extractComments(): JSONArray {
        val comments = JSONArray()
        
        // 尝试点击评论按钮展开评论
        val commentButton = containsText("评论").findFirst()
        commentButton?.tryClick()
        
        delay(1000) // 等待评论加载
        
        // 抓取评论列表
        val commentNodes = findAllWith { node ->
            node.text?.toString()?.let { text ->
                text.length > 5 && !text.contains("点赞") && !text.contains("回复")
            } ?: false
        }
        
        for (node in commentNodes) {
            val comment = JSONObject()
            val text = node.text?.toString()
            if (!text.isNullOrBlank()) {
                comment.put("content", text)
                comments.put(comment)
            }
        }
        
        return comments
    }
    
    /**
     * 抓取标签
     */
    private suspend fun extractTags(): JSONArray {
        val tags = JSONArray()
        
        // 查找包含#的文本（标签）
        val tagNodes = findAllWith { node ->
            node.text?.toString()?.contains("#") == true
        }
        
        for (node in tagNodes) {
            val text = node.text?.toString()
            if (!text.isNullOrBlank()) {
                val tagMatches = Regex("#([^#\\s]+)").findAll(text)
                for (match in tagMatches) {
                    tags.put(match.groupValues[1])
                }
            }
        }
        
        return tags
    }
    
    /**
     * 抓取用户统计数据
     */
    private suspend fun extractUserStats(): JSONObject {
        val stats = JSONObject()
        
        // 抓取关注数
        val followingCount = extractUserStatCount("关注")
        stats.put("following", followingCount)
        
        // 抓取粉丝数
        val followersCount = extractUserStatCount("粉丝")
        stats.put("followers", followersCount)
        
        // 抓取获赞数
        val likesCount = extractUserStatCount("获赞")
        stats.put("totalLikes", likesCount)
        
        return stats
    }
    
    /**
     * 抓取用户笔记列表
     */
    private suspend fun extractUserNoteList(): JSONArray {
        val noteList = JSONArray()
        
        // 实现笔记列表抓取逻辑
        // 这里可以滚动页面获取更多笔记
        
        return noteList
    }
    
    /**
     * 抓取当前信息流笔记
     */
    private suspend fun extractCurrentFeedNotes(): List<JSONObject> {
        val notes = mutableListOf<JSONObject>()
        
        // 实现信息流笔记抓取逻辑
        
        return notes
    }
    
    /**
     * 滑动加载更多
     */
    private suspend fun swipeToLoadMore() {
        // 获取屏幕尺寸并向上滑动
        val screenHeight = ViewNode.getRoot().bounds.height()
        val startY = screenHeight * 0.8
        val endY = screenHeight * 0.2
        
        swipe(500, startY.toInt(), 500, endY.toInt(), 1000)
    }
    
    /**
     * 抓取搜索关键词
     */
    private suspend fun extractSearchKeyword(): String {
        // 从搜索框或搜索结果页面获取关键词
        return ""
    }
    
    /**
     * 抓取搜索结果
     */
    private suspend fun extractSearchResults(): JSONArray {
        val results = JSONArray()
        
        // 实现搜索结果抓取逻辑
        
        return results
    }
    
    /**
     * 抓取可点击元素
     */
    private suspend fun extractClickableElements(): JSONArray {
        val elements = JSONArray()
        
        val clickableNodes = findAllWith { node ->
            node.isClickable && !node.text.isNullOrBlank()
        }
        
        for (node in clickableNodes) {
            val element = JSONObject()
            element.put("text", node.text?.toString())
            element.put("bounds", node.bounds.toString())
            elements.put(element)
        }
        
        return elements
    }
    
    /**
     * 抓取用户名
     */
    private suspend fun extractUsername(): String {
        // 实现用户名抓取逻辑
        return ""
    }
    
    /**
     * 抓取用户简介
     */
    private suspend fun extractUserBio(): String {
        // 实现用户简介抓取逻辑
        return ""
    }
    
    /**
     * 抓取用户等级
     */
    private suspend fun extractUserLevel(): String {
        // 实现用户等级抓取逻辑
        return ""
    }
    
    /**
     * 抓取数量（通用方法）
     */
    private suspend fun extractCount(type: String): Int {
        val node = containsText(type).findFirst()
        return node?.let { 
            extractNumberFromText(it.text?.toString() ?: "")
        } ?: 0
    }
    
    /**
     * 抓取用户统计数量
     */
    private suspend fun extractUserStatCount(type: String): Int {
        val node = containsText(type).findFirst()
        return node?.let { 
            extractNumberFromText(it.text?.toString() ?: "")
        } ?: 0
    }
    
    /**
     * 从文本中提取数字
     */
    private fun extractNumberFromText(text: String): Int {
        val numberRegex = Regex("\\d+")
        val match = numberRegex.find(text)
        return match?.value?.toIntOrNull() ?: 0
    }
    
    /**
     * 复制文本到剪贴板
     */
    private fun copyToClipboard(context: Context, text: String) {
        try {
            // 检查文本长度，如果超过100KB则截断
            val maxLength = 100 * 1024 // 100KB
            val textToCopy = if (text.length > maxLength) {
                text.substring(0, maxLength) + "\n\n[数据过长，已截断...]"
            } else {
                text
            }
            
            // 使用Handler确保在主线程执行
            (context as? ComponentActivity)?.runOnUiThread {
                try {
                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    if (clipboardManager != null) {
                        val clipData = ClipData.newPlainText("小红书数据", textToCopy)
                        clipboardManager.setPrimaryClip(clipData)
                        
                        // 验证复制是否成功
                        val clipText = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
                        if (clipText != null && clipText.isNotEmpty()) {
                            if (text.length > maxLength) {
                                toast("数据已复制到剪贴板~(已截断)")
                            } else {
                                toast("数据已复制到剪贴板~")
                            }
                        } else {
                            toast("复制失败：剪贴板为空")
                        }
                    } else {
                        toast("复制失败：无法获取剪贴板服务")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "复制到剪贴板失败", e)
                    toast("复制失败：${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "复制到剪贴板失败", e)
            toast("复制失败：${e.message}")
        }
    }

    /**
     * 保存数据到文件
     */
    private fun saveToFile(text: String) {
        try {
            // 生成文件名，包含时间戳
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "xiaohongshu_data_$timestamp.json"
            
            // 使用指定的Misc目录
            val miscDir = File("/mnt/shared/Misc")
            val file = File(miscDir, fileName)
            
            // 确保目录存在
            if (!miscDir.exists()) {
                miscDir.mkdirs()
            }
            
            // 写入文件
            FileWriter(file).use { writer ->
                writer.write(text)
            }
            
            toast("数据已保存至：${file.absolutePath}")
            Log.i(TAG, "数据已保存至：${file.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "保存文件失败", e)
            toast("保存失败：${e.message}")
        }
    }
    
    /**
     * 显示数据抓取结果
     */
    private suspend fun showDataResult(act: ComponentActivity, data: JSONObject) {
        val formattedData = data.toString(2) // 格式化JSON显示
        
        withContext(Dispatchers.Main) {
            AlertDialog.Builder(act).apply {
                setTitle("小红书数据抓取结果")
                setMessage(formattedData)
                setPositiveButton("确定") { dialog, _ ->
                    dialog.dismiss()
                }
                setNegativeButton("复制") { _, _ ->
                    copyToClipboard(act, formattedData)
                }
                setNeutralButton("保存") { _, _ ->
                    saveToFile(formattedData)
                }
                show()
            }
        }
        
        Log.i(TAG, "数据抓取完成: $formattedData")
    }
}