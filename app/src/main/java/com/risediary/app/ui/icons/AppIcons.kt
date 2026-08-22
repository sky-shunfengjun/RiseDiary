package com.risediary.app.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Th1
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.icon.extended.VolumeUp
import top.yukonga.miuix.kmp.icon.extended.WorldClock

/**
 * 统一图标别名：Material Icons → miuix-icons（Batch 6）。
 *
 * 语义映射（无同义词时按最接近的 miuix 图标兜底）：
 * - WaterDrop/液体 → Location（水滴形）；Straighten/距离 → Pin
 * - CalendarMonth/DateRange → Months；Schedule → WorldClock；EventRepeat → Refresh
 * - NotificationsActive → Alarm；Fingerprint → Lock；LockReset → Lock
 * - FlightTakeoff → Send；SystemUpdate → Update；Upload → UploadCloud
 * - Palette → Theme；Person → Contacts；LocalOffer/EditNote → Notes
 * - School/Lightbulb/BatteryAlert → Info；CleaningServices/DeleteForever → Delete
 * - Dashboard → GridView；ShowChart/Reorder → Sort；Insights → Layers
 * - EmojiEvents → FavoritesFill；TrackChanges → Favorites；Numbers → Th1
 * - Storage → Folder；CloudOff → CloudFill；Save/CheckCircle → Ok
 * - Stop → Close；Visibility → Show；VisibilityOff → Hide
 * - ChevronLeft/Right → ChevronBackward/Forward；ArrowBack → Back
 */
object AppIcons {
    val Add: ImageVector get() = MiuixIcons.Add
    val Alarm: ImageVector get() = MiuixIcons.Alarm
    val ArrowBack: ImageVector get() = MiuixIcons.Back
    val Backspace: ImageVector get() = MiuixIcons.Clear
    val Backup: ImageVector get() = MiuixIcons.Backup
    val BatteryAlert: ImageVector get() = MiuixIcons.Info
    val CalendarMonth: ImageVector get() = MiuixIcons.Months
    val Check: ImageVector get() = MiuixIcons.Basic.Check
    val CheckCircle: ImageVector get() = MiuixIcons.Ok
    val ChevronLeft: ImageVector get() = MiuixIcons.ChevronBackward
    val ChevronRight: ImageVector get() = MiuixIcons.ChevronForward
    val CleaningServices: ImageVector get() = MiuixIcons.Delete
    val CloudOff: ImageVector get() = MiuixIcons.CloudFill
    val Dashboard: ImageVector get() = MiuixIcons.GridView
    val DateRange: ImageVector get() = MiuixIcons.Months
    val Delete: ImageVector get() = MiuixIcons.Delete
    val DeleteForever: ImageVector get() = MiuixIcons.Delete
    val Download: ImageVector get() = MiuixIcons.Download
    val DragHandle: ImageVector get() = MiuixIcons.More
    val Edit: ImageVector get() = MiuixIcons.Edit
    val EditNote: ImageVector get() = MiuixIcons.Notes
    val EmojiEvents: ImageVector get() = MiuixIcons.FavoritesFill
    val EventRepeat: ImageVector get() = MiuixIcons.Refresh
    val Fingerprint: ImageVector get() = MiuixIcons.Lock
    val FlightTakeoff: ImageVector get() = MiuixIcons.Send
    val Home: ImageVector get() = MiuixIcons.Home
    val Info: ImageVector get() = MiuixIcons.Info
    val Insights: ImageVector get() = MiuixIcons.Layers
    val Lightbulb: ImageVector get() = MiuixIcons.Info
    val List: ImageVector get() = MiuixIcons.ListView
    val LocalOffer: ImageVector get() = MiuixIcons.Notes
    val Lock: ImageVector get() = MiuixIcons.Lock
    val LockReset: ImageVector get() = MiuixIcons.Lock
    val Notes: ImageVector get() = MiuixIcons.Notes
    val NotificationsActive: ImageVector get() = MiuixIcons.Alarm
    val Numbers: ImageVector get() = MiuixIcons.Th1
    val Palette: ImageVector get() = MiuixIcons.Theme
    val Pause: ImageVector get() = MiuixIcons.Pause
    val Person: ImageVector get() = MiuixIcons.Contacts
    val PlayArrow: ImageVector get() = MiuixIcons.Play
    val Refresh: ImageVector get() = MiuixIcons.Refresh
    val Reorder: ImageVector get() = MiuixIcons.Sort
    val Save: ImageVector get() = MiuixIcons.Ok
    val Schedule: ImageVector get() = MiuixIcons.WorldClock
    val School: ImageVector get() = MiuixIcons.Info
    val Settings: ImageVector get() = MiuixIcons.Settings
    val ShowChart: ImageVector get() = MiuixIcons.Sort
    val Stop: ImageVector get() = MiuixIcons.Basic.Close
    val Storage: ImageVector get() = MiuixIcons.Folder
    val Straighten: ImageVector get() = MiuixIcons.Pin
    val SystemUpdate: ImageVector get() = MiuixIcons.Update
    val Timer: ImageVector get() = MiuixIcons.Timer
    val TrackChanges: ImageVector get() = MiuixIcons.Favorites
    val Tune: ImageVector get() = MiuixIcons.Tune
    val Upload: ImageVector get() = MiuixIcons.UploadCloud
    val Visibility: ImageVector get() = MiuixIcons.Show
    val VisibilityOff: ImageVector get() = MiuixIcons.Hide
    val VolumeUp: ImageVector get() = MiuixIcons.VolumeUp
    val WaterDrop: ImageVector get() = MiuixIcons.Location
}
