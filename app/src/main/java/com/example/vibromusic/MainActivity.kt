package com.example.vibromusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException

// Цветовая схема YouTube Music
val YTBlack = Color(0xFF000000)
val YTRed = Color(0xFFFF0000)
val YTGray = Color(0xFFB3B3B3)
val YTDarkGray = Color(0xFF212121)

data class Track(
    val videoId: String, 
    val title: String, 
    val artist: String, 
    val cover: String, 
    val accentColor: Color = Color(0xFF4E0D5A)
)

class MainActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null
    private val client = OkHttpClient()

    companion object { init { System.loadLibrary("vibromusic") } }
    external fun getNativeStatus(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        exoPlayer = ExoPlayer.Builder(this).build()

        setContent {
            var currentTrack by remember { mutableStateOf<Track?>(null) }
            var isPlaying by remember { mutableStateOf(false) }
            var isFullScreen by remember { mutableStateOf(false) }
            var searchQuery by remember { mutableStateOf("") }
            val searchResults = remember { mutableStateListOf<Track>() }
            val scope = rememberCoroutineScope()
            val nativeStatus = remember { getNativeStatus() }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = YTBlack) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Основной контент
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Spacer(modifier = Modifier.height(50.dp))
                            
                            // 1. Поиск (Верхняя панель)
                            SearchBarUI(searchQuery, onQueryChange = { searchQuery = it }, onSearch = {
                                scope.launch(Dispatchers.IO) {
                                    val results = searchPiped(searchQuery)
                                    withContext(Dispatchers.Main) {
                                        searchResults.clear()
                                        searchResults.addAll(results)
                                    }
                                }
                            })

                            if (searchResults.isEmpty()) {
                                // 2. Главная страница (если нет поиска)
                                CategoryChipsUI()
                                GreetingSectionUI("Metadon")
                                PlaylistCarouselUI("Рекомендации для вас")
                                ArtistCarouselUI("Похоже на: madk1d")
                            } else {
                                // 3. Результаты поиска
                                Text("Результаты поиска", color = Color.White, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                                searchResults.forEach { track ->
                                    TrackRowUI(track) {
                                        scope.launch(Dispatchers.IO) {
                                            val url = getStreamUrl(track.videoId)
                                            withContext(Dispatchers.Main) {
                                                currentTrack = track
                                                exoPlayer?.setMediaItem(MediaItem.fromUri(url))
                                                exoPlayer?.prepare()
                                                exoPlayer?.play()
                                                isPlaying = true
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(150.dp))
                        }

                        // Мини-плеер и Навигация
                        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                            if (currentTrack != null && !isFullScreen) {
                                MiniPlayerUI(currentTrack!!, isPlaying, 
                                    onToggle = { if(isPlaying) exoPlayer?.pause() else exoPlayer?.play(); isPlaying = !isPlaying },
                                    onClick = { isFullScreen = true })
                            }
                            if (!isFullScreen) BottomNavUI()
                        }

                        // Полноэкранный плеер (Анимация снизу вверх)
                        AnimatedVisibility(
                            visible = isFullScreen, 
                            enter = slideInVertically { it }, 
                            exit = slideOutVertically { it }
                        ) {
                            currentTrack?.let { track ->
                                FullScreenPlayerUI(track, isPlaying, onClose = { isFullScreen = false }, 
                                    onToggle = { if(isPlaying) exoPlayer?.pause() else exoPlayer?.play(); isPlaying = !isPlaying }) 
                            }
                        }
                    }
                }
            }
        }
    }

    // Сетевая логика поиска (Piped API)
    private fun searchPiped(query: String): List<Track> {
        val request = Request.Builder().url("https://pipedapi.kavin.rocks/search?q=$query&filter=music_songs").build()
        return try {
            val response = client.newCall(request).execute()
            val items = JsonParser.parseString(response.body?.string()).asJsonObject.getAsJsonArray("items")
            items.map { 
                val obj = it.asJsonObject
                Track(
                    videoId = obj.get("url").asString.split("=").last(),
                    title = obj.get("title").asString,
                    artist = obj.get("uploaderName").asString,
                    cover = obj.get("thumbnail").asString,
                    accentColor = Color(0xFF35154D) // Можно генерировать случайно
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    // Получение ссылки на поток
    private fun getStreamUrl(id: String): String {
        val request = Request.Builder().url("https://pipedapi.kavin.rocks/streams/$id").build()
        return try {
            val response = client.newCall(request).execute()
            JsonParser.parseString(response.body?.string()).asJsonObject.getAsJsonArray("audioStreams")[0].asJsonObject.get("url").asString
        } catch (e: Exception) { "" }
    }
}

// --- КОМПОНЕНТЫ ИНТЕРФЕЙСА ---

@Composable
fun SearchBarUI(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    TextField(
        value = query, onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)),
        placeholder = { Text("Введите название песни...", color = YTGray) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        singleLine = true,
        colors = TextFieldDefaults.colors(focusedContainerColor = YTDarkGray, unfocusedContainerColor = YTDarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
    )
}

@Composable
fun FullScreenPlayerUI(track: Track, isPlaying: Boolean, onClose: () -> Unit, onToggle: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(track.accentColor, YTBlack)))) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(35.dp)) }
                Text("ИГРАЕТ ИЗ ПОИСКА", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.MoreVert, null, tint = Color.White)
            }
            Spacer(modifier = Modifier.height(40.dp))
            AsyncImage(model = track.cover, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Spacer(modifier = Modifier.height(40.dp))
            Text(track.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(track.artist, color = YTGray, fontSize = 18.sp)
            
            Spacer(modifier = Modifier.height(40.dp))
            Slider(value = 0.3f, onValueChange = {}, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(45.dp))
                IconButton(onClick = onToggle, modifier = Modifier.size(80.dp).background(Color.White, CircleShape)) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(45.dp))
                }
                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(45.dp))
            }
        }
    }
}

