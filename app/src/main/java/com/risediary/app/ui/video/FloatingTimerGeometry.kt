package com.risediary.app.ui.video

internal data class FloatingPoint(val x: Float, val y: Float)

/** Fractions of the available travel, so rotating preserves the relative position. */
internal data class FloatingTimerPosition(val x: Float = 0f, val y: Float = 0f)

/** Match the shared clock's six digits and two colons, plus two small touch insets. */
internal fun floatingTimerCapsuleWidth(availableWidth: Float, fontScale: Float): Float {
    val clockWidth = 258f * 22f / 52f * fontScale
    return minOf(clockWidth + 16f, availableWidth.coerceAtLeast(0f) * 0.55f)
}

internal fun floatingTimerOffset(
    position: FloatingTimerPosition, width: Float, height: Float,
    capsuleWidth: Float, capsuleHeight: Float
): FloatingPoint = FloatingPoint(
    position.x.coerceIn(0f, 1f) * (width - capsuleWidth).coerceAtLeast(0f),
    position.y.coerceIn(0f, 1f) * (height - capsuleHeight).coerceAtLeast(0f)
)

internal fun moveFloatingTimer(
    position: FloatingTimerPosition, dx: Float, dy: Float,
    width: Float, height: Float, capsuleWidth: Float, capsuleHeight: Float
): FloatingTimerPosition {
    val travelX = (width - capsuleWidth).coerceAtLeast(0f)
    val travelY = (height - capsuleHeight).coerceAtLeast(0f)
    return FloatingTimerPosition(
        if (travelX > 0f) (position.x + dx / travelX).coerceIn(0f, 1f) else position.x,
        if (travelY > 0f) (position.y + dy / travelY).coerceIn(0f, 1f) else position.y
    )
}

internal fun floatingTimerPanelOffset(
    anchor: FloatingPoint, capsuleHeight: Float, panelWidth: Float, panelHeight: Float,
    width: Float, height: Float
): FloatingPoint {
    // The expanded surface includes the capsule header, preserving the clock's anchor.
    val y = if (anchor.y + panelHeight <= height) anchor.y else anchor.y + capsuleHeight - panelHeight
    return FloatingPoint(
        anchor.x.coerceIn(0f, (width - panelWidth).coerceAtLeast(0f)),
        y.coerceIn(0f, (height - panelHeight).coerceAtLeast(0f))
    )
}
