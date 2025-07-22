package cn.vove7.andro_accessibility_api.demo.actions

import android.graphics.Rect
import android.util.Log
import androidx.activity.ComponentActivity
import cn.vove7.andro_accessibility_api.demo.toast
import cn.vove7.auto.core.api.findAllWith
import cn.vove7.auto.core.requireAutoService
import cn.vove7.auto.core.viewnode.ViewNode
import kotlinx.coroutines.delay

class ClickDebugAction : Action() {
    
    companion object {
        private const val TAG = "ClickDebugAction"
        private const val DEBUG_DURATION = 60000 // 60秒调试时间
    }
    
    override val name: String = "点击调试模式"
    
    override suspend fun run(act: ComponentActivity) {
        requireAutoService()
        
        toast("点击调试模式启动！点击屏幕任意位置查看该位置的元素信息")
        toast("调试模式将持续60秒，请在小红书页面进行点击测试")
        
        val startTime = System.currentTimeMillis()
        var clickCount = 0
        
        // 记录初始界面状态
        val initialElements = findAllWith { true }
        Log.i(TAG, "=== 调试模式启动 ===")
        Log.i(TAG, "初始界面元素数量: ${initialElements.size}")
        
        while (System.currentTimeMillis() - startTime < DEBUG_DURATION) {
            delay(500) // 每500ms检查一次界面变化
            
            val currentElements = findAllWith { true }
            
            // 检测是否有新的点击（通过界面变化或焦点变化来判断）
            val focusedElements = currentElements.filter { it.node.isFocused }
            val selectedElements = currentElements.filter { it.node.isSelected }
            val recentlyChangedElements = currentElements.filter { 
                it.node.isAccessibilityFocused || it.node.isSelected || it.node.isFocused
            }
            
            if (focusedElements.isNotEmpty() || selectedElements.isNotEmpty() || recentlyChangedElements.isNotEmpty()) {
                clickCount++
                Log.i(TAG, "\n=== 检测到第${clickCount}次交互 ===")
                
                // 分析焦点元素
                focusedElements.forEach { element ->
                    analyzeClickedElement(element, "焦点元素")
                }
                
                // 分析选中元素
                selectedElements.forEach { element ->
                    analyzeClickedElement(element, "选中元素")
                }
                
                // 分析最近变化的元素
                recentlyChangedElements.forEach { element ->
                    analyzeClickedElement(element, "交互元素")
                }
                
                delay(2000) // 分析完后等待2秒再继续监测
            }
            
            // 额外功能：分析屏幕中心区域的元素
            if ((System.currentTimeMillis() - startTime) % 10000 < 500) { // 每10秒分析一次屏幕中心
                analyzeScreenCenter()
            }
        }
        
        toast("点击调试模式结束！共检测到${clickCount}次交互")
        Log.i(TAG, "=== 调试模式结束 ===")
    }
    
    private suspend fun analyzeClickedElement(element: ViewNode, type: String) {
        val bounds = element.bounds
        Log.i(TAG, "\n--- $type 分析开始 ---")
        Log.i(TAG, "元素位置: (${bounds.left}, ${bounds.top}) - (${bounds.right}, ${bounds.bottom})")
        
        toast("分析$type: ${element.text?.toString()?.take(20) ?: element.desc()?.take(20) ?: "无文本"}")
        
        // 递归分析该元素及其父子节点
        recursiveAnalyzeElement(element, 0, "目标")
        
        // 分析该元素的父节点
        var parent = element.parent
        var parentLevel = 1
        while (parent != null && parentLevel <= 3) {
            Log.i(TAG, "\n=== 父节点层级 $parentLevel 分析 ===")
            recursiveAnalyzeElement(parent, 0, "父节点L$parentLevel")
            parent = parent.parent
            parentLevel++
        }
        
        // 分析附近区域的其他元素
        analyzeNearbyElements(bounds)
        
        Log.i(TAG, "--- $type 分析结束 ---\n")
        delay(1000)
    }
    
