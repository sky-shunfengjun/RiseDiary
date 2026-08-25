package com.risediary.app.ui.achievement

import androidx.annotation.StringRes
import com.risediary.app.R

data class AchievementDef(
    val key: String,
    val icon: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val category: String,
    @StringRes val shortNameRes: Int = nameRes
)

object AchievementCatalog {
    val definitions = listOf(
        AchievementDef("milestone_1", "✈️", R.string.achievement_name_milestone_1, R.string.achievement_description_milestone_1, "milestone"),
        AchievementDef("milestone_10", "🔟", R.string.achievement_name_milestone_10, R.string.achievement_description_milestone_10, "milestone"),
        AchievementDef("milestone_50", "🏅", R.string.achievement_name_milestone_50, R.string.achievement_description_milestone_50, "milestone", R.string.achievement_short_milestone_50),
        AchievementDef("milestone_100", "💯", R.string.achievement_name_milestone_100, R.string.achievement_description_milestone_100, "milestone", R.string.achievement_short_milestone_100),
        AchievementDef("milestone_500", "👑", R.string.achievement_name_milestone_500, R.string.achievement_description_milestone_500, "milestone", R.string.achievement_short_milestone_500),
        AchievementDef("record_distance_30", "🎯", R.string.achievement_name_record_distance_30, R.string.achievement_description_record_distance_30, "record", R.string.achievement_short_record_distance_30),
        AchievementDef("record_distance_80", "🚀", R.string.achievement_name_record_distance_80, R.string.achievement_description_record_distance_80, "record", R.string.achievement_short_record_distance_80),
        AchievementDef("record_duration_30", "⏱️", R.string.achievement_name_record_duration_30, R.string.achievement_description_record_duration_30, "record", R.string.achievement_short_record_duration_30),
        AchievementDef("record_duration_60", "⏳", R.string.achievement_name_record_duration_60, R.string.achievement_description_record_duration_60, "record", R.string.achievement_short_record_duration_60),
        AchievementDef("record_volume_high", "💦", R.string.achievement_name_record_volume_high, R.string.achievement_description_record_volume_high, "record", R.string.achievement_short_record_volume_high),
        AchievementDef("marathon", "🏃", R.string.achievement_name_marathon, R.string.achievement_description_marathon, "record", R.string.achievement_short_marathon),
        AchievementDef("speedster", "⚡", R.string.achievement_name_speedster, R.string.achievement_description_speedster, "record"),
        AchievementDef("streak_7", "🔥", R.string.achievement_name_streak_7, R.string.achievement_description_streak_7, "streak", R.string.achievement_short_streak_7),
        AchievementDef("streak_30", "📅", R.string.achievement_name_streak_30, R.string.achievement_description_streak_30, "streak", R.string.achievement_short_streak_30),
        AchievementDef("streak_90", "🏆", R.string.achievement_name_streak_90, R.string.achievement_description_streak_90, "streak", R.string.achievement_short_streak_90),
        AchievementDef("volume_100ml", "🥛", R.string.achievement_name_volume_100ml, R.string.achievement_description_volume_100ml, "special", R.string.achievement_short_volume_100ml),
        AchievementDef("volume_500ml", "🍾", R.string.achievement_name_volume_500ml, R.string.achievement_description_volume_500ml, "special", R.string.achievement_short_volume_500ml),
        AchievementDef("tag_5_types", "🏷️", R.string.achievement_name_tag_5_types, R.string.achievement_description_tag_5_types, "special", R.string.achievement_short_tag_5_types),
        AchievementDef("length_first", "📏", R.string.achievement_name_length_first, R.string.achievement_description_length_first, "special", R.string.achievement_short_length_first),
        AchievementDef("length_growth_2cm", "📈", R.string.achievement_name_length_growth_2cm, R.string.achievement_description_length_growth_2cm, "special", R.string.achievement_short_length_growth_2cm),
        AchievementDef("special_30day_record", "🌟", R.string.achievement_name_special_30day_record, R.string.achievement_description_special_30day_record, "special", R.string.achievement_short_special_30day_record)
    )

    private val byKey = definitions.associateBy(AchievementDef::key)

    fun find(key: String): AchievementDef? = byKey[key]
}
