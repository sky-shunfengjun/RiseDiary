package com.risediary.app.ui.achievement

data class AchievementDef(
    val key: String,
    val icon: String,
    val name: String,
    val description: String,
    val category: String,
    val shortName: String = name
)

object AchievementCatalog {
    val definitions = listOf(
        AchievementDef("milestone_1", "✈️", "首次起飞", "完成第一次飞行记录", "milestone"),
        AchievementDef("milestone_10", "🔟", "两位数", "累计完成 10 次飞行", "milestone"),
        AchievementDef("milestone_50", "🏅", "半百飞将", "累计完成 50 次飞行", "milestone", "半百"),
        AchievementDef("milestone_100", "💯", "百飞成就", "累计完成 100 次飞行", "milestone", "百飞"),
        AchievementDef("milestone_500", "👑", "五百飞王", "累计完成 500 次飞行", "milestone", "五百飞"),
        AchievementDef("record_distance_30", "🎯", "三十厘米射手", "单次射精距离 ≥ 30cm", "record", "30cm"),
        AchievementDef("record_distance_80", "🚀", "八十厘米炮手", "单次射精距离 ≥ 80cm", "record", "80cm"),
        AchievementDef("record_duration_30", "⏱️", "半小时选手", "单次时长 ≥ 30 分钟", "record", "30分"),
        AchievementDef("record_duration_60", "⏳", "一小时耐力", "单次时长 ≥ 60 分钟", "record", "60分"),
        AchievementDef("record_volume_high", "💦", "充沛输出", "单次 ≥ 15 股 或 ≥ 30ml", "record", "充沛"),
        AchievementDef("marathon", "🏃", "马拉松选手", "单次时长 ≥ 90 分钟", "record", "马拉松"),
        AchievementDef("speedster", "⚡", "快枪手", "单次时长 ≤ 3 分钟", "record"),
        AchievementDef("streak_7", "🔥", "连续一周", "连续 7 天有飞行记录", "streak", "连7天"),
        AchievementDef("streak_30", "📅", "月度全勤", "连续 30 天有飞行记录", "streak", "连30天"),
        AchievementDef("streak_90", "🏆", "季度霸主", "连续 90 天有飞行记录", "streak", "连90天"),
        AchievementDef("volume_100ml", "🥛", "百毫升俱乐部", "累计射精量 ≥ 100ml", "special", "100ml"),
        AchievementDef("volume_500ml", "🍾", "半升级", "累计射精量 ≥ 500ml", "special", "500ml"),
        AchievementDef("tag_5_types", "🏷️", "标签探索者", "使用过 5 种以上标签", "special", "标签"),
        AchievementDef("length_first", "📏", "初次测量", "首次记录长度测量", "special", "初测"),
        AchievementDef("length_growth_2cm", "📈", "增长见证", "勃起长度增长 ≥ 2cm", "special", "+2cm"),
        AchievementDef("special_30day_record", "🌟", "月度之星", "连续记录达到 30 天", "special", "月度星")
    )

    private val byKey = definitions.associateBy(AchievementDef::key)

    fun find(key: String): AchievementDef? = byKey[key]
}
