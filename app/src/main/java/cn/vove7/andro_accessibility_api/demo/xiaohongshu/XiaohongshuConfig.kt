package cn.vove7.andro_accessibility_api.demo.xiaohongshu

/**
 * 小红书数据抓取配置
 */
data class XiaohongshuConfig(
    // 基础配置
    val packageName: String = "com.xingin.xhs",
    val appName: String = "小红书",
    
    // 抓取配置
    val maxScrollAttempts: Int = 5,
    val scrollDelayMs: Long = 2000,
    val waitTimeoutMs: Long = 5000,
    val maxRetryCount: Int = 3,
    
    // 数据抓取开关
    val enableNoteExtraction: Boolean = true,
    val enableUserExtraction: Boolean = true,
    val enableCommentExtraction: Boolean = true,
    val enableInteractionExtraction: Boolean = true,
    val enableTagExtraction: Boolean = true,
    val enableImageExtraction: Boolean = false,
    
    // 信息流抓取配置
    val feedMaxNotes: Int = 50,
    val feedScrollDelay: Long = 1500,
    
    // 搜索配置
    val searchMaxResults: Int = 30,
    val searchScrollDelay: Long = 2000,
    
    // 评论抓取配置
    val maxCommentsPerNote: Int = 20,
    val commentScrollDelay: Long = 1000,
    
    // 输出配置
    val outputFormat: OutputFormat = OutputFormat.JSON,
    val enableDebugLog: Boolean = true,
    val enableScreenshot: Boolean = false,
    
    // 防检测配置
    val randomDelayRange: IntRange = 500..1500,
    val enableAntiDetection: Boolean = true,
    val simulateHumanBehavior: Boolean = true
)

/**
 * 输出格式枚举
 */
enum class OutputFormat {
    JSON,
    CSV,
    XML,
    TEXT
}

/**
 * 页面类型枚举
 */
enum class PageType {
    UNKNOWN,
    FEED_LIST,      // 信息流列表
    NOTE_DETAIL,    // 笔记详情
    USER_PROFILE,   // 用户资料
    SEARCH_RESULT,  // 搜索结果
    COMMENT_LIST,   // 评论列表
    DISCOVERY,      // 发现页面
    SHOPPING        // 购物页面
}

/**
 * 抓取状态枚举
 */
enum class ExtractionStatus {
    IDLE,           // 空闲
    INITIALIZING,   // 初始化中
    RUNNING,        // 运行中
    PAUSED,         // 暂停
    COMPLETED,      // 完成
    ERROR,          // 错误
    CANCELLED       // 取消
}

/**
 * 抓取任务类型
 */
enum class TaskType {
    SINGLE_NOTE,    // 单个笔记
    FEED_LIST,      // 信息流
    USER_PROFILE,   // 用户资料
    SEARCH_RESULT,  // 搜索结果
    BATCH_EXTRACT,  // 批量抓取
    MONITOR_MODE    // 监控模式
}