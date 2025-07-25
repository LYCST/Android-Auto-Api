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
import cn.vove7.andro_accessibility_api.demo.xiaohongshu.XiaohongshuCommentExtractor



class XiaohongshuUserSearchAction : Action() {

    companion object {
        private const val XIAOHONGSHU_PACKAGE = "com.xingin.xhs"
        private const val TAG = "XiaohongshuUserSearchAction"
    }

    override val name: String = "小红书用户搜索抓取"

    override suspend fun run(act: ComponentActivity) {
        try {
            requireAutoService()

            toast("启动小红书用户搜索功能")
            Log.i(TAG, "=== 开始执行小红书用户搜索抓取 ===")

            wakeUpXiaohongshu(act)
            delay(3000)

            clickSearchButton()
            delay(2000)

            inputUserId("726569132")
            delay(1000)

            executeSearch()
            delay(2000)

            selectUserCategory()
            delay(2000)

            clickFirstUser()
            delay(2000)
            Log.i(TAG, "=== 开始抓取 ===")
            extractUserNotes()

            Log.i(TAG, "=== 小红书用户搜索抓取完成 ===")
            toast("抓取任务完成")

        } catch (e: Exception) {
            Log.e(TAG, "程序执行过程中发生异常", e)
            toast("程序异常: ${e.message}")

            // 尝试恢复到主页面
            try {
                repeat(3) { back(); delay(1000) }
                toast("已尝试返回主页面")
            } catch (ex: Exception) {
                Log.e(TAG, "恢复操作失败", ex)
            }
        }
    }

    private suspend fun wakeUpXiaohongshu(act: ComponentActivity) {
        toast("启动小红书应用")

        act.startActivity(act.packageManager.getLaunchIntentForPackage(XIAOHONGSHU_PACKAGE))

        if (!waitForApp(XIAOHONGSHU_PACKAGE, 8000)) {
            toast("小红书应用未启动，请先安装小红书")
            throw RuntimeException("小红书应用未启动")
        }

        toast("小红书启动成功")
    }

    private suspend fun clickSearchButton() {
        toast("查找并点击搜索按钮")

        val searchButton = findSearchButton()
        if (searchButton == null) {
            toast("未找到搜索按钮")
            throw RuntimeException("未找到搜索按钮")
        }

        searchButton.tryClick()
        toast("点击搜索按钮成功")
    }

    private suspend fun findSearchButton(): ViewNode? {
        val searchButton = findAllWith { node ->
            node.contentDescription == "搜索" &&
            node.className?.contains("Button") == true &&
            node.isClickable()
        }.firstOrNull()

        if (searchButton != null) {
            toast("找到搜索按钮: ${searchButton.desc()}")
            return searchButton
        }

        return listOf(
            withText("搜索").findFirst(),
            withId("search").findFirst(),
            containsText("搜索").findFirst()
        ).firstOrNull { it != null }
    }

    private suspend fun inputUserId(userId: String) {
        toast("输入用户ID: $userId")

        val searchInput = findSearchInput()
        if (searchInput == null) {
            toast("未找到搜索输入框")
            throw RuntimeException("未找到搜索输入框")
        }

        searchInput.tryClick()
        delay(500)
        editor().require().apply {
            text = userId
        }
        toast("输入用户ID完成: $userId")
    }

    private suspend fun findSearchInput() = listOf(
        withType("EditText").findFirst(),
        withId("search_input").findFirst(),
        withId("edit_text").findFirst(),
        containsText("请输入").findFirst()
    ).firstOrNull { it != null }

    private suspend fun executeSearch() {
        toast("执行搜索")

        val confirmSearch = findConfirmSearchButton()
        if (confirmSearch != null) {
            confirmSearch.tryClick()
            toast("点击搜索确认")
        } else {
            AutoApi.sendKeyCode(KeyEvent.KEYCODE_ENTER)
            toast("按Enter键搜索")
        }
    }

    private suspend fun findConfirmSearchButton() = listOf(
        withText("搜索").findFirst(),
        withText("确认").findFirst(),
        withId("search_btn").findFirst()
    ).firstOrNull { it != null }

    private suspend fun selectUserCategory() {
        toast("选择用户类别")

        val userTab = findUserTab()
        if (userTab != null) {
            userTab.tryClick()
            toast("点击用户类别成功")
        } else {
            toast("未找到用户类别标签，可能已经在用户页面")
        }
    }

