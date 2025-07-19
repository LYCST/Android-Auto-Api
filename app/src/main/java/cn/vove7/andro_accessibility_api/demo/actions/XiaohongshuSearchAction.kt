package cn.vove7.andro_accessibility_api.demo.actions

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import cn.vove7.andro_accessibility_api.demo.toast
import cn.vove7.auto.core.AutoApi
import cn.vove7.auto.core.api.*
import cn.vove7.auto.core.requireAutoService
import cn.vove7.auto.core.viewnode.ViewNode
import kotlinx.coroutines.delay

class XiaohongshuSearchAction : Action() {
    
    companion object {
        private const val XIAOHONGSHU_PACKAGE = "com.xingin.xhs"
        private const val SEARCH_KEYWORD = "钻戒"
        private const val TAG = "XiaohongshuSearchAction"
    }
    
    override val name: String = "小红书搜索钻戒"
    
    override suspend fun run(act: ComponentActivity) {
        requireAutoService()
        
        toast("启动小红书并搜索：${SEARCH_KEYWORD}")
        
        act.startActivity(act.packageManager.getLaunchIntentForPackage(XIAOHONGSHU_PACKAGE))
        
        if (!waitForApp(XIAOHONGSHU_PACKAGE, 8000)) {
            toast("小红书应用未启动，请先安装小红书")
            return
        }
        
        delay(3000)
        
        val searchButton = findSearchButton()
        if (searchButton == null) {
            toast("未找到搜索按钮")
            return
        }
        
        searchButton.tryClick()
        toast("点击搜索按钮")
        delay(2000)
        
        val searchInput = findSearchInput()
        if (searchInput == null) {
            toast("未找到搜索输入框")
            return
        }
        
        searchInput.tryClick()
        delay(500)
        editor().require().apply {
            text = SEARCH_KEYWORD
        }
        toast("输入关键字：${SEARCH_KEYWORD}")
        delay(1000)
        
        val confirmSearch = findConfirmSearchButton()
        if (confirmSearch != null) {
            confirmSearch.tryClick()
            toast("点击搜索确认")
        } else {
            AutoApi.sendKeyCode(KeyEvent.KEYCODE_ENTER)
            toast("按Enter键搜索")
        }
        
        delay(3000)
        toast("搜索完成，等待结果刷新")
        
        val firstItem = findFirstSearchResult()
        if (firstItem == null) {
            toast("未找到搜索结果项目")
            return
        }
        
        firstItem.tryClick()
        toast("点击第一个搜索结果")
        delay(3000)
        toast("等待进入笔记详情页面")
        
        extractNoteData()
    }
    
    private suspend fun findSearchButton(): ViewNode? {
        // 基于调试结果，搜索按钮的特征:
        // 描述='搜索', 类名='android.widget.Button', ID包含obfuscated
        val searchButton = findAllWith { node ->
            node.contentDescription == "搜索" &&
            node.className?.contains("Button") == true &&
            node.isClickable()
        }.firstOrNull()
        
        if (searchButton != null) {
            toast("找到搜索按钮: ${searchButton.desc()}")
            return searchButton
        }
        
        // 备用搜索策略
        return listOf(
            withText("搜索").findFirst(),
            withId("search").findFirst(),
            containsText("搜索").findFirst()
        ).firstOrNull { it != null }
    }
    
    private suspend fun debugPrintAllButtons() {
        toast("开始分析界面按钮...")
        delay(1000)
        
        val allButtons = findAllWith { node ->
            node.isClickable() || 
            node.className?.contains("Button") == true ||
            node.className?.contains("ImageView") == true ||
            node.className?.contains("TextView") == true
        }
        
        allButtons.forEachIndexed { index, node ->
            val info = buildString {
                append("按钮${index + 1}: ")
                append("文本='${node.text?.toString() ?: ""}' ")
                append("描述='${node.desc() ?: ""}' ")
                append("类名='${node.className?.toString() ?: ""}' ")
                append("ID='${try { node.id ?: "" } catch (e: Exception) { "null" }}' ")
                append("位置=(${node.bounds.left},${node.bounds.top},${node.bounds.right},${node.bounds.bottom}) ")

                append("可点击=${node.isClickable()}")
            }
            toast(info)
            Log.i(TAG, "debugPrintAllButtons: $info");
            delay(2000) // 延长显示时间以便阅读
        }

        toast("按钮分析完成，共找到${allButtons.size}个元素")
        delay(2000)
    }

    private suspend fun findSearchInput() = listOf(
        withType("EditText").findFirst(),
        withId("search_input").findFirst(),
        withId("edit_text").findFirst(),
        containsText("请输入").findFirst()
    ).firstOrNull { it != null }

    private suspend fun findConfirmSearchButton() = listOf(
        withText("搜索").findFirst(),
        withText("确认").findFirst(),
        withId("search_btn").findFirst()
    ).firstOrNull { it != null }

    private suspend fun findFirstSearchResult(): ViewNode? {
        val clickableItems = findAllWith { node ->
            node.isClickable() &&
            node.text?.toString()?.contains("搜索") != true &&
            node.text?.toString()?.contains("筛选") != true &&
            node.text?.toString()?.contains("排序") != true
        }

        return clickableItems.firstOrNull() ?: withType("ImageView").findFirst()
    }

