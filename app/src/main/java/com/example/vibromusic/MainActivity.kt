package com.example.vibromusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage

// Цвета YT Music
val YTBlack = Color(0xFF000000)
val YTRed = Color(0xFFFF0000)
val YTGray = Color(0xFFB3B3B3)
val YTDarkGray = Color(0xFF212121)

data class Track(val title: String, val artist: String, val cover: String, val url: String, val accentColor: Color)

class MainActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null

    companion object {
        init { System.loadLibrary("vibromusic") }
    }
    external fun getNativeStatus(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        exoPlayer = ExoPlayer.Builder(this).build()

        setContent {
            var currentTrack by remember { mutableStateOf<Track?>(null) }
            var isPlaying by remember { mutableStateOf(false) }
            var isFullScreen by remember { mutableStateOf(false) }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = YTBlack) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Основной контент
                        MainContent(onTrackSelect = {
                            currentTrack = it
                            exoPlayer?.setMediaItem(MediaItem.fromUri(it.url))
                            exoPlayer?.prepare()
                            exoPlayer?.play()
                            isPlaying = true
                        })

                        // Мини-плеер и Навигация
                        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                            if (currentTrack != null && !isFullScreen) {
                                MiniPlayer(currentTrack!!, isPlaying, 
                                    onToggle = { 
                                        if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                                        isPlaying = !isPlaying
                                    },
                                    onClick = { isFullScreen = true }
                                )
                            }
                            if (!isFullScreen) BottomNav()
                        }

                        // Полноэкранный плеер
                        AnimatedVisibility(
                            visible = isFullScreen,
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it })
                        ) {
                            currentTrack?.let { track ->
                                FullScreenPlayer(
                                    track = track,
                                    isPlaying = isPlaying,
                                    onClose = { isFullScreen = false },
                                    onToggle = {
                                        if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                                        isPlaying = !isPlaying
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainContent(onTrackSelect: (Track) -> Unit) {
    val tracks = listOf(
        Track("Женщина, я не танцую", "Костюшкин Стас", "https://i.ytimg.com/vi/xFYQQPAOz78/mqdefault.jpg", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", Color(0xFF4E0D5A)),
        Track("Ай-яй-яй", "Руки Вверх!", "https://i.ytimg.com/vi/34Na4j8AVgA/mqdefault.jpg", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3", Color(0xFF0D3A5A))
    )

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 48.dp, bottom = 150.dp)) {
        // Тот же интерфейс, что был ранее (TopBar, Chips и т.д.)
        Text(" Music", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        
        Text("Здравствуйте, Metadon!", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        
        tracks.forEach { track ->
            TrackRow(track) { onTrackSelect(track) }
        }
    }
}

@Composable
fun FullScreenPlayer(track: Track, isPlaying: Boolean, onClose: () -> Unit, onToggle: () -> Unit) {
    // Динамический фон на основе акцентного цвета трека
    Box(modifier = Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(track.accentColor, YTBlack))
    )) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            // Top Bar плеера
            Row(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                Row(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(0.1f)).padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("Трек", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Видео", color = YTGray)
                }
                Icon(Icons.Default.MoreVert, null, tint = Color.White)
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Обложка
            AsyncImage(
                model = track.cover,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Название и артист
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(track.artist, color = YTGray, fontSize = 18.sp)
                }
                Icon(Icons.Default.Cast, null, tint = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Кнопки лайков и комментов
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PlayerActionButton(Icons.Outlined.ThumbUp, "191 тыс.")
                PlayerActionButton(Icons.Outlined.ThumbDown, "")
                PlayerActionButton(Icons.Outlined.Comment, "827")
                PlayerActionButton(Icons.Default.PlaylistAdd, "Сохранить")
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Прогресс бар
            Slider(value = 0.2f, onValueChange = {}, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0:20", color = YTGray, fontSize = 12.sp)
                Text("3:23", color = YTGray, fontSize = 12.sp)
            }

            // Управление воспроизведением
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shuffle, null, tint = Color.White)
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(40.dp))
                IconButton(onClick = onToggle, modifier = Modifier.size(80.dp).background(Color.White, CircleShape)) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(40.dp))
                }
                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(40.dp))
                Icon(Icons.Default.Repeat, null, tint = Color.White)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Нижние табы
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceAround) {
                Text("ДАЛЕЕ", color = YTGray, fontWeight = FontWeight.Bold)
                Text("ТЕКСТ", color = YTGray, fontWeight = FontWeight.Bold)
                Text("ПОХОЖИЕ", color = YTGray, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PlayerActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.background(Color.White.copy(0.05f), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            if (text.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(text, color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun TrackRow(track: Track, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = track.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(track.title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(track.artist, color = YTGray, fontSize = 14.sp)
        }
        Icon(Icons.Default.MoreVert, null, tint = Color.White)
    }
}

@Composable
fun MiniPlayer(track: Track, isPlaying: Boolean, onToggle: () -> Unit, onClick: () -> Unit) {
    Surface(color = YTDarkGray, modifier = Modifier.fillMaxWidth().height(64.dp).clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            AsyncImage(model = track.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                Text(track.artist, color = YTGray, fontSize = 12.sp)
            }
            IconButton(onClick = onToggle) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun BottomNav() {
    NavigationBar(containerColor = YTBlack, modifier = Modifier.height(70.dp)) {
        val items = listOf("Главная" to Icons.Default.Home, "Семплы" to Icons.Default.SlowMotionVideo, "Навигатор" to Icons.Default.Explore, "Библиотека" to Icons.Default.LibraryMusic, "Подписка" to Icons.Default.PlayCircle)
        items.forEach { (label, icon) ->
            NavigationBarItem(
                selected = label == "Главная",
                onClick = {},
                icon = { Icon(icon, null, tint = if (label == "Главная") Color.White else YTGray) },
                label = { Text(label, color = if (label == "Главная") Color.White else YTGray, fontSize = 9.sp) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )
        }
    }
}