    private suspend fun findUserTab(): ViewNode? {
        return listOf(
            withText("用户").findFirst(),
            withText("账号").findFirst(),
            containsText("用户").findFirst(),
            findAllWith { node ->
                val text = node.text?.toString()
                text == "用户" || text == "账号" || (text?.contains("用户") == true)
            }.firstOrNull()
        ).firstOrNull { it != null }
    }

    private suspend fun clickFirstUser() {
        toast("点击第一个用户")

        val firstUser = findFirstUserResult()
        if (firstUser == null) {
            toast("未找到用户搜索结果")
            throw RuntimeException("未找到用户搜索结果")
        }

        firstUser.tryClick()
        toast("点击第一个用户成功")
    }

    private suspend fun findFirstUserResult(): ViewNode? {
        delay(2000) // 等待搜索结果加载

        val userItems = findAllWith { node ->
            val nodeBounds = android.graphics.Rect()
            node.getBoundsInScreen(nodeBounds)
            node.isClickable() &&
            nodeBounds.height() > 100 && // 过滤掉小的点击元素
            node.text?.toString()?.contains("搜索") != true &&
            node.text?.toString()?.contains("筛选") != true &&
            node.text?.toString()?.contains("排序") != true &&
            node.text?.toString()?.contains("用户") != true
        }

        return userItems.firstOrNull()
    }

    private suspend fun extractUserNotes() {
        toast("开始抓取用户笔记")
        Log.i(TAG, "=== 开始抓取用户笔记 ===")

        delay(3000) // 等待用户页面加载

        var noteCount = 0
        var maxNotes = 2 // 限制抓取笔记数量
        var scrollAttempts = 0
        val maxScrollAttempts = 3

        try {
            while (noteCount < maxNotes && scrollAttempts < maxScrollAttempts) {
                // 检查系统健康状态
                checkSystemHealth()

                val noteItems = findNoteItems()

                toast("找到 ${noteItems.size} 个潜在笔记项")
                Log.i(TAG, "找到 ${noteItems.size} 个潜在笔记项")

                // 打印每个找到的笔记项信息
                noteItems.forEachIndexed { index, item ->
                    val bounds = item.bounds
                    val text = item.text?.toString() ?: ""
                    val desc = item.desc() ?: ""
                    val className = item.className ?: ""

                    val info = "笔记项${index + 1}: 文本='$text' 描述='$desc' 类名='$className' 位置=(${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}) 大小=${bounds.width()}x${bounds.height()}"
                    Log.i(TAG, info)
                    toast(info)
                }

                if (noteItems.isEmpty()) {
                    Log.w(TAG, "未找到笔记项，尝试滚动查找更多")
                    scrollDownForMoreNotes()
                    delay(2000)
                    scrollAttempts++
                    continue
                }

                for ((_, noteItem) in noteItems.withIndex()) {
                    if (noteCount >= maxNotes) break

                    try {
                        toast("点击第${noteCount + 1}个笔记")
                        Log.i(TAG, "尝试点击笔记项 ${noteCount + 1}")
                        noteItem.tryClick()

                        delay(3000) // 等待笔记详情页加载

                        // 验证是否成功进入笔记详情页
                        if (verifyInNoteDetailPage()) {
                            Log.i(TAG, "=== 开始抓取笔记 ${noteCount + 1} ===")
                            extractSingleNote(noteCount + 1)
                        } else {
                            Log.w(TAG, "未成功进入笔记详情页，跳过此笔记")
                            continue
                        }

                        // 返回用户页面
                        back()
                        delay(2000)

                        // 验证是否成功返回用户页面
                        if (verifyInUserPage()) {
                            Log.i(TAG, "成功返回用户页面")
                        } else {
                            Log.w(TAG, "可能未成功返回用户页面")
                            // 多尝试几次返回
                            repeat(2) { back(); delay(1000) }
                        }

                        noteCount++

                    } catch (e: Exception) {
                        Log.e(TAG, "处理笔记${noteCount + 1}时出现异常", e)
                        toast("处理笔记异常，尝试恢复")

                        // 尝试返回用户页面
                        try {
                            repeat(3) { back(); delay(1000) }
                        } catch (ex: Exception) {
                            Log.e(TAG, "恢复操作失败", ex)
                        }

                        noteCount++ // 跳过这个笔记，继续下一个
                    }
                }

                if (noteCount < maxNotes && noteItems.isNotEmpty()) {
                    scrollDownForMoreNotes()
                    delay(2000)
                }

                scrollAttempts = 0 // 重置滚动计数
            }

        } catch (e: Exception) {
            Log.e(TAG, "抓取用户笔记时发生异常", e)
            toast("抓取异常: ${e.message}")
        } finally {
            toast("笔记抓取完成，共抓取 $noteCount 个笔记")
            Log.i(TAG, "=== 笔记抓取完成，共抓取 $noteCount 个笔记 ===")
        }
    }