    private fun recursiveAnalyzeElement(node: ViewNode, depth: Int, prefix: String) {
        val indent = "  ".repeat(depth)
        val text = node.text?.toString() ?: ""
        val desc = node.desc() ?: ""
        val className = node.className ?: ""
        val bounds = node.bounds
        
        val info = buildString {
            append("${prefix}${indent}[D$depth] ")
            append("文本='$text' ")
            append("描述='$desc' ")
            append("类名='$className' ")
            append("ID='${try { node.id ?: "" } catch (e: Exception) { "null" }}' ")
            append("子项=${node.childCount} ")
            append("位置=(${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}) ")
            append("大小=${bounds.width()}x${bounds.height()} ")
            append("可见=${node.isVisibleToUser} ")
            append("可点击=${node.isClickable()} ")
            append("可编辑=${node.node.isEditable} ")
            append("可滚动=${node.node.isScrollable}")
            
            // 特别标记
            when {
                text.length > 10 && !text.contains("点赞|收藏|评论|分享".toRegex()) -> append(" [★重要文本★]")
                className.contains("RecyclerView") -> append(" [★RecyclerView★]")
                className.contains("TextView") && text.isNotBlank() -> append(" [★TextView★]")
                node.isClickable() -> append(" [可点击]")
            }
        }
        
        Log.i(TAG, info)
        
        // 如果有重要文本，也在toast中显示
        if (text.length > 5 && !text.contains("点赞|收藏|评论|分享".toRegex())) {
            // 不在这里toast，避免过多干扰
        }
        
        // 递归分析子节点（限制深度）
        if (depth < 3 && node.childCount > 0) {
            for (i in 0 until node.childCount) {
                val child = node.childAt(i)
                if (child != null) {
                    recursiveAnalyzeElement(child, depth + 1, prefix)
                }
            }
        }
    }
    
    private suspend fun analyzeNearbyElements(targetBounds: Rect) {
        Log.i(TAG, "\n=== 分析附近区域元素 ===")
        
        // 扩展区域，查找附近的元素
        val expandedBounds = Rect(
            targetBounds.left - 100,
            targetBounds.top - 100, 
            targetBounds.right + 100,
            targetBounds.bottom + 100
        )
        
        val nearbyElements = findAllWith { node ->
            val nodeBounds = android.graphics.Rect()
            node.getBoundsInScreen(nodeBounds)
            expandedBounds.intersects(nodeBounds.left, nodeBounds.top, nodeBounds.right, nodeBounds.bottom) &&
            (!node.text?.toString().isNullOrBlank() || !node.contentDescription.isNullOrBlank())
        }
        
        Log.i(TAG, "附近区域找到 ${nearbyElements.size} 个有内容的元素")
        
        nearbyElements.take(10).forEachIndexed { index, node ->
            val text = node.text?.toString() ?: ""
            val desc = node.desc() ?: ""
            val distance = calculateDistance(targetBounds, node.bounds)
            
            val info = "附近元素${index + 1}: 文本='$text' 描述='$desc' 距离=${distance}px 类名=${node.className}"
            Log.i(TAG, info)
        }
    }
    
    private fun calculateDistance(bounds1: Rect, bounds2: Rect): Int {
        val centerX1 = bounds1.centerX()
        val centerY1 = bounds1.centerY()
        val centerX2 = bounds2.centerX()
        val centerY2 = bounds2.centerY()
        
        val dx = centerX1 - centerX2
        val dy = centerY1 - centerY2
        
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toInt()
    }
    
    private suspend fun analyzeScreenCenter() {
        Log.i(TAG, "\n=== 屏幕中心区域分析 ===")
        
        val allElements = findAllWith { true }
        if (allElements.isEmpty()) return
        
        // 找到屏幕中心附近的元素
        val screenCenterX = 500 // 假设屏幕中心
        val screenCenterY = 1000
        
        val centerElements = allElements.filter { node ->
            val bounds = node.bounds
            val centerX = bounds.centerX()
            val centerY = bounds.centerY()
            
            kotlin.math.abs(centerX - screenCenterX) < 200 && 
            kotlin.math.abs(centerY - screenCenterY) < 300 &&
            (!node.text?.toString().isNullOrBlank() || !node.desc().isNullOrBlank())
        }.sortedBy { 
            calculateDistance(Rect(screenCenterX, screenCenterY, screenCenterX, screenCenterY), it.bounds)
        }
        
        Log.i(TAG, "屏幕中心区域找到 ${centerElements.size} 个元素")
        
        centerElements.take(5).forEach { element ->
            Log.i(TAG, "中心元素: 文本='${element.text}' 类名=${element.className} 位置=${element.bounds}")
        }
    }
}