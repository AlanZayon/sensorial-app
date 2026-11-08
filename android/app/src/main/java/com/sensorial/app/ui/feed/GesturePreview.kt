package com.sensorial.app.ui.feed

/**
 * UI-only gesture preview while the user is dragging.
 * Domain commit happens only after release via [com.sensorial.domain.gesture.GestureInterpreter].
 */
sealed interface GesturePreviewState {
    data object Idle : GesturePreviewState

    data class Dragging(
        val offsetX: Float,
        val offsetY: Float,
        val preview: IntentPreview,
        val progress: Float,
    ) : GesturePreviewState

    data object Settling : GesturePreviewState
}

enum class IntentPreview {
    None,
    Deepen,
    Explore,
    NavigateBack,
    NavigateForward,
}

fun IntentPreview.label(): String = when (this) {
    IntentPreview.None -> ""
    IntentPreview.Deepen -> "APROFUNDAR"
    IntentPreview.Explore -> "EXPLORAR"
    IntentPreview.NavigateBack -> "ANTERIOR"
    IntentPreview.NavigateForward -> "PRÓXIMA"
}
