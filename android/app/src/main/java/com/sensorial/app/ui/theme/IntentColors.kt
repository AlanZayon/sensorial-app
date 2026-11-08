package com.sensorial.app.ui.theme

import androidx.compose.ui.graphics.Color

object IntentColors {
    val Like = Color(0xFF2A9D8F)
    val Dislike = Color(0xFFD76B4F)
    val Deepen = Color(0xFF4A6FA5)
    val Explore = Color(0xFFC4A35A)
    val NavigateBack = Color(0xFF7A8B94)
    val NavigateForward = Color(0xFF7A8B94)

    @Deprecated("Use Deepen", ReplaceWith("Deepen"))
    val Similar = Deepen

    @Deprecated("Use Like", ReplaceWith("Like"))
    val Positive = Like

    @Deprecated("Use Dislike", ReplaceWith("Dislike"))
    val Negative = Dislike
}
