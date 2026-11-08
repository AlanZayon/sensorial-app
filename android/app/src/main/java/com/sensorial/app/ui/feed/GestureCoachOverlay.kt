package com.sensorial.app.ui.feed

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val PrefsName = "deriva_prefs"
private const val KeyCoachSeen = "gesture_coach_seen"

fun isGestureCoachSeen(context: Context): Boolean =
    context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        .getBoolean(KeyCoachSeen, false)

fun markGestureCoachSeen(context: Context) {
    context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KeyCoachSeen, true)
        .apply()
}

@Composable
fun GestureCoachOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                markGestureCoachSeen(context)
                onDismiss()
            }
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Deriva",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
            )
            Text(
                text = "Deslize para descobrir",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(8.dp))
            CoachLine("↑", "Próxima")
            CoachLine("↓", "Anterior")
            CoachLine("←", "Aprofundar")
            CoachLine("→", "Explorar")
            CoachLine("♥ ou duplo toque", "Gostei / desfazer")
            CoachLine("toque na capa", "Pausar / tocar")
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Toque para fechar",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Toque em Deriva para ver de novo",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun CoachLine(gesture: String, meaning: String) {
    Text(
        text = "$gesture  ·  $meaning",
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.9f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
