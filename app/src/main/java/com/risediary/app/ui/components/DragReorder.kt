package com.risediary.app.ui.components

/**
 * Applies vertical drag displacement to a mutable ordered list.
 *
 * The returned offset keeps the dragged item visually under the finger after
 * each list move. At the first and last item it is clamped to half an item,
 * preventing large overscroll debt that would otherwise make reverse dragging
 * feel stuck.
 */
internal fun <T> reorderByDragOffset(
    items: MutableList<T>,
    draggedItem: T,
    dragOffsetY: Float,
    itemExtentPx: Float
): Float {
    if (
        items.isEmpty() ||
        !dragOffsetY.isFinite() ||
        !itemExtentPx.isFinite() ||
        itemExtentPx <= 0f
    ) {
        return 0f
    }

    val threshold = itemExtentPx * 0.5f
    var remainingOffset = dragOffsetY

    while (remainingOffset > threshold) {
        val from = items.indexOf(draggedItem)
        if (from < 0) return 0f
        if (from >= items.lastIndex) {
            remainingOffset = threshold
            break
        }
        items.add(from + 1, items.removeAt(from))
        remainingOffset -= itemExtentPx
    }

    while (remainingOffset < -threshold) {
        val from = items.indexOf(draggedItem)
        if (from < 0) return 0f
        if (from <= 0) {
            remainingOffset = -threshold
            break
        }
        items.add(from - 1, items.removeAt(from))
        remainingOffset += itemExtentPx
    }

    return remainingOffset.coerceIn(-threshold, threshold)
}
