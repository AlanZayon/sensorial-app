package com.sensorial.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class SpotifyAuthStep {
    WebApi,
    AppRemote,
}

@Composable
fun SpotifyAuthScreen(
    step: SpotifyAuthStep,
    busy: Boolean,
    errorMessage: String?,
    onConnect: () -> Unit,
) {
    val title = when (step) {
        SpotifyAuthStep.WebApi -> "Conectar Spotify"
        SpotifyAuthStep.AppRemote -> "Autorizar reprodução"
    }
    val body = when (step) {
        SpotifyAuthStep.WebApi ->
            "Conecte sua conta para catálogo e descoberta. Em seguida autorizamos a reprodução no app Spotify."
        SpotifyAuthStep.AppRemote ->
            "Último passo: autorize o controle do Spotify Player. " +
                "No Dashboard, a Redirect URI com.sensorial.app://app-remote também precisa estar cadastrada. " +
                "Aceite a permissão na tela do Spotify."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Sensorial",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )
        if (busy) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(title)
            }
        }
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
