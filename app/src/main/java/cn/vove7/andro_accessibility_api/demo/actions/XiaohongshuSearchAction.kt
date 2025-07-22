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
        val imageCount = extractImageCount()
        val isVideo = detectVideoNote()
        // 先打印所有屏幕元素用于调试
        debugPrintAllScreenElements()

        if (isVideo) {
            toast("检测到视频笔记")
        } else if (imageCount > 1) {
            toast("检测到多张图片(${imageCount}张)，开始滑动查看")
            extractMultipleImages(imageCount)
        } else {
            toast("单张图片笔记")
        }

        // 先尝试直接提取内容
        debugPrintAllScreenElements()
        var title = extractNoteTitle()
        var content = extractNoteContent()

        // 如果没找到标题或内容，尝试下滑后再提取
        if (title == "未找到标题" || content == "未找到内容") {
            toast("第一次提取未找到完整内容，尝试下滑后重新提取")
            swipeViewDown()
            delay(3000)
            debugPrintAllScreenElements()

            // 重新提取
            if (title == "未找到标题") {
                title = extractNoteTitle()
            }
            if (content == "未找到内容") {
                content = extractNoteContent()
            }
        }

        toast("标题: ${title}")
        delay(1000)
        toast("内容: ${content.take(50)}...")
        delay(1000)

        toast("数据抓取完成")
    }

    private suspend fun debugPrintAllScreenElements() {
        toast("开始深度递归分析笔记页面所有元素...")
        delay(1000)

        // 1. 特别关注 RecyclerView 和其子项的递归分析
        Log.i(TAG, "=== 小红书笔记页面深度递归分析 ===")
        
        // 2. 先找到所有 RecyclerView
        val recyclerViews = findAllWith { node ->
            node.className?.contains("RecyclerView") == true
        }
        
        Log.i(TAG, "找到 ${recyclerViews.size} 个 RecyclerView")
        
        // 3. 递归分析每个 RecyclerView 的内容
        recyclerViews.forEachIndexed { rvIndex, recyclerView ->
            Log.i(TAG, "=== RecyclerView ${rvIndex + 1} 分析 ===")
            Log.i(TAG, "RecyclerView: ID=${recyclerView.id} 位置=${recyclerView.bounds} 子项数=${recyclerView.childCount}")
            
            // 递归遍历 RecyclerView 的所有子项
            analyzeRecyclerViewChildren(recyclerView, 0, "RV${rvIndex + 1}")
        }
        
        // 4. 全局所有元素分析（作为补充）
        val allElements = findAllWith { node -> true }
        Log.i(TAG, "页面总元素数量: ${allElements.size}")
        
        // 5. 专门寻找可能的标题和内容元素
        val potentialContent = findAllWith { node ->
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?: ""
            (text.isNotBlank() && text.length > 5) || desc.isNotBlank()
        }.sortedWith(compareBy(
            { node -> if (node.bounds.top < 800) 0 else 1 }, // 优先页面上方
            { node -> -(node.text?.toString()?.length ?: 0) } // 然后按文本长度
        ))

        Log.i(TAG, "=== 潜在内容元素分析 ===")
        potentialContent.take(20).forEachIndexed { index, node ->
            val text = node.text?.toString() ?: ""
            val desc = node.desc() ?: ""
            val bounds = node.bounds
            
            val info = buildString {
                append("潜在内容${index + 1}: ")
                append("文本='$text' ")
                append("描述='$desc' ")
                append("类名='${node.className}' ")
                append("ID='${try { node.id ?: "" } catch (e: Exception) { "null" }}' ")
                append("长度=${text.length} ")
                append("位置=(${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}) ")
                append("可见=${node.isVisibleToUser}")
                
                // 智能标记
                when {
                    text.length in 10..50 && bounds.top < 800 && !text.contains("点赞|收藏|评论|分享".toRegex()) -> 
                        append(" [疑似标题]")
                    text.length > 50 && !text.contains("点赞|收藏|评论|分享".toRegex()) -> 
                        append(" [疑似正文]")
                    text.matches(Regex("\\d+/\\d+")) -> append(" [图片计数]")
                    text.contains("点赞|收藏|评论|分享".toRegex()) -> append(" [交互按钮]")
                }
            }
            Log.i(TAG, info)
            
            // Toast 显示重要内容
            if (text.length > 8 && !text.contains("点赞|收藏|评论|分享".toRegex())) {
                toast("发现内容: $text")
                delay(2000)
            }
        }
        
        Log.i(TAG, "=== 深度递归分析完成 ===")
        toast("深度递归分析完成，请查看日志")
        delay(2000)
    }
    
    private suspend fun analyzeRecyclerViewChildren(parent: ViewNode, depth: Int, prefix: String) {
        val indent = "  ".repeat(depth)
        
        for (i in 0 until parent.childCount) {
            val child = parent.childAt(i)
            if (child != null) {
                val text = child.text?.toString() ?: ""
                val desc = child.desc() ?: ""
                val className = child.className ?: ""
                
                val info = buildString {
                    append("${prefix}${indent}子项[$i]: ")
                    append("文本='$text' ")
                    append("描述='$desc' ")
                    append("类名='$className' ")
                    append("ID='${try { child.id ?: "" } catch (e: Exception) { "null" }}' ")
                    append("子项数=${child.childCount} ")
                    append("位置=${child.bounds}")
                    
                    // 特别标记可能的标题和正文
                    if (text.length in 10..100 && !text.contains("点赞|收藏|评论|分享".toRegex())) {
                        append(" [★可能是标题或正文★]")
                    }
                }
                Log.i(TAG, info)
                
                // 如果有重要文本内容，在 toast 中显示
                if (text.length > 8 && !text.contains("点赞|收藏|评论|分享".toRegex())) {
                    toast("${prefix}发现: $text")
                    delay(1500)
                }
                
                // 递归遍历子节点（最多3层深度，避免过深）
                if (depth < 3 && child.childCount > 0) {
                    analyzeRecyclerViewChildren(child, depth + 1, prefix)
                }
            }
        }
    }
    
    private suspend fun extractNoteTitle(): String {
        // 基于发现的规律：标题在深度17的TextView中，通常较短，位置靠上
        Log.i(TAG, "开始深度递归查找标题...")
        
        val rootNode = ViewNode.getRoot()
        val titleCandidates = findTextAtSpecificDepth(rootNode, 0, 17)
        
        Log.i(TAG, "在深度17找到 ${titleCandidates.size} 个TextView元素")
        
        // 根据用户提供的标题特征进行过滤和排序
        val titleNodes = titleCandidates.filter { candidate ->
            val text = candidate.text
            val bounds = candidate.bounds
            
            !text.isNullOrBlank()
        }.sortedWith(compareBy(
            { it.bounds.top },  // 按位置从上到下
            { it.text?.length ?: 0 }  // 然后按长度从短到长
        ))
        
        titleNodes.forEach { candidate ->
            val text = candidate.text ?: ""
            val bounds = candidate.bounds
            Log.i(TAG, "候选标题[深度${candidate.depth}]: '$text' 位置:(${bounds.left},${bounds.top}) 长度:${text.length}")
        }
        
        val title = titleNodes.firstOrNull()?.text ?: "未找到标题"
        Log.i(TAG, "最终选择标题: $title")
        return title
    }
    
    private suspend fun extractNoteContent(): String {
        // 基于发现的规律：内容在深度17的TextView中，通常较长，位置在标题下方
        Log.i(TAG, "开始深度递归查找内容...")
        
        val rootNode = ViewNode.getRoot()
        val contentCandidates = findTextAtSpecificDepth(rootNode, 0, 17)
        
        Log.i(TAG, "在深度17找到 ${contentCandidates.size} 个TextView元素用于内容查找")

        
        // 根据用户提供的内容特征进行过滤和排序
        val contentNodes = contentCandidates.filter { candidate ->
            val text = candidate.text
            val bounds = candidate.bounds
            
            !text.isNullOrBlank()
        }.sortedWith(compareBy(
            { it.bounds.top }  // 按位置从上到下，取最靠上的内容
        ))

        val content = contentNodes.lastOrNull()?.text ?: "未找到内容"
        Log.i(TAG, "最终选择内容: ${content.take(100)}...")
        return content
    }
    
    private suspend fun findTitleInRecyclerView(recyclerView: ViewNode): String {
        return findTextInNodeRecursively(recyclerView, 0) { text ->
            text.length in 8..60 && 
            !text.contains("点赞|收藏|评论|分享|关注".toRegex())
        }.firstOrNull() ?: ""
    }
    
    private suspend fun findContentInRecyclerView(recyclerView: ViewNode): String {
        val contents = findTextInNodeRecursively(recyclerView, 0) { text ->
            text.length > 20 && 
            !text.contains("点赞|收藏|评论|分享|关注".toRegex())
        }
        return contents.joinToString("\n")
    }
    
    private fun findTextInNodeRecursively(node: ViewNode, depth: Int, filter: (String) -> Boolean): List<String> {
        val results = mutableListOf<String>()
        
        // 检查当前节点
        val text = node.text?.toString()
        if (!text.isNullOrBlank() && filter(text)) {
            results.add(text)
        }
        
        // 递归检查子节点（限制深度避免过深）
        if (depth < 4) {
            for (i in 0 until node.childCount) {
                val child = node.childAt(i)
                if (child != null) {
                    results.addAll(findTextInNodeRecursively(child, depth + 1, filter))
                }
            }
        }
        
        return results
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

    private suspend fun swipeViewDown() {
        // 获取实际屏幕尺寸
        val (screenWidth, screenHeight) = getScreenSize()

        toast("屏幕尺寸: ${screenWidth}x${screenHeight}")

        val centerX = screenWidth / 2
        val startY = screenHeight * 7 / 10
        val endY = screenHeight * 3 / 10

        swipe(centerX, startY, centerX , endY,  500)
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
    
    // 数据类，用于存储深度和节点信息
    data class DepthTextInfo(
        val text: String?,
        val bounds: android.graphics.Rect,
        val depth: Int,
        val className: String?
    )
    
    // 递归查找指定深度的TextView元素
    private fun findTextAtSpecificDepth(node: ViewNode, currentDepth: Int, targetDepth: Int): List<DepthTextInfo> {
        val results = mutableListOf<DepthTextInfo>()
        
        // 如果当前深度等于目标深度，检查是否是TextView
        if (currentDepth == targetDepth) {
            if (node.className?.contains("TextView") == true) {
                val text = node.text?.toString()
                if (!text.isNullOrBlank() && node.isClickable()) {
                    Log.i(TAG, " ${node.toString()} ")
                    results.add(DepthTextInfo(
                        text = text,
                        bounds = node.bounds,
                        depth = currentDepth,
                        className = node.className
                    ))
                }
            }
        } else if (currentDepth < targetDepth) {
            // 继续递归查找子节点
            for (i in 0 until node.childCount) {
                val child = node.childAt(i)
                if (child != null) {
                    results.addAll(findTextAtSpecificDepth(child, currentDepth + 1, targetDepth))
                }
            }
        }
        
        return results
    }
}