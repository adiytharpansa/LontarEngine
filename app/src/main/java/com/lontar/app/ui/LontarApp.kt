package com.lontar.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.bunny.core.model.VideoDetails
import com.bunny.core.model.VideoItem
import com.lontar.app.player.PlayerActivity
import java.text.NumberFormat
import java.util.Locale

private val LontarColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF9DE2D1),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF07382F),
    secondary = androidx.compose.ui.graphics.Color(0xFFAFCFC8),
    background = androidx.compose.ui.graphics.Color(0xFF101413),
    surface = androidx.compose.ui.graphics.Color(0xFF191F1D),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF29312E),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE5ECE9),
)

@Composable
fun LontarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LontarColors, content = content)
}

@Composable
fun LontarApp() {
    val navController = rememberNavController()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                HomeRoute { video ->
                    navController.navigate("details/${Uri.encode(video.id)}")
                }
            }
            composable(
                route = "details/{videoId}",
                arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
            ) { entry ->
                val videoId = entry.arguments?.getString("videoId").orEmpty()
                VideoDetailsRoute(
                    videoId = videoId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun HomeRoute(onVideoClick: (VideoItem) -> Unit) {
    val viewModel: HomeViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "intro") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Lontar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Temukan video untuk ditonton", color = MaterialTheme.colorScheme.secondary)
            }
        }
        item(key = "search") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Cari video") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
                )
                Button(onClick = { viewModel.search(query) }) {
                    Text("Cari")
                }
            }
        }
        if (state.isLoading) {
            item(key = "loading") {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.errorMessage != null) {
            item(key = "error") {
                ErrorPanel(message = state.errorMessage!!, onRetry = viewModel::retry)
            }
        } else if (state.items.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = if (state.query.isBlank()) "Belum ada video untuk ditampilkan." else "Tidak ada hasil untuk pencarian ini.",
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        } else {
            item(key = "section-title") {
                Text(
                    text = if (state.query.isBlank()) "Pilihan video" else "Hasil pencarian",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(state.items, key = { it.id }) { video ->
                VideoCard(video = video, onClick = { onVideoClick(video) })
            }
        }
    }
}

@Composable
private fun VideoCard(video: VideoItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column {
            Thumbnail(url = video.thumbnailUrl, title = video.title, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Spacer(modifier = Modifier.height(5.dp))
                Text(video.channelName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                val metadata = listOfNotNull(
                    video.viewCount?.let(::formatCount),
                    video.uploadedText?.takeIf(String::isNotBlank),
                    video.durationSeconds?.let(::formatDuration),
                ).joinToString(" · ")
                if (metadata.isNotEmpty()) {
                    Text(metadata, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun VideoDetailsRoute(videoId: String, onBack: () -> Unit) {
    val viewModel: VideoDetailsViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(videoId) {
        viewModel.load(videoId)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("‹  Kembali") }
        when {
            state.isLoading -> Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.errorMessage != null -> ErrorPanel(
                message = state.errorMessage!!,
                onRetry = { viewModel.load(videoId, force = true) },
            )
            state.details != null -> {
                val details = state.details!!
                VideoDetailsContent(details = details) {
                    context.startActivity(
                        Intent(context, PlayerActivity::class.java).apply {
                            putExtra(PlayerActivity.EXTRA_VIDEO_ID, details.id)
                            putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, details.title)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoDetailsContent(details: VideoDetails, onPlay: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Thumbnail(
            url = details.thumbnailUrl,
            title = details.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        Text(details.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(details.channelName, color = MaterialTheme.colorScheme.secondary)
        val metadata = listOfNotNull(
            details.viewCount?.let(::formatCount),
            details.uploadedText?.takeIf(String::isNotBlank),
        ).joinToString(" · ")
        if (metadata.isNotEmpty()) Text(metadata, style = MaterialTheme.typography.bodySmall)
        Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
            Text("Putar video")
        }
        Text("Tentang video", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(details.description.ifBlank { "Tidak ada deskripsi." }, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), lineHeight = 22.sp)
    }
}

@Composable
private fun Thumbnail(url: String?, title: String, modifier: Modifier = Modifier) {
    if (url.isNullOrBlank()) {
        Box(
            modifier = modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("Pratinjau tidak tersedia", color = MaterialTheme.colorScheme.secondary)
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = "Thumbnail: $title",
            modifier = modifier.clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.secondary)
        Button(onClick = onRetry) { Text("Coba lagi") }
    }
}

private fun formatCount(value: Long): String =
    "${NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)} tayangan"

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes >= 60) {
        String.format(Locale.ROOT, "%d:%02d:%02d", minutes / 60, minutes % 60, remainingSeconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, remainingSeconds)
    }
}
