package com.risediary.app.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.icon.extended.UploadCloud

/**
 * 统一图标别名（Batch 6 引入，Dev16 校准）。
 *
 * 原则：
 * - miuix-icons 语义准确的直接用；
 * - miuix 缺失或语义偏离的用 LucideIcons（描边 2px，风格与 miuix 一致）；
 * - 默认描边（空心），仅强调态（底栏选中）用 Filled 实心变体。
 */
object AppIcons {
    // ── miuix 直用 ──
    val Add: ImageVector get() = MiuixIcons.Add
    val ArrowBack: ImageVector get() = MiuixIcons.Back
    val Backup: ImageVector get() = MiuixIcons.Backup
    val Check: ImageVector get() = MiuixIcons.Basic.Check
    val CheckCircle: ImageVector get() = LucideIcons.CheckCircleOutline
    val Filter: ImageVector get() = LucideIcons.Filter
    val ChevronLeft: ImageVector get() = MiuixIcons.ChevronBackward
    val ChevronRight: ImageVector get() = MiuixIcons.ChevronForward
    val Close: ImageVector get() = MiuixIcons.Basic.Close
    val Delete: ImageVector get() = MiuixIcons.Delete
    val DeleteForever: ImageVector get() = LucideIcons.Trash2
    val Download: ImageVector get() = MiuixIcons.Download
    val DragHandle: ImageVector get() = MiuixIcons.More
    val Edit: ImageVector get() = MiuixIcons.Edit
    val EditNote: ImageVector get() = MiuixIcons.Notes
    val Info: ImageVector get() = MiuixIcons.Info
    val Lock: ImageVector get() = MiuixIcons.Lock
    val LockReset: ImageVector get() = LucideIcons.LockKeyhole
    val Notes: ImageVector get() = MiuixIcons.Notes
    val Palette: ImageVector get() = MiuixIcons.Theme
    val Refresh: ImageVector get() = MiuixIcons.Refresh
    val Reorder: ImageVector get() = MiuixIcons.Sort
    val SystemUpdate: ImageVector get() = MiuixIcons.Update
    val Tune: ImageVector get() = MiuixIcons.Tune
    val Upload: ImageVector get() = MiuixIcons.UploadCloud
    val Visibility: ImageVector get() = MiuixIcons.Show
    val VisibilityOff: ImageVector get() = MiuixIcons.Hide

    // ── lucide 替代（语义更准 / 统一空心） ──
    val Alarm: ImageVector get() = LucideIcons.AlarmClock
    val Backspace: ImageVector get() = LucideIcons.DeleteKey
    val BatteryAlert: ImageVector get() = LucideIcons.BatteryWarning
    val CalendarMonth: ImageVector get() = LucideIcons.CalendarDays
    val CleaningServices: ImageVector get() = LucideIcons.Sparkles
    val CloudOff: ImageVector get() = LucideIcons.CloudOff
    val Dashboard: ImageVector get() = LucideIcons.LayoutDashboard
    val DateRange: ImageVector get() = LucideIcons.CalendarRange
    val EmojiEvents: ImageVector get() = LucideIcons.Trophy
    val EventRepeat: ImageVector get() = LucideIcons.Repeat2
    val Fingerprint: ImageVector get() = LucideIcons.Scan
    val Insights: ImageVector get() = LucideIcons.ChartLine
    val Lightbulb: ImageVector get() = LucideIcons.Lightbulb
    val LocalOffer: ImageVector get() = LucideIcons.Tag
    val NotificationsActive: ImageVector get() = LucideIcons.BellRing
    val Numbers: ImageVector get() = LucideIcons.Hash
    val Pause: ImageVector get() = LucideIcons.Pause
    val Person: ImageVector get() = LucideIcons.User
    val PlayArrow: ImageVector get() = LucideIcons.Play
    val Save: ImageVector get() = LucideIcons.Save
    val Schedule: ImageVector get() = LucideIcons.Clock
    val School: ImageVector get() = LucideIcons.GraduationCap
    val ShowChart: ImageVector get() = LucideIcons.ChartColumn
    val Stop: ImageVector get() = LucideIcons.Square
    val Storage: ImageVector get() = LucideIcons.HardDrive
    val Straighten: ImageVector get() = LucideIcons.Ruler
    val Timer: ImageVector get() = LucideIcons.Timer
    val TrackChanges: ImageVector get() = LucideIcons.Ruler
    val VolumeUp: ImageVector get() = LucideIcons.Volume2

    // ── 强调态实心（被液体玻璃覆盖时为实心） ──
    val FlightTakeoff: ImageVector get() = LucideIcons.PlaneTakeoff
    val FlightTakeoffLite: ImageVector get() = LucideIcons.PlaneTakeoffLite
    val WaterDrop: ImageVector get() = LucideIcons.DropletFilled

    // ── 底栏（描边/实心成对） ──
    val Home: ImageVector get() = LucideIcons.House
    val HomeFilled: ImageVector get() = LucideIcons.HouseFilled
    val List: ImageVector get() = LucideIcons.List
    val ListFilled: ImageVector get() = LucideIcons.ListFilled
    val Settings: ImageVector get() = LucideIcons.Settings
    val SettingsFilled: ImageVector get() = LucideIcons.SettingsFilled
}
