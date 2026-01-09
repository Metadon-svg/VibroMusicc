package com.example.vibromusic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

// Цвета
val YTBlack = Color(0xFF000000)
val YTGray = Color(0xFFB3B3B3)
val YTDarkGray = Color(0xFF1A1A1A)
val VGreen = Color(0xFF1DB954)

data class Track(
    val id: String, val title: String, val artist: String, val cover: String, 
    val url: String = "", val isLocal: Boolean = false, val accentColor: Color = Color(0xFF35154D)
)

data class LrcLine(val time: Long, val text: String)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()

        setContent {
            var currentTrack by remember { mutableStateOf<Track?>(null) }
            var isPlaying by remember { mutableStateOf(false) }
            var isFullScreen by remember { mutableStateOf(false) }
            var currentScreen by remember { mutableStateOf("home") }
            var lyricsLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
            var currentTime by remember { mutableStateOf(0L) }
            var repeatMode by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
            val queue = remember { mutableStateListOf<Track>() }
            var isLoggedIn by remember { mutableStateOf(false) }

            // Запрос разрешений на уведомления
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Трекер времени для караоке
            LaunchedEffect(isPlaying) {
                while (isPlaying) {
                    currentTime = player.currentPosition
                    delay(300)
                }
            }

            MaterialTheme {
                Surface(color = YTBlack) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            when (currentScreen) {
                                "home" -> HomeScreen(isLoggedIn, { isLoggedIn = true }, { track -> 
                                    playTrack(track, player) { currentTrack = it; isPlaying = true }
                                    fetchLyrics(track) { lyricsLines = it }
                                })
                                "library" -> LibraryScreen(queue) { track -> 
                                    playTrack(track, player) { currentTrack = it; isPlaying = true }
                                }
                            }
                            Spacer(modifier = Modifier.height(140.dp))
                        }

                        // Плееры
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            Column {
                                if (currentTrack != null && !isFullScreen) {
                                    MiniPlayer(currentTrack!!, isPlaying, 
                                        { player.playWhenReady = !player.playWhenReady; isPlaying = player.playWhenReady },
                                        { isFullScreen = true }
                                    )
                                }
                                BottomNav(currentScreen) { currentScreen = it }
                            }
                        }

                        // FullScreen Player
                        AnimatedVisibility(isFullScreen, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                            FullScreenPlayer(
                                track = currentTrack!!,
                                isPlaying = isPlaying,
                                lyricsLines = lyricsLines,
                                currentTime = currentTime,
                                repeatMode = repeatMode,
                                onRepeatClick = {
                                    repeatMode = when (repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                                        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                    player.repeatMode = repeatMode
                                },
                                onClose = { isFullScreen = false },
                                onToggle = { player.playWhenReady = !player.playWhenReady; isPlaying = player.playWhenReady }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun fetchLyrics(track: Track, onResult: (List<LrcLine>) -> Unit) {
        val url = "https://lrclib.net/api/get?artist_name=${track.artist}&track_name=${track.title}"
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                val lrc = JSONObject(body).optString("syncedLyrics", "")
                onResult(parseLrc(lrc))
            }
            override fun onFailure(call: Call, e: IOException) {}
        })
    }

    private fun parseLrc(lrc: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        val regex = Regex("\\[(\\d+):(\\d+\\.\\d+)\\](.*)")
        lrc.lines().forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = (match.groupValues[2].toFloat() * 1000).toLong()
                lines.add(LrcLine(min * 60000 + sec, match.groupValues[3]))
            }
        }
        return lines
    }
}

// --- ЭКРАНЫ ---

@Composable
fun HomeScreen(isLoggedIn: Boolean, onLogin: () -> Unit, onTrackClick: (Track) -> Unit) {
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Spacer(modifier = Modifier.height(40.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("VibroMusic", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (!isLoggedIn) {
                Button(onClick = onLogin, colors = ButtonDefaults.buttonColors(containerColor = YTDarkGray)) {
                    Text("Войти в YT")
                }
            } else {
                Icon(Icons.Default.AccountCircle, null, tint = VGreen, modifier = Modifier.size(35.dp))
            }
        }
        
        Text(if (isLoggedIn) "Ваши рекомендации" else "Тренды", color = YTGray, modifier = Modifier.padding(vertical = 16.dp))
        
        // Список (заглушка)
        val sample = Track("1", "Женщина, я не танцую", "Костюшкин Стас", "https://i.ytimg.com/vi/xFYQQPAOz78/mqdefault.jpg")
        repeat(5) {
            TrackItem(sample) { onTrackClick(sample) }
        }
    }
}

@Composable
fun LibraryScreen(queue: MutableList<Track>, onTrackClick: (Track) -> Unit) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { queue.add(Track(it.toString(), "Локальный файл", "Импорт", "", isLocal = true)) }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Spacer(modifier = Modifier.height(40.dp))
        Text("Медиатека", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            LibraryAction(Icons.Default.Favorite, "Лайки")
            Spacer(modifier = Modifier.width(8.dp))
            LibraryAction(Icons.Default.FileDownload, "Импорт") { filePicker.launch("audio/*") }
        }

        Text("Плейлисты", color = Color.White, fontSize = 20.sp)
        LazyColumn {
            items(queue) { track ->
                TrackItem(track) { onTrackClick(track) }
            }
        }
    }
}

