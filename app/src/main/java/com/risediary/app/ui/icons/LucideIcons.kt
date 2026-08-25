package com.risediary.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Lucide icons (https://lucide.dev, ISC License) as Compose ImageVectors.
 * Stroke 2px round caps by default; Filled variants for selected/emphasis states.
 */
object LucideIcons {
    private class PathSpec(val d: String, val filled: Boolean)

    private fun icon(name: String, vararg specs: PathSpec): ImageVector =
        ImageVector.Builder(
            name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            specs.forEach { spec ->
                addPath(
                    pathData = PathParser().parsePathString(spec.d).toNodes(),
                    fill = if (spec.filled) SolidColor(Color.Black) else null,
                    stroke = if (spec.filled) null else SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun iconFilled(name: String, vararg ds: String): ImageVector =
        ImageVector.Builder(
            name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            val nodes = ds.flatMap { d -> PathParser().parsePathString(d).toNodes() }
            addPath(
                pathData = nodes,
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd,
            )
        }.build()

    val House: ImageVector by lazy {
        icon("House",
                PathSpec("M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8", filled = false),
                PathSpec("M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", filled = false),
        )
    }
    val HouseFilled: ImageVector by lazy {
        iconFilled("HouseFilled",
                "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
                "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
        )
    }

    val List: ImageVector by lazy {
        icon("List",
                PathSpec("M3 5h.01", filled = false),
                PathSpec("M3 12h.01", filled = false),
                PathSpec("M3 19h.01", filled = false),
                PathSpec("M8 5h13", filled = false),
                PathSpec("M8 12h13", filled = false),
                PathSpec("M8 19h13", filled = false),
        )
    }
    val ListFilled: ImageVector by lazy {
        iconFilled("ListFilled",
                "M 1.25 5 a 1.75 1.75 0 1 0 3.5 0 a 1.75 1.75 0 1 0 -3.5 0 z",
                "M 1.25 12 a 1.75 1.75 0 1 0 3.5 0 a 1.75 1.75 0 1 0 -3.5 0 z",
                "M 1.25 19 a 1.75 1.75 0 1 0 3.5 0 a 1.75 1.75 0 1 0 -3.5 0 z",
                "M 8 3.5 h 13 v 3 h -13 z",
                "M 8 10.5 h 13 v 3 h -13 z",
                "M 8 17.5 h 13 v 3 h -13 z",
        )
    }

    val Settings: ImageVector by lazy {
        icon("Settings",
                PathSpec("M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915", filled = false),
                PathSpec("M 9 12 a 3 3 0 1 0 6 0 a 3 3 0 1 0 -6 0 z", filled = false),
        )
    }
    val SettingsFilled: ImageVector by lazy {
        iconFilled("SettingsFilled",
                "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
                "M 9 12 a 3 3 0 1 0 6 0 a 3 3 0 1 0 -6 0 z",
        )
    }

    val PlaneTakeoff: ImageVector by lazy {
        icon("PlaneTakeoff",
                PathSpec("M2 22h20", filled = false),
                PathSpec("M6.36 17.4 4 17l-2-4 1.1-.55a2 2 0 0 1 1.8 0l.17.1a2 2 0 0 0 1.8 0L8 12 5 6l.9-.45a2 2 0 0 1 2.09.2l4.02 3a2 2 0 0 0 2.1.2l4.19-2.06a2.41 2.41 0 0 1 1.73-.17L21 7a1.4 1.4 0 0 1 .87 1.99l-.38.76c-.23.46-.6.84-1.07 1.08L7.58 17.2a2 2 0 0 1-1.22.18Z", filled = false),
        )
    }

    val PlaneTakeoffLite: ImageVector by lazy {
        icon("PlaneTakeoffLite",
                PathSpec("M6.36 17.4 4 17l-2-4 1.1-.55a2 2 0 0 1 1.8 0l.17.1a2 2 0 0 0 1.8 0L8 12 5 6l.9-.45a2 2 0 0 1 2.09.2l4.02 3a2 2 0 0 0 2.1.2l4.19-2.06a2.41 2.41 0 0 1 1.73-.17L21 7a1.4 1.4 0 0 1 .87 1.99l-.38.76c-.23.46-.6.84-1.07 1.08L7.58 17.2a2 2 0 0 1-1.22.18Z", filled = false),
        )
    }

    val CircleCheckBig: ImageVector by lazy {
        icon("CircleCheckBig",
                PathSpec("M21.801 10A10 10 0 1 1 17 3.335", filled = false),
                PathSpec("m9 11 3 3L22 4", filled = false),
        )
    }

    val Filter: ImageVector by lazy {
        icon("Filter",
                PathSpec("M22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3z", filled = false),
        )
    }

    val CheckCircleOutline: ImageVector by lazy {
        iconFilled("CheckCircleOutline",
                "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z",
                "M12 4c-4.42 0-8 3.58-8 8s3.58 8 8 8 8-3.58 8-8-3.58-8-8-8z",
                "M9.29 16.29 5.7 12.7c-0.39-0.39-0.39-1.02 0-1.41 0.39-0.39 1.02-0.39 1.41 0L10 14.17l6.88-6.88c0.39-0.39 1.02-0.39 1.41 0 0.39 0.39 0.39 1.02 0 1.41l-7.59 7.59c-0.38 0.39-1.02 0.39-1.41 0z",
        )
    }
    val PlaneTakeoffFilled: ImageVector by lazy {
        iconFilled("PlaneTakeoffFilled",
                "M2 22h20",
                "M6.36 17.4 4 17l-2-4 1.1-.55a2 2 0 0 1 1.8 0l.17.1a2 2 0 0 0 1.8 0L8 12 5 6l.9-.45a2 2 0 0 1 2.09.2l4.02 3a2 2 0 0 0 2.1.2l4.19-2.06a2.41 2.41 0 0 1 1.73-.17L21 7a1.4 1.4 0 0 1 .87 1.99l-.38.76c-.23.46-.6.84-1.07 1.08L7.58 17.2a2 2 0 0 1-1.22.18Z",
        )
    }

    val Droplet: ImageVector by lazy {
        icon("Droplet",
                PathSpec("M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z", filled = false),
        )
    }
    val DropletFilled: ImageVector by lazy {
        iconFilled("DropletFilled",
                "M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z",
        )
    }

    val Ruler: ImageVector by lazy {
        icon("Ruler",
                PathSpec("M21.3 15.3a2.4 2.4 0 0 1 0 3.4l-2.6 2.6a2.4 2.4 0 0 1-3.4 0L2.7 8.7a2.41 2.41 0 0 1 0-3.4l2.6-2.6a2.41 2.41 0 0 1 3.4 0Z", filled = false),
                PathSpec("m14.5 12.5 2-2", filled = false),
                PathSpec("m11.5 9.5 2-2", filled = false),
                PathSpec("m8.5 6.5 2-2", filled = false),
                PathSpec("m17.5 15.5 2-2", filled = false),
        )
    }

    val Scan: ImageVector by lazy {
        icon("Scan",
                PathSpec("M3 7V5a2 2 0 0 1 2-2h2", filled = false),
                PathSpec("M17 3h2a2 2 0 0 1 2 2v2", filled = false),
                PathSpec("M21 17v2a2 2 0 0 1-2 2h-2", filled = false),
                PathSpec("M7 21H5a2 2 0 0 1-2-2v-2", filled = false),
        )
    }

    val BatteryWarning: ImageVector by lazy {
        icon("BatteryWarning",
                PathSpec("M10 17h.01", filled = false),
                PathSpec("M10 7v6", filled = false),
                PathSpec("M14 6h2a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2", filled = false),
                PathSpec("M22 14v-4", filled = false),
                PathSpec("M6 18H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2", filled = false),
        )
    }

    val Trophy: ImageVector by lazy {
        icon("Trophy",
                PathSpec("M10 14.66V17a1 1 0 0 1-1 1 2 2 0 0 0-2 2v2", filled = false),
                PathSpec("M14 14.66V17a1 1 0 0 0 1 1 2 2 0 0 1 2 2v2", filled = false),
                PathSpec("M17.916 10H19.5A2.5 2.5 0 0 0 22 7.5V5a1 1 0 0 0-1-1h-3", filled = false),
                PathSpec("M4 22h16", filled = false),
                PathSpec("M6 9a6 6 0 0 0 12 0V3a1 1 0 0 0-1-1H7a1 1 0 0 0-1 1z", filled = false),
                PathSpec("M6.084 10H4.5A2.5 2.5 0 0 1 2 7.5V5a1 1 0 0 1 1-1h3", filled = false),
        )
    }

    val Repeat2: ImageVector by lazy {
        icon("Repeat2",
                PathSpec("m2 9 3-3 3 3", filled = false),
                PathSpec("M13 18H7a2 2 0 0 1-2-2V6", filled = false),
                PathSpec("m22 15-3 3-3-3", filled = false),
                PathSpec("M11 6h6a2 2 0 0 1 2 2v10", filled = false),
        )
    }

    val ChartLine: ImageVector by lazy {
        icon("ChartLine",
                PathSpec("M3 3v16a2 2 0 0 0 2 2h16", filled = false),
                PathSpec("m19 9-5 5-4-4-3 3", filled = false),
        )
    }

    val Lightbulb: ImageVector by lazy {
        icon("Lightbulb",
                PathSpec("M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5", filled = false),
                PathSpec("M9 18h6", filled = false),
                PathSpec("M10 22h4", filled = false),
        )
    }

    val GraduationCap: ImageVector by lazy {
        icon("GraduationCap",
                PathSpec("M21.42 10.922a1 1 0 0 0-.019-1.838L12.83 5.18a2 2 0 0 0-1.66 0L2.6 9.08a1 1 0 0 0 0 1.832l8.57 3.908a2 2 0 0 0 1.66 0z", filled = false),
                PathSpec("M22 10v6", filled = false),
                PathSpec("M6 12.5V16a6 3 0 0 0 12 0v-3.5", filled = false),
        )
    }

    val Tag: ImageVector by lazy {
        icon("Tag",
                PathSpec("M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z", filled = false),
                PathSpec("M 7 7.5 a 0.5 0.5 0 1 0 1 0 a 0.5 0.5 0 1 0 -1 0 z", filled = true),
        )
    }

    val User: ImageVector by lazy {
        icon("User",
                PathSpec("M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", filled = false),
                PathSpec("M 8 7 a 4 4 0 1 0 8 0 a 4 4 0 1 0 -8 0 z", filled = false),
        )
    }

    val HardDrive: ImageVector by lazy {
        icon("HardDrive",
                PathSpec("M10 16h.01", filled = false),
                PathSpec("M2.212 11.577a2 2 0 0 0-.212.896V18a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-5.527a2 2 0 0 0-.212-.896L18.55 5.11A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z", filled = false),
                PathSpec("M21.946 12.013H2.054", filled = false),
                PathSpec("M6 16h.01", filled = false),
        )
    }

    val ChartColumn: ImageVector by lazy {
        icon("ChartColumn",
                PathSpec("M3 3v16a2 2 0 0 0 2 2h16", filled = false),
                PathSpec("M18 17V9", filled = false),
                PathSpec("M13 17V5", filled = false),
                PathSpec("M8 17v-3", filled = false),
        )
    }

    val LayoutDashboard: ImageVector by lazy {
        icon("LayoutDashboard",
                PathSpec("M 4 4 h 5 a 1 1 0 0 1 1 1 v 7 a 1 1 0 0 1 -1 1 h -5 a 1 1 0 0 1 -1 -1 v -7 a 1 1 0 0 1 1 -1 z", filled = false),
                PathSpec("M 15 4 h 5 a 1 1 0 0 1 1 1 v 3 a 1 1 0 0 1 -1 1 h -5 a 1 1 0 0 1 -1 -1 v -3 a 1 1 0 0 1 1 -1 z", filled = false),
                PathSpec("M 15 13 h 5 a 1 1 0 0 1 1 1 v 7 a 1 1 0 0 1 -1 1 h -5 a 1 1 0 0 1 -1 -1 v -7 a 1 1 0 0 1 1 -1 z", filled = false),
                PathSpec("M 4 17 h 5 a 1 1 0 0 1 1 1 v 3 a 1 1 0 0 1 -1 1 h -5 a 1 1 0 0 1 -1 -1 v -3 a 1 1 0 0 1 1 -1 z", filled = false),
        )
    }

    val Sparkles: ImageVector by lazy {
        icon("Sparkles",
                PathSpec("M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z", filled = false),
                PathSpec("M20 2v4", filled = false),
                PathSpec("M22 4h-4", filled = false),
                PathSpec("M 2 20 a 2 2 0 1 0 4 0 a 2 2 0 1 0 -4 0 z", filled = false),
        )
    }

    val Hash: ImageVector by lazy {
        icon("Hash",
                PathSpec("M 4 9 L 20 9", filled = false),
                PathSpec("M 4 15 L 20 15", filled = false),
                PathSpec("M 10 3 L 8 21", filled = false),
                PathSpec("M 16 3 L 14 21", filled = false),
        )
    }

    val DeleteKey: ImageVector by lazy {
        icon("DeleteKey",
                PathSpec("M10 5a2 2 0 0 0-1.344.519l-6.328 5.74a1 1 0 0 0 0 1.481l6.328 5.741A2 2 0 0 0 10 19h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2z", filled = false),
                PathSpec("m12 9 6 6", filled = false),
                PathSpec("m18 9-6 6", filled = false),
        )
    }

    val Square: ImageVector by lazy {
        icon("Square",
                PathSpec("M 5 5 h 14 a 2 2 0 0 1 2 2 v 14 a 2 2 0 0 1 -2 2 h -14 a 2 2 0 0 1 -2 -2 v -14 a 2 2 0 0 1 2 -2 z", filled = false),
        )
    }

    val Save: ImageVector by lazy {
        icon("Save",
                PathSpec("M15.2 3a2 2 0 0 1 1.4.6l3.8 3.8a2 2 0 0 1 .6 1.4V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", filled = false),
                PathSpec("M17 21v-7a1 1 0 0 0-1-1H8a1 1 0 0 0-1 1v7", filled = false),
                PathSpec("M7 3v4a1 1 0 0 0 1 1h7", filled = false),
        )
    }

    val CloudOff: ImageVector by lazy {
        icon("CloudOff",
                PathSpec("M10.94 5.274A7 7 0 0 1 15.71 10h1.79a4.5 4.5 0 0 1 4.222 6.057", filled = false),
                PathSpec("M18.796 18.81A4.5 4.5 0 0 1 17.5 19H9A7 7 0 0 1 5.79 5.78", filled = false),
                PathSpec("m2 2 20 20", filled = false),
        )
    }

    val Play: ImageVector by lazy {
        icon("Play",
                PathSpec("M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z", filled = false),
        )
    }

    val Pause: ImageVector by lazy {
        icon("Pause",
                PathSpec("M 15 4 h 3 a 1 1 0 0 1 1 1 v 16 a 1 1 0 0 1 -1 1 h -3 a 1 1 0 0 1 -1 -1 v -16 a 1 1 0 0 1 1 -1 z", filled = false),
                PathSpec("M 6 4 h 3 a 1 1 0 0 1 1 1 v 16 a 1 1 0 0 1 -1 1 h -3 a 1 1 0 0 1 -1 -1 v -16 a 1 1 0 0 1 1 -1 z", filled = false),
        )
    }

    val Timer: ImageVector by lazy {
        icon("Timer",
                PathSpec("M 10 2 L 14 2", filled = false),
                PathSpec("M 12 14 L 15 11", filled = false),
                PathSpec("M 4 14 a 8 8 0 1 0 16 0 a 8 8 0 1 0 -16 0 z", filled = false),
        )
    }

    val Clock: ImageVector by lazy {
        icon("Clock",
                PathSpec("M 2 12 a 10 10 0 1 0 20 0 a 10 10 0 1 0 -20 0 z", filled = false),
                PathSpec("M12 6v6l4 2", filled = false),
        )
    }

    val CalendarDays: ImageVector by lazy {
        icon("CalendarDays",
                PathSpec("M8 2v3", filled = false),
                PathSpec("M16 2v3", filled = false),
                PathSpec("M 5 5 h 14 a 2 2 0 0 1 2 2 v 14 a 2 2 0 0 1 -2 2 h -14 a 2 2 0 0 1 -2 -2 v -14 a 2 2 0 0 1 2 -2 z", filled = false),
                PathSpec("M3 9h18", filled = false),
                PathSpec("M8 13h.01", filled = false),
                PathSpec("M12 13h.01", filled = false),
                PathSpec("M16 13h.01", filled = false),
                PathSpec("M8 17h.01", filled = false),
                PathSpec("M12 17h.01", filled = false),
                PathSpec("M16 17h.01", filled = false),
        )
    }

    val Volume2: ImageVector by lazy {
        icon("Volume2",
                PathSpec("M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z", filled = false),
                PathSpec("M16 9a5 5 0 0 1 0 6", filled = false),
                PathSpec("M19.364 18.364a9 9 0 0 0 0-12.728", filled = false),
        )
    }

    val AlarmClock: ImageVector by lazy {
        icon("AlarmClock",
                PathSpec("M 4 13 a 8 8 0 1 0 16 0 a 8 8 0 1 0 -16 0 z", filled = false),
                PathSpec("M12 9v4l2 2", filled = false),
                PathSpec("M5 3 2 6", filled = false),
                PathSpec("m22 6-3-3", filled = false),
                PathSpec("M6.38 18.7 4 21", filled = false),
                PathSpec("M17.64 18.67 20 21", filled = false),
        )
    }

    val BellRing: ImageVector by lazy {
        icon("BellRing",
                PathSpec("M10.268 21a2 2 0 0 0 3.464 0", filled = false),
                PathSpec("M22 8c0-2.3-.8-4.3-2-6", filled = false),
                PathSpec("M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326", filled = false),
                PathSpec("M4 2C2.8 3.7 2 5.7 2 8", filled = false),
        )
    }

    val CalendarRange: ImageVector by lazy {
        icon("CalendarRange",
                PathSpec("M 3 4 h 18 v 16 a 2 2 0 0 1 -2 2 H 5 a 2 2 0 0 1 -2 -2 z", filled = false),
                PathSpec("M16 2v4", filled = false),
                PathSpec("M3 10h18", filled = false),
                PathSpec("M8 2v4", filled = false),
                PathSpec("M17 14h-6", filled = false),
                PathSpec("M13 18H7", filled = false),
                PathSpec("M7 14h.01", filled = false),
                PathSpec("M17 18h.01", filled = false),
        )
    }

    val Trash2: ImageVector by lazy {
        icon("Trash2",
                PathSpec("M3 6h18", filled = false),
                PathSpec("M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", filled = false),
                PathSpec("M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2", filled = false),
                PathSpec("M10 11v6", filled = false),
                PathSpec("M14 11v6", filled = false),
        )
    }

    val LockKeyhole: ImageVector by lazy {
        icon("LockKeyhole",
                PathSpec("M 11 16 a 1 1 0 1 0 2 0 a 1 1 0 1 0 -2 0 z", filled = false),
                PathSpec("M 3 10 h 18 v 10 a 2 2 0 0 1 -2 2 H 5 a 2 2 0 0 1 -2 -2 z", filled = false),
                PathSpec("M7 10V7a5 5 0 0 1 10 0v3", filled = false),
        )
    }

}
