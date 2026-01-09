package com.example.vibromusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage

// Цвета в стиле YouTube Music
val YTBlack = Color(0xFF000000)
val YTSurface = Color(0xFF121212)
val YTRed = Color(0xFFFF0000)
val YTGray = Color(0xFFB3B3B3)

data class Track(val title: String, val artist: String, val cover: String, val url: String, val views: String = "")
data class Artist(val name: String, val subs: String, val image: String)

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

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = YTBlack) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(top = 40.dp, bottom = 120.dp)
                        ) {
                            TopBar()
                            CategoryChips()
                            
                            GreetingSection("Metadon")
                            
                            // Рекомендации (Вертикальный список)
                            RecommendationList { track ->
                                currentTrack = track
                                playMusic(track.url)
                                isPlaying = true
                            }

                            // Похоже на (Горизонтальная карусель артистов)
                            ArtistCarousel("Похоже на: madk1d")

                            // Плейлисты (Карусель карточек)
                            PlaylistCarousel("Плейлисты других пользователей")
                        }

                        // Мини-плеер внизу
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            Column {
                                if (currentTrack != null) {
                                    MiniPlayer(currentTrack!!, isPlaying) {
                                        if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                                        isPlaying = !isPlaying
                                    }
                                }
                                BottomNav()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun playMusic(url: String) {
        val mediaItem = MediaItem.fromUri(url)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.play()
    }
}

@Composable
fun TopBar() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = YTRed, modifier = Modifier.size(30.dp))
            Text(" Music", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Row {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.Gray), contentAlignment = Alignment.Center) {
                Text("M", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun CategoryChips() {
    val categories = listOf("Релакс", "Заряд энергии", "Вечеринка", "Тренировка")
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories) { cat ->
            Surface(
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.5f))
            ) {
                Text(cat, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun GreetingSection(name: String) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("РЕКОМЕНДАЦИИ", color = YTGray, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Здравствуйте, $name!", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Смотреть все", color = YTGray, fontSize = 12.sp, modifier = Modifier.border(1.dp, YTGray, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

@Composable
fun RecommendationList(onTrackClick: (Track) -> Unit) {
    val tracks = listOf(
        Track("Женщина, я не танцую", "Стас Костюшкин", "https://i.ytimg.com/vi/xFYQQPAOz78/mqdefault.jpg", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", "205 млн"),
        Track("Ай-яй-яй", "Руки Вверх!", "https://i.ytimg.com/vi/34Na4j8AVgA/mqdefault.jpg", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3", "4,9 млн")
    )
    Column {
        tracks.forEach { track ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onTrackClick(track) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(model = track.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${track.artist} • ${track.views} просмотров", color = YTGray, fontSize = 12.sp)
                }
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
fun ArtistCarousel(title: String) {
    val artists = listOf(
        Artist("fallen777angel", "18,3 тыс.", "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg"),
        Artist("паранойя", "6,26 тыс.", "https://i.ytimg.com/vi/4NRXx6U8ABQ/mqdefault.jpg")
    )
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(artists) { artist ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp)) {
                    AsyncImage(model = artist.image, contentDescription = null, modifier = Modifier.size(120.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    Text(artist.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text("${artist.subs} подписчиков", color = YTGray, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun PlaylistCarousel(title: String) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(3) {
                Column(modifier = Modifier.width(150.dp)) {
                    Box(modifier = Modifier.size(150.dp).clip(RoundedCornerShape(8.dp)).background(YTSurface)) {
                        Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.align(Alignment.Center).size(50.dp), tint = Color.DarkGray)
                    }
                    Text("Лучшие хиты", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text("Влад • 75 треков", color = YTGray, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(track: Track, isPlaying: Boolean, onToggle: () -> Unit) {
    Surface(color = Color(0xFF212121), modifier = Modifier.fillMaxWidth().height(64.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            AsyncImage(model = track.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                Text(track.artist, color = YTGray, fontSize = 12.sp)
            }
            Icon(Icons.Default.Cast, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(onClick = onToggle) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun BottomNav() {
    NavigationBar(containerColor = YTBlack, modifier = Modifier.height(60.dp)) {
        val items = listOf("Главная" to Icons.Default.Home, "Семплы" to Icons.Default.SlowMotionVideo, "Навигатор" to Icons.Default.Explore, "Библиотека" to Icons.Default.LibraryMusic)
        items.forEach { (label, icon) ->
            NavigationBarItem(
                selected = label == "Главная",
                onClick = {},
                icon = { Icon(icon, contentDescription = null, tint = Color.White) },
                label = { Text(label, color = Color.White, fontSize = 10.sp) }
            )
        }
    }
}