    private suspend fun verifyInNoteDetailPage(): Boolean {
        // 检查是否在笔记详情页（通过查找特征性元素）
        val detailPageElements = findAllWith { node ->
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription ?: ""
            text.contains("点赞|收藏|评论|分享".toRegex()) ||
            desc.contains("点赞|收藏|评论|分享".toRegex())
        }
        return detailPageElements.isNotEmpty()
    }

    private suspend fun verifyInUserPage(): Boolean {
        // 检查是否在用户页面（通过查找特征性元素）
        val userPageElements = findAllWith { node ->
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription ?: ""
            text.contains("关注|粉丝|获赞".toRegex()) ||
            desc.contains("关注|粉丝|获赞".toRegex()) ||
            desc.contains("笔记")
        }
        return userPageElements.isNotEmpty()
    }

    private suspend fun findNoteItems(): List<ViewNode> {
        return findAllWith { node ->
            val nodeBounds = android.graphics.Rect()
            node.getBoundsInScreen(nodeBounds)
            val desc = node.contentDescription ?: ""

            node.isClickable() &&
            nodeBounds.width() > 200 &&
            nodeBounds.height() > 200 &&
            (node.className?.contains("FrameLayout") == true &&
             desc.contains("笔记") &&
             !desc.contains("已选定笔记") && // 排除"已选定笔记"
             desc.contains("赞")) // 确保包含点赞信息，这是真正笔记项的特征
        }.take(3) // 每次只处理前3个笔记项
    }

    private suspend fun extractSingleNote(noteNumber: Int) {
        toast("抓取第${noteNumber}个笔记数据")
        Log.i(TAG, "=== 开始抓取第${noteNumber}个笔记 ===")

        // 1. 先抓取图片
      //  extractNoteImages(noteNumber)

        // 2. 保存图片后，立即获取标题和内容（在原位置）
       // val title = extractNoteTitle()
       // val content = extractNoteContent()
        val (title, content) = extractTitleAndContent()
        Log.i(TAG, "笔记${noteNumber} - 标题: $title")
        Log.i(TAG, "笔记${noteNumber} - 内容: ${content}")

        // 3. 获取互动数据（点赞、收藏、评论数）
        val (likes, favorites, comments) = extractInteractionData()
        Log.i(TAG, "笔记${noteNumber} - 点赞: $likes, 收藏: $favorites, 评论: $comments")

        // 4. 最后下滑查找评论区并抓取评论
        val commentExtractor = XiaohongshuCommentExtractor()
        val result = commentExtractor.extractComments()

        Log.i(TAG, "extractSingleNote: ${result}")

        toast("第${noteNumber}个笔记数据抓取完成")
    }

    private suspend fun extractNoteImages(noteNumber: Int) {
        toast("抓取笔记图片")

        val imageCount = getImageCount()
        Log.i(TAG, "检测到: ${imageCount} 张图片")

        if (imageCount <= 1) {
            saveCurrentImage(noteNumber, 1)
        } else {
            toast("检测到${imageCount}张图片")
            for (i in 1..imageCount) {
                saveCurrentImage(noteNumber, i)
                if (i < imageCount) {
                    delay(1500)
                    Log.i(TAG, "向右滑动")
                    swipeImageRight()
                    delay(1500)
                }
            }
        }
    }

    private suspend fun getImageCount(): Int {
        val imageIndicators = findAllWith { node ->
            val text = node.text?.toString()
            text?.matches(Regex("\\d+/\\d+")) == true
        }

        if (imageIndicators.isNotEmpty()) {
            val indicator = imageIndicators.first().text?.toString()
            val parts = indicator?.split("/")
            return parts?.get(1)?.toIntOrNull() ?: 1
        }

        return 1
    }

