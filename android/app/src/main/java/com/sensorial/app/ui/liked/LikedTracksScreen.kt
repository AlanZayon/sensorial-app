package com.sensorial.app.ui.liked

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.Track
import com.sensorial.domain.preference.LikedSort
import com.sensorial.domain.preference.LikedTracks

@Composable
fun LikedTracksScreen(
    state: LikedTracksUiState,
    onBack: () -> Unit,
    onPlay: (Track) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleArtist: (ArtistId) -> Unit,
    onToggleGenre: (GenreId) -> Unit,
    onSortChange: (LikedSort) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        LikedHeader(
            state = state,
            onBack = onBack,
            onClearFilters = onClearFilters,
        )

        LikedSearchField(
            query = state.query,
            onQueryChange = onQueryChange,
        )

        LikedSortRow(
            selected = state.sort,
            onSortChange = onSortChange,
        )

        if (state.artistFilters.isNotEmpty()) {
            FilterSectionLabel("Artistas")
            ChipRow {
                state.artistFilters.forEach { artist ->
                    DerivaFilterChip(
                        label = artist.name,
                        selected = state.selectedArtistId == artist.id,
                        onClick = { onToggleArtist(artist.id) },
                    )
                }
            }
        }

        if (state.genreFilters.isNotEmpty()) {
            FilterSectionLabel("Estilos")
            ChipRow {
                state.genreFilters.forEach { genre ->
                    DerivaFilterChip(
                        label = LikedTracks.genreLabel(genre),
                        selected = state.selectedGenreId == genre,
                        onClick = { onToggleGenre(genre) },
                    )
                }
            }
        }

        if (state.hasActiveFilters) {
            ActiveFiltersBar(
                state = state,
                onClearQuery = { onQueryChange("") },
                onClearArtist = {
                    state.selectedArtistId?.let(onToggleArtist)
                },
                onClearGenre = {
                    state.selectedGenreId?.let(onToggleGenre)
                },
                onClearAll = onClearFilters,
            )
        }

        LikedListBody(
            state = state,
            onPlay = onPlay,
            onClearFilters = onClearFilters,
        )
    }
}

@Composable
private fun LikedHeader(
    state: LikedTracksUiState,
    onBack: () -> Unit,
    onClearFilters: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text("Voltar")
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Curtidas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = when {
                    state.resolving -> "${state.resolvedCount}/${state.totalCount} carregadas"
                    state.hasActiveFilters ->
                        "${state.visibleTracks.size} de ${state.allTracks.size}"
                    else -> "${state.allTracks.size} músicas"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
        }
        Spacer(Modifier.weight(1f))
        if (state.hasActiveFilters) {
            TextButton(onClick = onClearFilters) {
                Text("Limpar")
            }
        } else {
            Spacer(Modifier.width(72.dp))
        }
    }
}

@Composable
private fun LikedSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        singleLine = true,
        placeholder = {
            Text(
                text = "Buscar título, artista, álbum ou estilo",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Limpar busca",
                    )
                }
            }
        },
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
        ),
    )
}

@Composable
private fun LikedSortRow(
    selected: LikedSort,
    onSortChange: (LikedSort) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        FilterSectionLabel("Ordenar")
        ChipRow {
            SortChip(
                label = "Recentes",
                selected = selected == LikedSort.Recent,
                onClick = { onSortChange(LikedSort.Recent) },
            )
            SortChip(
                label = "Título",
                selected = selected == LikedSort.Title,
                onClick = { onSortChange(LikedSort.Title) },
            )
            SortChip(
                label = "Artista",
                selected = selected == LikedSort.Artist,
                onClick = { onSortChange(LikedSort.Artist) },
            )
        }
    }
}

@Composable
private fun FilterSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun DerivaFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val border = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun SortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.onBackground
    } else {
        Color.Transparent
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .then(
                if (!selected) {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(18.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ActiveFiltersBar(
    state: LikedTracksUiState,
    onClearQuery: () -> Unit,
    onClearArtist: () -> Unit,
    onClearGenre: () -> Unit,
    onClearAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Ativos",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        )
        if (state.query.isNotBlank()) {
            ActiveFilterPill(
                text = "“${state.query.trim()}”",
                onClear = onClearQuery,
            )
        }
        state.selectedArtistName?.let { name ->
            ActiveFilterPill(text = name, onClear = onClearArtist)
        }
        state.selectedGenreLabel?.let { label ->
            ActiveFilterPill(text = label, onClear = onClearGenre)
        }
        Text(
            text = "Limpar tudo",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClearAll)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ActiveFilterPill(
    text: String,
    onClear: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClear)
            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Remover filtro",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(14.dp),
        )
    }
}

@Composable
private fun LikedListBody(
    state: LikedTracksUiState,
    onPlay: (Track) -> Unit,
    onClearFilters: () -> Unit,
) {
    when {
        state.loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        state.errorMessage != null && state.allTracks.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        state.allTracks.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Nenhuma música curtida ainda",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Text(
                        text = "Toque no coração no feed para salvar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        state.visibleTracks.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Nada encontrado",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    )
                    Text(
                        text = "Tente outro termo ou limpe os filtros",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    TextButton(onClick = onClearFilters) {
                        Text("Limpar filtros")
                    }
                }
            }
        }
        else -> {
            val playingId = state.playingTrackId
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = state.visibleTracks,
                    key = { it.id.value },
                    contentType = { "liked-row" },
                ) { track ->
                    LikedTrackRow(
                        track = track,
                        playing = track.id == playingId,
                        onClick = { onPlay(track) },
                    )
                }
                if (state.resolving) {
                    item(key = "resolving") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Carregando mais…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LikedTrackRow(
    track: Track,
    playing: Boolean,
    onClick: () -> Unit,
) {
    val artworkUrl = remember(track.id, track.artworkUrl, track.album.artworkUrl) {
        track.artworkUrl?.takeIf { it.isNotBlank() }
            ?: track.album.artworkUrl?.takeIf { it.isNotBlank() }
    }
    val genre = track.genres.firstOrNull()
        ?.takeIf { !it.value.equals("unknown", ignoreCase = true) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (playing) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (artworkUrl != null) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(
                    text = track.album.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                genre?.let {
                    Text(
                        text = LikedTracks.genreLabel(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        if (playing) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
fun LikedTracksEntryButton(
    onClick: () -> Unit,
    lightContent: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (lightContent) Color.White else MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "Curtidas",
            tint = tint.copy(alpha = 0.92f),
            modifier = Modifier.size(24.dp),
        )
    }
}