@Composable
fun BottomNavUI() {
    NavigationBar(containerColor = YTBlack, modifier = Modifier.height(64.dp)) {
        val icons = listOf(Icons.Default.Home, Icons.Default.SlowMotionVideo, Icons.Default.Explore, Icons.Default.LibraryMusic, Icons.Default.PlayCircle)
        icons.forEach { icon ->
            NavigationBarItem(
                selected = icon == Icons.Default.Home, 
                onClick = {}, 
                icon = { Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) },
                label = null, // ТЕКСТ УБРАН ПО ТВОЕМУ ЗАПРОСУ
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )
        }
    }
}

@Composable
fun TrackRowUI(track: Track, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = track.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(track.artist, color = YTGray, fontSize = 14.sp)
        }
        Icon(Icons.Default.MoreVert, null, tint = Color.White)
    }
}

@Composable
fun MiniPlayerUI(track: Track, isPlaying: Boolean, onToggle: () -> Unit, onClick: () -> Unit) {
    Surface(color = YTDarkGray, modifier = Modifier.fillMaxWidth().height(64.dp).clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            AsyncImage(model = track.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                Text(track.artist, color = YTGray, fontSize = 12.sp)
            }
            IconButton(onClick = onToggle) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
        }
    }
}

@Composable fun GreetingSectionUI(name: String) {
    Text("Здравствуйте, $name!", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
}

@Composable fun CategoryChipsUI() {
    val items = listOf("Релакс", "Энергия", "Вечеринка", "Спорт")
    LazyRow(modifier = Modifier.padding(16.dp)) {
        items(items) { Text(it, color = Color.White, modifier = Modifier.background(Color.White.copy(0.1f), RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) ; Spacer(Modifier.width(8.dp)) }
    }
}

@Composable fun PlaylistCarouselUI(t: String) {
    Column {
        Text(t, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        LazyRow(modifier = Modifier.padding(horizontal = 16.dp)) {
            items(5) { Box(modifier = Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)).background(YTDarkGray)) ; Spacer(Modifier.width(12.dp)) }
        }
    }
}

@Composable fun ArtistCarouselUI(t: String) {
    Column {
        Text(t, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        LazyRow(modifier = Modifier.padding(horizontal = 16.dp)) {
            items(5) { Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(YTDarkGray)) ; Spacer(Modifier.width(16.dp)) }
        }
    }
}
