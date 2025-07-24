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
            // 保存单张图片
            saveCurrentImage(1)
        }

//        // 先尝试直接提取内容
//        debugPrintAllScreenElements()
//        var title = extractNoteTitle()
//        var content = extractNoteContent()
//
//        // 如果没找到标题或内容，尝试下滑后再提取
//        if (title == "未找到标题" || content == "未找到内容") {
//            toast("第一次提取未找到完整内容，尝试下滑后重新提取")
//            swipeViewDown()
//            delay(3000)
//            debugPrintAllScreenElements()
//
//            // 重新提取
//            if (title == "未找到标题") {
//                title = extractNoteTitle()
//            }
//            if (content == "未找到内容") {
//                content = extractNoteContent()
//            }
//        }
//
//        toast("标题: ${title}")
//        delay(1000)
//        toast("内容: ${content.take(50)}...")
//        delay(1000)

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
        toast("开始查看并保存所有图片")
        
        // 保存第一张图片
        saveCurrentImage(1)
        
        for (i in 1 until totalCount) {
            Log.i(TAG, "准备切换到第${i + 1}张图片")
            delay(1500) // 确保上一次保存操作完全完成
            
            swipeImageRight()
            toast("滑动到第${i + 1}张图片")
            Log.i(TAG, "滑动完成，等待页面稳定...")
            
            delay(2000) // 等待滑动动画完成和UI更新
            
            // 验证滑动是否成功（检查图片计数器的变化）
            val currentImageIndicator = findAllWith { node ->
                val text = node.text?.toString()
                text?.matches(Regex("\\d+/${totalCount}")) == true
            }.firstOrNull()?.text?.toString()
            
            if (currentImageIndicator != null) {
                Log.i(TAG, "当前图片指示器: $currentImageIndicator")
                toast("当前位置: $currentImageIndicator")
            }
            
            // 保存当前图片
            saveCurrentImage(i + 1)
        }
        
        toast("所有图片保存完成")
    }
    
    private suspend fun analyzeImageElements() {
        Log.i(TAG, "=== 开始分析图片元素 ===")
        toast("分析当前图片元素...")
        
        // 专门查找图片FrameLayout容器
        val imageFrameLayouts = findAllWith { node ->
            node.className?.contains("FrameLayout") == true &&
            (node.contentDescription?.contains("图片") == true)
        }
        
        Log.i(TAG, "找到 ${imageFrameLayouts.size} 个图片FrameLayout容器")
        
        imageFrameLayouts.forEachIndexed { index, frameLayout ->
            val bounds = frameLayout.bounds
            val desc = frameLayout.desc() ?: ""
            val className = frameLayout.className ?: ""
            val resourceId = try { frameLayout.id ?: "" } catch (e: Exception) { "无法获取" }
            
            Log.i(TAG, "=== 图片FrameLayout ${index + 1} 详细分析 ===")
            Log.i(TAG, "FrameLayout: 类名='$className' ID='$resourceId' 描述='$desc'")
            Log.i(TAG, "位置=(${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}) 大小=${bounds.width()}x${bounds.height()}")
            Log.i(TAG, "子项数=${frameLayout.childCount} 可见=${frameLayout.isVisibleToUser}")
            
            toast("分析图片容器${index + 1}: ${frameLayout.childCount}个子项")
            
            // 递归分析FrameLayout的所有子项
            analyzeFrameLayoutChildren(frameLayout, 0)
        }
        
        // 如果没找到FrameLayout，再用其他方式查找图片元素
        if (imageFrameLayouts.isEmpty()) {
            Log.i(TAG, "未找到图片FrameLayout，使用备用方法查找图片...")
            
            val imageElements = findAllWith { node ->
                node.className?.contains("ImageView") == true ||
                node.className?.contains("Image") == true ||
                (node.contentDescription?.contains("图片") == true) ||
                (node.contentDescription?.contains("image") == true) ||
                (node.text?.toString()?.contains("图片") == true)
            }
            
            Log.i(TAG, "找到 ${imageElements.size} 个图片相关元素")
            
            imageElements.forEachIndexed { index, imageNode ->
                val bounds = imageNode.bounds
                val desc = imageNode.desc() ?: ""
                val text = imageNode.text?.toString() ?: ""
                val className = imageNode.className ?: ""
                val resourceId = try { imageNode.id ?: "" } catch (e: Exception) { "无法获取" }
                
                Log.i(TAG, "图片元素${index + 1}: 类名='$className' ID='$resourceId' 描述='$desc' 位置=${bounds}")
            }
        }
        
        Log.i(TAG, "=== 图片元素分析完成 ===")
        delay(1000)
    }
    
    private suspend fun analyzeFrameLayoutChildren(frameLayout: ViewNode, depth: Int) {
        val indent = "  ".repeat(depth)
        
        for (i in 0 until frameLayout.childCount) {
            val child = frameLayout.childAt(i)
            if (child != null) {
                val text = child.text?.toString() ?: ""
                val desc = child.desc() ?: ""
                val className = child.className ?: ""
                val bounds = child.bounds
                val resourceId = try { child.id ?: "" } catch (e: Exception) { "无法获取" }
                
                val childInfo = buildString {
                    append("${indent}子项[$i]: ")
                    append("类名='$className' ")
                    append("ID='$resourceId' ")
                    append("文本='$text' ")
                    append("描述='$desc' ")
                    append("位置=(${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}) ")
                    append("大小=${bounds.width()}x${bounds.height()} ")
                    append("子项数=${child.childCount} ")
                    append("可见=${child.isVisibleToUser} ")
                    append("可点击=${child.isClickable()}")
                    
                    // 特殊标记
                    when {
                        className.contains("ImageView") -> append(" [★ImageView★]")
                        className.contains("Image") -> append(" [★Image★]")
                        bounds.width() > 500 && bounds.height() > 500 -> append(" [大尺寸]")
                        child.childCount > 0 -> append(" [有子项]")
                    }
                }
                
                Log.i(TAG, childInfo)
                toast("子项${i + 1}: ${className}")
                delay(800)
                
                // 如果子项也有子项，继续递归（限制深度）
                if (depth < 2 && child.childCount > 0) {
                    analyzeFrameLayoutChildren(child, depth + 1)
                }
            }
        }
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
    
    private suspend fun saveCurrentImage(imageNumber: Int) {
        toast("正在保存第${imageNumber}张图片...")
        Log.i(TAG, "=== 开始保存图片 $imageNumber ===")
        
        try {
            // 多种策略查找图片容器
            var imageContainer: ViewNode? = null
            
            // 策略1: 查找包含"图片"描述的FrameLayout
            imageContainer = findAllWith { node ->
                node.className?.contains("FrameLayout") == true &&
                (node.contentDescription?.contains("图片") == true)
            }.firstOrNull()
            
            if (imageContainer == null) {
                Log.i(TAG, "策略1失败，尝试策略2...")
                // 策略2: 查找大尺寸的FrameLayout（可能是图片容器）
                imageContainer = findAllWith { node ->
                    node.className?.contains("FrameLayout") == true &&
                    node.isVisibleToUser
                }.sortedByDescending { it.bounds.width() * it.bounds.height() }
                .firstOrNull()
            }
            
            if (imageContainer == null) {
                Log.i(TAG, "策略2失败，尝试策略3...")
                // 策略3: 查找大尺寸的可见FrameLayout（无需检查子项）
                imageContainer = findAllWith { node ->
                    node.className?.contains("FrameLayout") == true &&
                    node.isVisibleToUser
                }.sortedByDescending { it.bounds.width() * it.bounds.height() }
                .firstOrNull()
            }
            
            if (imageContainer == null) {
                Log.i(TAG, "策略3失败，尝试策略4...")
                // 策略4: 直接查找最大的ImageView
                imageContainer = findAllWith { node ->
                    node.className?.contains("ImageView") == true &&
                    node.isVisibleToUser
                }.sortedByDescending { it.bounds.width() * it.bounds.height() }
                .firstOrNull()
            }
            
            if (imageContainer != null) {
                Log.i(TAG, "找到图片容器: 类名=${imageContainer.className} 描述='${imageContainer.desc()}' 大小=${imageContainer.bounds.width()}x${imageContainer.bounds.height()}")
                
                // 长按图片容器中央
                val bounds = imageContainer.bounds
                val centerX = bounds.centerX()
                val centerY = bounds.centerY()
                
                toast("长按图片位置: ($centerX, $centerY)")
                Log.i(TAG, "长按位置: ($centerX, $centerY) 容器大小: ${bounds.width()}x${bounds.height()}")
                
                // 执行长按操作
                longClick(centerX, centerY)
                
                // 等待弹窗出现
                delay(2000)
                toast("等待保存弹窗出现...")
                
                // 查找"保存"按钮
                val saveButton = findSaveButton()
                if (saveButton != null) {
                    saveButton.tryClick()
                    toast("第${imageNumber}张图片保存成功!")
                    Log.i(TAG, "图片 $imageNumber 保存成功")
                    delay(1500) // 等待保存完成
                } else {
                    toast("未找到保存按钮")
                    Log.w(TAG, "未找到保存按钮")
                    // 按返回键关闭可能的弹窗
                    back()
                    delay(1000)
                }
            } else {
                toast("未找到图片容器")
                Log.w(TAG, "所有策略都无法找到图片容器")
                
                // 调试: 列出当前屏幕上的所有FrameLayout和ImageView
                debugListImageElements()
            }
        } catch (e: Exception) {
            toast("保存图片失败: ${e.message}")
            Log.e(TAG, "保存图片失败", e)
        }
    }
    
    private fun hasImageViewChild(node: ViewNode): Boolean {
        for (i in 0 until node.childCount) {
            val child = node.childAt(i)
            if (child != null) {
                if (child.className?.contains("ImageView") == true) {
                    return true
                }
                // 递归查找子节点
                if (hasImageViewChild(child)) {
                    return true
                }
            }
        }
        return false
    }
    
    
    private suspend fun debugListImageElements() {
        Log.i(TAG, "=== 调试: 列出所有可能的图片相关元素 ===")
        
        // 列出所有FrameLayout
        val frameLayouts = findAllWith { node ->
            node.className?.contains("FrameLayout") == true &&
            node.isVisibleToUser
        }
        
        Log.i(TAG, "找到 ${frameLayouts.size} 个可见的FrameLayout:")
        frameLayouts.forEachIndexed { index, layout ->
            val bounds = layout.bounds
            val desc = layout.desc() ?: ""
            Log.i(TAG, "FrameLayout${index + 1}: 描述='$desc' 大小=${bounds.width()}x${bounds.height()} 位置=${bounds} 子项=${layout.childCount}")
        }
        
        // 列出所有ImageView
        val imageViews = findAllWith { node ->
            node.className?.contains("ImageView") == true &&
            node.isVisibleToUser
        }
        
        Log.i(TAG, "找到 ${imageViews.size} 个可见的ImageView:")
        imageViews.forEachIndexed { index, imageView ->
            val bounds = imageView.bounds
            val desc = imageView.desc() ?: ""
            Log.i(TAG, "ImageView${index + 1}: 描述='$desc' 大小=${bounds.width()}x${bounds.height()} 位置=${bounds}")
        }
    }
    
    private suspend fun findSaveButton(): ViewNode? {
        // 多种策略查找保存按钮
        val saveStrategies = listOf<suspend () -> ViewNode?>(
            { withText("保存").findFirst() },
            { withText("保存图片").findFirst() },
            { withText("保存到相册").findFirst() },
            { withText("下载").findFirst() },
            { containsText("保存").findFirst() },
            { containsText("下载").findFirst() },
            // 查找可点击的元素，描述包含保存
            { 
                findAllWith { node ->
                    node.isClickable() && 
                    (node.contentDescription?.contains("保存") == true ||
                     node.text?.toString()?.contains("保存") == true)
                }.firstOrNull()
            }
        )
        
        for (strategy in saveStrategies) {
            val button = strategy()
            if (button != null) {
                Log.i(TAG, "找到保存按钮: 文本='${button.text}' 描述='${button.desc()}' 类名=${button.className}")
                return button
            }
        }
        
        // 如果都没找到，列出当前屏幕上所有可点击的元素用于调试
        Log.i(TAG, "=== 保存按钮查找失败，列出所有可点击元素 ===")
        val clickableElements = findAllWith { node -> node.isClickable() }
        clickableElements.take(10).forEachIndexed { index, node ->
            val text = node.text?.toString() ?: ""
            val desc = node.desc() ?: ""
            Log.i(TAG, "可点击元素${index + 1}: 文本='$text' 描述='$desc' 类名=${node.className} 位置=${node.bounds}")
        }
        
        return null
    }
}