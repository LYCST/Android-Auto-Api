package cn.vove7.andro_accessibility_api.demo.actions

import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import cn.vove7.andro_accessibility_api.demo.toast
import cn.vove7.auto.core.api.findAllWith
import cn.vove7.auto.core.requireAutoService
import cn.vove7.auto.core.viewnode.ViewNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class UITreeSerializerAction : Action() {
    
    companion object {
        private const val TAG = "UITreeSerializer"
    }
    
    override val name: String = "UI树序列化导出"
    
    override suspend fun run(act: ComponentActivity) {
        requireAutoService()
        
        toast("UI树序列化启动！请在30秒内打开小红书并导航到目标页面")
        toast("倒计时开始...")
        
        // 30秒倒计时，每5秒提醒一次
        for (i in 10 downTo 1) {
            when {
                i % 5 == 0 -> toast("请准备目标页面，还有 ${i} 秒开始序列化")
                i <= 3 -> toast("${i}...")
            }
            kotlinx.coroutines.delay(1000)
        }
        
        toast("开始序列化当前页面UI树...")
        
        try {
            // 获取根节点
            val rootNode = ViewNode.getRoot()
            
            toast("正在分析UI树结构...")
            
            // 序列化整个UI树
            val uiTreeJson = serializeUITree(rootNode)
            
            // 保存到文件
            val fileName = saveToFile(uiTreeJson)
            
            // 同时输出到日志（部分内容）
            logUITreeSummary(uiTreeJson)
            
            toast("UI树序列化完成！已保存到: $fileName")
            Log.i(TAG, "UI树序列化文件: $fileName")
            
        } catch (e: Exception) {
            Log.e(TAG, "UI树序列化失败", e)
            toast("序列化失败: ${e.message}")
        }
    }
    
    private suspend fun serializeUITree(node: ViewNode): JSONObject {
        return withContext(Dispatchers.Default) {
            val nodeJson = JSONObject()
            
            // 基本信息
            nodeJson.put("className", node.className ?: "")
            nodeJson.put("text", node.text?.toString() ?: "")
            nodeJson.put("contentDescription", node.desc() ?: "")
            nodeJson.put("resourceId", try { node.id ?: "" } catch (e: Exception) { "" })
            
            // 位置和大小信息
            val bounds = node.bounds
            val boundsJson = JSONObject().apply {
                put("left", bounds.left)
                put("top", bounds.top)
                put("right", bounds.right)
                put("bottom", bounds.bottom)
                put("width", bounds.width())
                put("height", bounds.height())
            }
            nodeJson.put("bounds", boundsJson)
            
            // 状态信息
            val stateJson = JSONObject().apply {
                put("isVisibleToUser", node.isVisibleToUser)
                put("isClickable", node.isClickable())
                put("isEnabled", node.node.isEnabled)
                put("isFocusable", node.node.isFocusable)
                put("isFocused", node.node.isFocused)
                put("isSelected", node.node.isSelected)
                put("isCheckable", node.node.isCheckable)
                put("isChecked", node.node.isChecked)
                put("isEditable", node.node.isEditable)
                put("isScrollable", node.node.isScrollable)
                put("isLongClickable", node.node.isLongClickable)
                put("isPassword", node.node.isPassword)
            }
            nodeJson.put("state", stateJson)
            
            // 层次信息
            nodeJson.put("childCount", node.childCount)
            
            // 特殊标记（用于分析）
            val tags = mutableListOf<String>()
            val text = node.text?.toString() ?: ""
            val className = node.className ?: ""
            
            when {
                className.contains("RecyclerView") -> tags.add("RecyclerView")
                className.contains("TextView") && text.isNotBlank() -> tags.add("TextView_WithText")
                className.contains("EditText") -> tags.add("EditText")
                className.contains("Button") -> tags.add("Button")
                className.contains("ImageView") -> tags.add("ImageView")
                text.length > 10 && !text.contains("点赞|收藏|评论|分享".toRegex()) -> tags.add("PotentialContent")
                text.matches(Regex("\\d+/\\d+")) -> tags.add("ImageCounter")
                node.isClickable() -> tags.add("Clickable")
            }
            nodeJson.put("tags", JSONArray(tags))
            
            // 递归处理子节点
            val childrenArray = JSONArray()
            for (i in 0 until node.childCount) {
                val child = node.childAt(i)
                if (child != null) {
                    val childJson = serializeUITree(child)
                    childJson.put("index", i)
                    childrenArray.put(childJson)
                }
            }
            nodeJson.put("children", childrenArray)
            
            nodeJson
        }
    }
    
    private suspend fun saveToFile(jsonData: JSONObject): String {
        return withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "ui_tree_$timestamp.json"
            
            // 优先尝试保存到 /mnt/shared/Misc 目录
            val sharedMiscDir = File("/mnt/shared/Misc")
            val sharedFile = File(sharedMiscDir, fileName)
            
            try {
                if (!sharedMiscDir.exists()) {
                    sharedMiscDir.mkdirs()
                }
                
                sharedFile.writeText(jsonData.toString(2)) // 格式化JSON，缩进2空格
                Log.i(TAG, "UI树已保存到: ${sharedFile.absolutePath}")
                return@withContext sharedFile.absolutePath
            } catch (e: Exception) {
                Log.w(TAG, "保存到 /mnt/shared/Misc 失败，尝试其他目录", e)
            }
            
            // 备用方案1：尝试保存到Downloads目录
            try {
                val externalDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(externalDir, fileName)
                
                if (!externalDir.exists()) {
                    externalDir.mkdirs()
                }
                
                file.writeText(jsonData.toString(2))
                Log.i(TAG, "UI树已保存到Downloads: ${file.absolutePath}")
                return@withContext file.absolutePath
            } catch (e: Exception) {
                Log.w(TAG, "保存到Downloads失败", e)
            }
            
            // 备用方案2：保存到外部存储根目录
            try {
                val rootFile = File(Environment.getExternalStorageDirectory(), fileName)
                rootFile.writeText(jsonData.toString(2))
                Log.i(TAG, "UI树已保存到存储根目录: ${rootFile.absolutePath}")
                return@withContext rootFile.absolutePath
            } catch (e: Exception) {
                Log.w(TAG, "保存到存储根目录失败", e)
            }
            
            // 备用方案3：保存到应用缓存目录（总是可写的）
            try {
                val cacheFile = File(Environment.getExternalStorageDirectory(), "Android/data/cn.vove7.andro_accessibility_api.demo/cache/$fileName")
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(jsonData.toString(2))
                Log.i(TAG, "UI树已保存到缓存目录: ${cacheFile.absolutePath}")
                return@withContext cacheFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "所有保存方案都失败", e)
                throw e
            }
        }
    }
    
    private fun logUITreeSummary(jsonData: JSONObject) {
        Log.i(TAG, "=== UI树结构摘要 ===")
        
        // 统计信息
        val stats = analyzeUITreeStats(jsonData)
        Log.i(TAG, "总节点数: ${stats.totalNodes}")
        Log.i(TAG, "文本节点数: ${stats.textNodes}")
        Log.i(TAG, "可点击节点数: ${stats.clickableNodes}")
        Log.i(TAG, "RecyclerView数: ${stats.recyclerViews}")
        Log.i(TAG, "最大深度: ${stats.maxDepth}")
        
        // 输出重要的文本内容
        Log.i(TAG, "\n=== 重要文本内容 ===")
        val importantTexts = extractImportantTexts(jsonData, 0)
        importantTexts.take(20).forEachIndexed { index, textInfo ->
            Log.i(TAG, "${index + 1}. [深度${textInfo.depth}] ${textInfo.className}: '${textInfo.text}' (${textInfo.bounds})")
        }
        
        // 输出RecyclerView结构
        Log.i(TAG, "\n=== RecyclerView结构 ===")
        val recyclerViews = findRecyclerViews(jsonData, 0)
        recyclerViews.forEachIndexed { index, rvInfo ->
            Log.i(TAG, "RecyclerView ${index + 1}: 深度${rvInfo.depth}, 子项${rvInfo.childCount}, 位置${rvInfo.bounds}")
            rvInfo.importantChildren.take(5).forEach { child ->
                Log.i(TAG, "  └─ ${child.className}: '${child.text}'")
            }
        }
    }
    
    private fun analyzeUITreeStats(node: JSONObject, depth: Int = 0): UITreeStats {
        val stats = UITreeStats(maxDepth = depth)
        
        stats.totalNodes++
        
        val text = node.optString("text", "")
        if (text.isNotBlank()) {
            stats.textNodes++
        }
        
        val isClickable = node.optJSONObject("state")?.optBoolean("isClickable", false) ?: false
        if (isClickable) {
            stats.clickableNodes++
        }
        
        val className = node.optString("className", "")
        if (className.contains("RecyclerView")) {
            stats.recyclerViews++
        }
        
        // 递归统计子节点
        val children = node.optJSONArray("children")
        children?.let {
            for (i in 0 until it.length()) {
                val child = it.getJSONObject(i)
                val childStats = analyzeUITreeStats(child, depth + 1)
                stats.merge(childStats)
            }
        }
        
        return stats
    }
    
    private fun extractImportantTexts(node: JSONObject, depth: Int): List<TextInfo> {
        val texts = mutableListOf<TextInfo>()
        
        val text = node.optString("text", "")
        val className = node.optString("className", "")
        val bounds = node.optJSONObject("bounds")?.let {
            "(${it.optInt("left")},${it.optInt("top")})"
        } ?: ""
        
        // 收集重要的文本
        if (text.isNotBlank() && text.length > 3 && 
            !text.contains("点赞|收藏|评论|分享|更多|查看".toRegex())) {
            texts.add(TextInfo(text, className, depth, bounds))
        }
        
        // 递归处理子节点
        val children = node.optJSONArray("children")
        children?.let {
            for (i in 0 until it.length()) {
                val child = it.getJSONObject(i)
                texts.addAll(extractImportantTexts(child, depth + 1))
            }
        }
        
        return texts.sortedWith(compareBy({ it.depth }, { -it.text.length }))
    }
    
    private fun findRecyclerViews(node: JSONObject, depth: Int): List<RecyclerViewInfo> {
        val recyclerViews = mutableListOf<RecyclerViewInfo>()
        
        val className = node.optString("className", "")
        if (className.contains("RecyclerView")) {
            val childCount = node.optInt("childCount", 0)
            val bounds = node.optJSONObject("bounds")?.let {
                "(${it.optInt("left")},${it.optInt("top")})"
            } ?: ""
            
            // 收集重要的子节点信息
            val importantChildren = mutableListOf<TextInfo>()
            val children = node.optJSONArray("children")
            children?.let {
                for (i in 0 until it.length()) {
                    val child = it.getJSONObject(i)
                    importantChildren.addAll(extractImportantTexts(child, depth + 1))
                }
            }
            
            recyclerViews.add(RecyclerViewInfo(depth, childCount, bounds, importantChildren))
        }
        
        // 递归查找子节点中的RecyclerView
        val children = node.optJSONArray("children")
        children?.let {
            for (i in 0 until it.length()) {
                val child = it.getJSONObject(i)
                recyclerViews.addAll(findRecyclerViews(child, depth + 1))
            }
        }
        
        return recyclerViews
    }
    
    data class UITreeStats(
        var totalNodes: Int = 0,
        var textNodes: Int = 0,
        var clickableNodes: Int = 0,
        var recyclerViews: Int = 0,
        var maxDepth: Int = 0
    ) {
        fun merge(other: UITreeStats) {
            totalNodes += other.totalNodes
            textNodes += other.textNodes
            clickableNodes += other.clickableNodes
            recyclerViews += other.recyclerViews
            maxDepth = maxOf(maxDepth, other.maxDepth)
        }
    }
    
    data class TextInfo(
        val text: String,
        val className: String,
        val depth: Int,
        val bounds: String
    )
    
    data class RecyclerViewInfo(
        val depth: Int,
        val childCount: Int,
        val bounds: String,
        val importantChildren: List<TextInfo>
    )
}