@Composable
fun LibraryAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit = {}) {
    Column(modifier = Modifier.background(YTDarkGray, RoundedCornerShape(12.dp)).padding(16.dp).width(80.dp).clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = VGreen)
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

// --- ПОЛНОЭКРАННЫЙ ПЛЕЕР (КАРАОКЕ) ---

@Composable
fun FullScreenPlayer(
    track: Track, isPlaying: Boolean, lyricsLines: List<LrcLine>, 
    currentTime: Long, repeatMode: Int, onRepeatClick: () -> Unit,
    onClose: () -> Unit, onToggle: () -> Unit
) {
    var tab by remember { mutableStateOf("текст") }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(track.accentColor, YTBlack)))) {
        Column(modifier = Modifier.padding(24.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.padding(top = 20.dp)) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White) }
            
            // Табы
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("ДАЛЕЕ", modifier = Modifier.padding(16.dp).clickable { tab = "далее" }, color = if(tab=="далее") Color.White else YTGray)
                Text("ТЕКСТ", modifier = Modifier.padding(16.dp).clickable { tab = "текст" }, color = if(tab=="текст") Color.White else YTGray)
                Text("ПОХОЖИЕ", modifier = Modifier.padding(16.dp).clickable { tab = "похожие" }, color = if(tab=="похожие") Color.White else YTGray)
            }

            if (tab == "текст") {
                LyricsKaraoke(lyricsLines, currentTime)
            } else {
                // Дизайн обложки
                Spacer(modifier = Modifier.height(40.dp))
                AsyncImage(model = track.cover, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Spacer(modifier = Modifier.height(20.dp))
                Text(track.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(track.artist, color = YTGray, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.weight(1f))
            
            // Управление
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRepeatClick) { 
                    Icon(if(repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, null, tint = if(repeatMode != Player.REPEAT_MODE_OFF) VGreen else Color.White) 
                }
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(40.dp))
                IconButton(onClick = onToggle, modifier = Modifier.size(80.dp).background(Color.White, CircleShape)) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(40.dp))
                }
                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(40.dp))
                Icon(Icons.Default.Shuffle, null, tint = Color.White)
            }
            
            // Креативная фишка: Визуализатор (симуляция)
            Box(modifier = Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.Center) {
                repeat(10) { i ->
                    val height = if(isPlaying) (10..40).random().dp else 5.dp
                    Box(modifier = Modifier.padding(horizontal = 4.dp).width(4.dp).height(height).background(VGreen, CircleShape))
                }
            }
        }
    }
}

@Composable
fun LyricsKaraoke(lines: List<LrcLine>, currentTime: Long) {
    val listState = rememberLazyListState()
    val activeIndex = lines.indexOfLast { it.time <= currentTime }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxHeight(0.6f)) {
        itemsIndexed(lines) { index, line ->
            val isActive = index == activeIndex
            Text(
                text = line.text,
                color = if (isActive) Color.White else Color.White.copy(0.3f),
                fontSize = if (isActive) 28.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp).animateContentSize()
            )
        }
    }
}

// --- УТИЛИТЫ ---

fun playTrack(track: Track, player: ExoPlayer, onReady: (Track) -> Unit) {
    val mediaItem = MediaItem.fromUri(if(track.isLocal) track.id else "https://pipedapi.kavin.rocks/streams/${track.id}") // Placeholder stream
    player.setMediaItem(mediaItem)
    player.prepare()
    player.play()
    onReady(track)
}

@Composable
fun TrackItem(track: Track, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = track.cover, contentDescription = null, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)))
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(track.title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(track.artist, color = YTGray)
        }
    }
}

@Composable
fun MiniPlayer(track: Track, isPlaying: Boolean, onToggle: () -> Unit, onClick: () -> Unit) {
    Surface(color = YTDarkGray, modifier = Modifier.fillMaxWidth().height(64.dp).clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            AsyncImage(model = track.cover, contentDescription = null, modifier = Modifier.size(45.dp).clip(RoundedCornerShape(4.dp)))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                Text(track.artist, color = YTGray, fontSize = 12.sp)
            }
            IconButton(onClick = onToggle) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White) }
        }
    }
}

@Composable
fun BottomNav(current: String, onNav: (String) -> Unit) {
    NavigationBar(containerColor = YTBlack) {
        NavigationBarItem(selected = current=="home", onClick = { onNav("home") }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Главная") })
        NavigationBarItem(selected = current=="library", onClick = { onNav("library") }, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Медиатека") })
    }
}