    private suspend fun saveCurrentImage(noteNumber: Int, imageNumber: Int) {
        toast("保存笔记${noteNumber}的第${imageNumber}张图片")
        Log.i(TAG, "保存笔记${noteNumber}的图片${imageNumber}")

        try {
            // 检查系统状态
            checkSystemHealth()

            delay(1000)

            var attempts = 0
            val maxAttempts = 3
            var saveSuccess = false

            while (attempts < maxAttempts && !saveSuccess) {
                attempts++
                Log.i(TAG, "第${attempts}次尝试保存图片${imageNumber}")

                val imageContainer = findImageContainer()
                if (imageContainer != null) {
                    val bounds = imageContainer.bounds
                    val centerX = bounds.centerX()
                    val centerY = bounds.centerY()

                    Log.i(TAG, "找到图片容器: 类名=${imageContainer.className} 描述='${imageContainer.desc()}' 位置=$bounds")
                    toast("第${attempts}次尝试长按图片位置: ($centerX, $centerY)")

                    longClick(centerX, centerY)
                    delay(1000) // 增加等待时间，确保菜单完全出现

                    val saveButton = findSaveButton()
                    if (saveButton != null) {
                        Log.i(TAG, "找到保存按钮: ${saveButton.text} ${saveButton.desc()}")
                        saveButton.tryClick()
                        toast("第${imageNumber}张图片保存成功")
                        saveSuccess = true
                    } else {
                        Log.w(TAG, "第${attempts}次尝试未找到保存按钮")
                        if (attempts < maxAttempts) {
                            back() // 关闭可能的弹窗
                            delay(1000)
                            toast("重试保存...")
                        } else {
                            debugSaveMenuElements()
                            toast("保存按钮查找失败")
                        }
                    }
                } else {
                    Log.w(TAG, "第${attempts}次尝试未找到图片容器")
                    if (attempts == maxAttempts) {
                        debugImageContainerSearch()
                        toast("图片容器查找失败")
                    }
                }

                if (!saveSuccess && attempts < maxAttempts) {
                    delay(1500) // 重试间隔
                }
            }

            if (!saveSuccess) {
                Log.w(TAG, "经过${maxAttempts}次尝试仍未成功保存图片${imageNumber}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "保存图片失败", e)
            toast("保存图片失败: ${e.message}")
        }
    }

    private suspend fun checkSystemHealth() {
        try {
            // 检查内存状态
            System.gc() // 建议垃圾回收
            delay(100)

            // 检查当前应用是否还在前台
            if (!waitForApp(XIAOHONGSHU_PACKAGE, 1000)) {
                Log.w(TAG, "小红书应用可能不在前台，尝试重新激活")
                toast("应用状态检查...")
            }
        } catch (e: Exception) {
            Log.w(TAG, "系统健康检查异常: ${e.message}")
        }
    }

    private suspend fun findImageContainer(): ViewNode? {
        return findAllWith { node ->
            val nodeBounds = android.graphics.Rect()
            node.getBoundsInScreen(nodeBounds)
            node.className?.contains("FrameLayout") == true &&
            (node.contentDescription?.contains("图片") == true ||
            nodeBounds.width() > 500 && nodeBounds.height() > 500) &&
            node.isVisibleToUser
        }.sortedByDescending {
            it.bounds.width() * it.bounds.height()
        }.firstOrNull()
    }

    private suspend fun findSaveButton(): ViewNode? {
        val saveStrategies = listOf<suspend () -> ViewNode?>(
            { withText("保存").findFirst() },
            { withText("保存图片").findFirst() },
            { withText("保存到相册").findFirst() },
            { containsText("保存").findFirst() }
        )

        for (strategy in saveStrategies) {
            val button = strategy()
            if (button != null) return button
        }

        return null
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
                    results.add(
                        DepthTextInfo(
                        text = text,
                        bounds = node.bounds,
                        depth = currentDepth,
                        className = node.className
                    )
                    )
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

    private suspend fun extractTitleAndContent(): Pair<String, String> {
        var title = ""
        var content = ""

        // 尝试直接提取
        val textElements = findAllWith { node ->
            val text = node.text?.toString()
            !text.isNullOrBlank() &&
            text.length > 5 &&
            !text.contains("点赞|收藏|评论|分享|关注".toRegex()) &&
            node.isVisibleToUser
        }.sortedBy {
            it.bounds.top
        }

        if (textElements.isNotEmpty()) {
            title = textElements.firstOrNull()?.text?.toString() ?: ""
            content = textElements.drop(1).joinToString("\n") { it.text?.toString() ?: "" }
        }

        // 如果没找到内容，尝试下滑
        if (title.isEmpty() || content.isEmpty()) {
            swipeViewDown()
            delay(2000)

            val moreTextElements = findAllWith { node ->
                val text = node.text?.toString()
                !text.isNullOrBlank() &&
                text.length > 5 &&
                !text.contains("点赞|收藏|评论|分享|关注".toRegex()) &&
                node.isVisibleToUser
            }.sortedBy {
                it.bounds.top
            }

            if (title.isEmpty() && moreTextElements.isNotEmpty()) {
                title = moreTextElements.firstOrNull()?.text?.toString() ?: ""
            }
            if (content.isEmpty() && moreTextElements.size > 1) {
                content = moreTextElements.drop(1).joinToString("\n") { it.text?.toString() ?: "" }
            }
        }
        Log.i(TAG, "extractTitleAndContent: ${textElements}")
        return Pair(title.take(100), content.take(500))
    }

    private suspend fun extractInteractionData(): Triple<String, String, String> {
        val interactionElements = findAllWith { node ->
            val text = node.text?.toString()
            text?.matches(Regex("\\d+")) == true ||
            text?.matches(Regex("\\d+\\.\\d+[万千]?")) == true
        }

        var likes = "0"
        var favorites = "0"
        var comments = "0"

        // 根据UI结构，通常点赞、收藏、评论按钮会按顺序出现
        if (interactionElements.size >= 3) {
            likes = interactionElements[0].text?.toString() ?: "0"
            favorites = interactionElements[1].text?.toString() ?: "0"
            comments = interactionElements[2].text?.toString() ?: "0"
        }

        return Triple(likes, favorites, comments)
    }

    private suspend fun swipeImageRight() {
        val (screenWidth, screenHeight) = getScreenSize()
        val centerY = screenHeight / 3
        val startX = screenWidth * 9 / 10
        val endX = screenWidth / 10

        swipe(startX, centerY, endX, centerY, 500)
    }

    private suspend fun swipeViewDown() {
        val (screenWidth, screenHeight) = getScreenSize()
        val centerX = screenWidth / 2
        val startY = screenHeight * 7 / 10
        val endY = screenHeight * 3 / 10

        swipe(centerX, startY, centerX, endY, 500)
    }

    private suspend fun scrollDownForMoreNotes() {
        val (screenWidth, screenHeight) = getScreenSize()
        val centerX = screenWidth / 2
        val startY = screenHeight * 8 / 10
        val endY = screenHeight * 2 / 10

        swipe(centerX, startY, centerX, endY, 800)
    }

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

    private suspend fun debugPrintAllScreenElements() {
        toast("开始深度递归分析用户页面所有元素...")
        delay(1000)

        Log.i(TAG, "=== 小红书用户页面深度递归分析 ===")

        // 先找到所有 RecyclerView
        val recyclerViews = findAllWith { node ->
            node.className?.contains("RecyclerView") == true
        }

        Log.i(TAG, "找到 ${recyclerViews.size} 个 RecyclerView")

        // 递归分析每个 RecyclerView 的内容
        recyclerViews.forEachIndexed { rvIndex, recyclerView ->
            Log.i(TAG, "=== RecyclerView ${rvIndex + 1} 分析 ===")
            val recyclerViewId = try { recyclerView.id} catch (e: Exception) { "无法获取" }
            Log.i(TAG, "RecyclerView: ID=$recyclerViewId 位置=${recyclerView.bounds} 子项数=${recyclerView.childCount}")

            analyzeRecyclerViewChildren(recyclerView, 0, "RV${rvIndex + 1}")
        }

        // 全局所有元素分析（作为补充）
        val allElements = findAllWith { _ -> true }
        Log.i(TAG, "页面总元素数量: ${allElements.size}")

        // 专门寻找可能的笔记项元素
        val potentialNotes = findAllWith { node ->
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val className = node.className ?: ""
            val desc = node.contentDescription ?: ""
            val text = node.text?.toString() ?: ""

            node.isClickable() &&
            bounds.width() > 100 &&
            bounds.height() > 100 &&
            !text.contains("搜索|筛选|排序|关注|粉丝|获赞".toRegex())
        }.sortedWith(compareBy(
            { node -> node.bounds.top }, // 按位置从上到下
            { node -> node.bounds.left }  // 然后从左到右
        ))

        Log.i(TAG, "=== 潜在笔记项元素分析 ===")
        potentialNotes.take(20).forEachIndexed { index, node ->
            val text = node.text?.toString() ?: ""
            val desc = node.desc() ?: ""
            val bounds = node.bounds

            val info = buildString {
                append("潜在笔记项${index + 1}: ")
                append("文本='$text' ")
                append("描述='$desc' ")
                append("类名='${node.className}' ")
                append("ID='${try { node.id  } catch (e: Exception) { "null" }}' ")
                append("位置=(${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}) ")
                append("大小=${bounds.width()}x${bounds.height()} ")
                append("可见=${node.isVisibleToUser}")

                // 智能标记
                when {
                    node.className?.contains("Image") == true -> append(" [★图片元素★]")
                    desc.contains("笔记") -> append(" [★笔记描述★]")
                    desc.contains("图片") -> append(" [★图片描述★]")
                    bounds.width() > 300 && bounds.height() > 300 -> append(" [大尺寸]")
                }
            }
            Log.i(TAG, info)

            // Toast 显示重要内容
            if (desc.contains("笔记") || desc.contains("图片") || node.className?.contains("Image") == true) {
                toast("发现潜在笔记项: ${desc.ifEmpty { node.className }}")
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
                val bounds = child.bounds

                val info = buildString {
                    append("${prefix}${indent}子项[$i]: ")
                    append("文本='$text' ")
                    append("描述='$desc' ")
                    append("类名='$className' ")
                    append("ID='${try { child.id  } catch (e: Exception) { "null" }}' ")
                    append("子项数=${child.childCount} ")
                    append("位置=${bounds} ")
                    append("可点击=${child.isClickable()}")

                    // 特别标记可能的笔记项
                    if (child.isClickable() && bounds.width() > 200 && bounds.height() > 200) {
                        if (className.contains("Image") || desc.contains("笔记") || desc.contains("图片")) {
                            append(" [★可能是笔记项★]")
                        }
                    }
                }
                Log.i(TAG, info)

                // 如果是重要的笔记相关元素，在 toast 中显示
                if (child.isClickable() && (className.contains("Image") || desc.contains("笔记") || desc.contains("图片"))) {
                    toast("${prefix}发现笔记项: ${desc.ifEmpty { className }}")
                    delay(1500)
                }

                // 递归遍历子节点（最多3层深度，避免过深）
                if (depth < 3 && child.childCount > 0) {
                    analyzeRecyclerViewChildren(child, depth + 1, prefix)
                }
            }
        }
    }

    private suspend fun debugSaveMenuElements() {
        Log.i(TAG, "=== 调试保存菜单元素 ===")
        toast("调试保存菜单元素")

        val allClickableElements = findAllWith { node ->
            node.isClickable() || node.text?.toString()?.isNotBlank() == true
        }

        Log.i(TAG, "长按后屏幕上所有可点击/有文本的元素:")
        allClickableElements.take(15).forEachIndexed { index, node ->
            val text = node.text?.toString() ?: ""
            val desc = node.desc() ?: ""
            val className = node.className ?: ""
            val bounds = node.bounds

            val info = "菜单元素${index + 1}: 文本='$text' 描述='$desc' 类名='$className' 位置=${bounds} 可点击=${node.isClickable()}"
            Log.i(TAG, info)

            if (text.contains("保存") || desc.contains("保存") || text.contains("下载") || desc.contains("下载")) {
                toast("发现保存相关元素: $text $desc")
                delay(2000)
            }
        }
    }

    private suspend fun debugImageContainerSearch() {
        Log.i(TAG, "=== 调试图片容器查找 ===")
        toast("调试图片容器查找")

        val allFrameLayouts = findAllWith { node ->
            node.className?.contains("FrameLayout") == true && node.isVisibleToUser
        }

        Log.i(TAG, "屏幕上所有可见的FrameLayout:")
        allFrameLayouts.take(10).forEachIndexed { index, node ->
            val desc = node.desc() ?: ""
            val bounds = node.bounds
            val area = bounds.width() * bounds.height()

            val info = "FrameLayout${index + 1}: 描述='$desc' 位置=${bounds} 大小=${bounds.width()}x${bounds.height()} 面积=$area"
            Log.i(TAG, info)

            if (desc.contains("图片") || area > 250000) { // 大面积的可能是图片容器
                toast("可能的图片容器: ${desc.ifEmpty { "大尺寸FrameLayout" }}")
                delay(1500)
            }
        }

        val allImageViews = findAllWith { node ->
            node.className?.contains("ImageView") == true && node.isVisibleToUser
        }

        Log.i(TAG, "屏幕上所有可见的ImageView:")
        allImageViews.take(5).forEachIndexed { index, node ->
            val desc = node.desc() ?: ""
            val bounds = node.bounds

            val info = "ImageView${index + 1}: 描述='$desc' 位置=${bounds} 大小=${bounds.width()}x${bounds.height()}"
            Log.i(TAG, info)
        }
    }
}