    private suspend fun extractNoteData() {
        toast("开始抓取笔记数据")

        // 先打印所有屏幕元素用于调试
        debugPrintAllScreenElements()

        val title = extractNoteTitle()
        val content = extractNoteContent()
        val imageCount = extractImageCount()
        val isVideo = detectVideoNote()

        toast("标题: ${title}")
        delay(1000)
        toast("内容: ${content.take(50)}...")
        delay(1000)

        if (isVideo) {
            toast("检测到视频笔记")
        } else if (imageCount > 1) {
            toast("检测到多张图片(${imageCount}张)，开始滑动查看")
            extractMultipleImages(imageCount)
        } else {
            toast("单张图片笔记")
        }

        toast("数据抓取完成")
    }

    private suspend fun debugPrintAllScreenElements() {
        toast("开始分析笔记页面所有文本元素...")
        delay(1000)

        val allTextElements = findAllWith { node ->
            !node.text?.toString().isNullOrBlank() ||
            !node.contentDescription.isNullOrBlank()
        }

        Log.i(TAG, "=== 笔记页面所有元素分析 ===")
        Log.i(TAG, "总共找到 ${allTextElements.size} 个文本元素")

        allTextElements.forEachIndexed { index, node ->
            val info = buildString {
                append("元素${index + 1}: ")
                append("文本='${node.text?.toString() ?: ""}' ")
                append("描述='${node.desc() ?: ""}' ")
                append("类名='${node.className?.toString() ?: ""}' ")
                append("ID='${try { node.id ?: "" } catch (e: Exception) { "null" }}' ")
                append("文本长度=${node.text?.toString()?.length ?: 0} ")
                append("可点击=${node.isClickable()}")
            }
            Log.i(TAG, info)
            
            // 对于长度在合理范围内的文本也在 toast 中显示
            val text = node.text?.toString() ?: ""
            if (text.isNotBlank() && text.length in 5..100) {
                toast("文本${index + 1}: ${text}")
                delay(1500)
            }
        }
        
        Log.i(TAG, "=== 元素分析完成 ===")
        toast("屏幕元素分析完成，请查看日志")
        delay(2000)
    }
    
    private suspend fun extractNoteTitle(): String {
        val titleNodes = findAllWith { node ->
            val text = node.text?.toString()
            !text.isNullOrBlank() && 
            text.length > 5 && 
            text.length < 100 &&
            !text.contains("点赞") &&
            !text.contains("收藏") &&
            !text.contains("评论") &&
            !text.contains("分享")
        }
        
        return titleNodes.firstOrNull()?.text?.toString() ?: "未找到标题"
    }
    
    private suspend fun extractNoteContent(): String {
        val contentBuilder = StringBuilder()
        
        val textNodes = findAllWith { node ->
            val text = node.text?.toString()
            !text.isNullOrBlank() && 
            text.length > 10 &&
            !text.contains("点赞") &&
            !text.contains("收藏") &&
            !text.contains("评论") &&
            !text.contains("分享") &&
            !text.contains("关注")
        }
        
        textNodes.forEach { node ->
            val text = node.text?.toString()
            if (!text.isNullOrBlank()) {
                contentBuilder.append(text).append("\n")
            }
        }
        
        return contentBuilder.toString().trim()
    }
    
    private suspend fun extractImageCount(): Int {
        val imageIndicators = findAllWith { node ->
            val text = node.text?.toString()
            text?.matches(Regex("\\d+/\\d+")) == true
        }
        
        if (imageIndicators.isNotEmpty()) {
            val indicator = imageIndicators.first().text?.toString()
            val parts = indicator?.split("/")
            return parts?.get(1)?.toIntOrNull() ?: 1
        }
        
        val images = findAllWith { node ->
            node.className?.contains("ImageView") == true ||
            node.className?.contains("Image") == true
        }
        
        return maxOf(1, images.size)
    }
    
    private suspend fun detectVideoNote(): Boolean {
        val videoElements = findAllWith { node ->
            val text = node.text?.toString()
            val className = node.className?.toString()
            
            text?.contains("播放") == true ||
            text?.contains("视频") == true ||
            className?.contains("Video") == true ||
            className?.contains("Player") == true
        }
        
        return videoElements.isNotEmpty()
    }
    
    private suspend fun extractMultipleImages(totalCount: Int) {
        toast("开始查看所有图片")
        
        for (i in 1 until totalCount) {
            delay(1000)
            swipeImageRight()
            toast("查看第${i + 1}张图片")
            delay(1500)
        }
        
        toast("所有图片查看完成")
    }
    
    private suspend fun swipeImageRight() {
        // 获取实际屏幕尺寸
        val (screenWidth, screenHeight) = getScreenSize()
        
        toast("屏幕尺寸: ${screenWidth}x${screenHeight}")
        
        val centerY = screenHeight / 3
        val startX = screenWidth * 9 / 10
        val endX = screenWidth / 10
        
        swipe(startX, centerY, endX, centerY, 500)
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
}