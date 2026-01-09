package com.example.vibromusic

import android.Manifest
import android.os.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import coil.compose.AsyncImage
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException

// Цветовая палитра YT Music
val YTBlack = Color(0xFF000000)
val YTDark = Color(0xFF121212)
val YTRed = Color(0xFFFF0000)
val YTGray = Color(0xFFB3B3B3)
val VGreen = Color(0xFF1DB954)

data class Track(
    val id: String, val title: String, val artist: String, val cover: String,
    val streamUrl: String = "", val accentColor: Color = Color(0xFF35154D)
)
data class LrcLine(val time: Long, val text: String)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private val client = OkHttpClient()

    companion object { init { System.loadLibrary("vibromusic") } }
    external fun getNativeStatus(): String

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
            var searchQuery by remember { mutableStateOf("") }
            val searchResults = remember { mutableStateListOf<Track>() }
            var lyricsLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
            var currentTime by remember { mutableStateOf(0L) }
            var repeatMode by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
            val localList = remember { mutableStateListOf<Track>() }
            var isLoggedIn by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            // Разрешения
            val pLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Плеер стейт
            DisposableEffect(Unit) {
                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
                }
                player.addListener(listener)
                onDispose { player.removeListener(listener) }
            }

            // Караоке таймер
            LaunchedEffect(isPlaying) {
                while (isPlaying) {
                    currentTime = player.currentPosition
                    delay(300)
                }
            }

            MaterialTheme {
                Surface(color = YTBlack, modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            when (currentScreen) {
                                "home" -> HomeScreen(
                                    searchQuery, { searchQuery = it },
                                    onSearch = { scope.launch(Dispatchers.IO) {
                                        val res = searchPiped(searchQuery)
                                        withContext(Dispatchers.Main) {
                                            searchResults.clear()
                                            searchResults.addAll(res)
                                        }
                                    }},
                                    searchResults, isLoggedIn, { isLoggedIn = true },
                                    onTrackClick = { track ->
                                        // ЭТАП 2: Получаем поток при клике (Extract Flat logic)
                                        scope.launch(Dispatchers.IO) {
                                            val streamUrl = getStreamUrl(track.id)
                                            withContext(Dispatchers.Main) {
                                                if (streamUrl.isNotEmpty()) {
                                                    currentTrack = track.copy(streamUrl = streamUrl)
                                                    player.setMediaItem(MediaItem.fromUri(streamUrl))
                                                    player.prepare(); player.play()
                                                    fetchLyrics(track) { lyricsLines = it }
                                                }
                                            }
                                        }
                                    }
                                )
                                "library" -> LibraryScreen(localList) { track ->
                                    currentTrack = track; isPlaying = true
                                }
                            }
                            Spacer(modifier = Modifier.height(130.dp))
                        }

                        // Мини-плеер и Навигация
                        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                            if (currentTrack != null && !isFullScreen) {
                                MiniPlayer(currentTrack!!, isPlaying, 
                                    { if (isPlaying) player.pause() else player.play() },
                                    { isFullScreen = true }
                                )
                            }
                            BottomNav(currentScreen) { currentScreen = it }
                        }

                        // Полноэкранный плеер
                        AnimatedVisibility(isFullScreen, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                            currentTrack?.let { track ->
                                FullScreenPlayer(
                                    track, isPlaying, lyricsLines, currentTime, repeatMode,
                                    onRepeat = {
                                        repeatMode = when(repeatMode) {
                                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                                            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                                            else -> Player.REPEAT_MODE_OFF
                                        }
                                        player.repeatMode = repeatMode
                                    },
                                    onClose = { isFullScreen = false },
                                    onToggle = { if (isPlaying) player.pause() else player.play() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ЭТАП 1: Поиск только метаданных
    private fun searchPiped(q: String): List<Track> {
        val url = "https://pipedapi.kavin.rocks/search?q=$q&filter=music_songs"
        return try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            val json = JsonParser.parseString(response.body?.string()).asJsonObject
            json.getAsJsonArray("items").map { 
                val o = it.asJsonObject
                Track(
                    id = o.get("url").asString.split("=").last(),
                    title = o.get("title").asString,
                    artist = o.get("uploaderName").asString,
                    cover = o.get("thumbnail").asString
                )
            }
        } catch(e: Exception) { emptyList() }
    }

    private fun getStreamUrl(id: String): String {
        val url = "https://pipedapi.kavin.rocks/streams/$id"
        return try {
            val resp = client.newCall(Request.Builder().url(url).build()).execute()
            JsonParser.parseString(resp.body?.string()).asJsonObject.getAsJsonArray("audioStreams")[0].asJsonObject.get("url").asString
        } catch(e: Exception) { "" }
    }

    private fun fetchLyrics(track: Track, onRes: (List<LrcLine>) -> Unit) {
        val url = "https://lrclib.net/api/get?artist_name=${track.artist}&track_name=${track.title}"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                val lrc = JsonParser.parseString(body).asJsonObject.get("syncedLyrics")?.asString ?: ""
                onRes(parseLrc(lrc))
            }
            override fun onFailure(call: Call, e: IOException) {}
        })
    }

    private fun parseLrc(lrc: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        val regex = Regex("\\[(\\d+):(\\d+\\.\\d+)\\](.*)")
        lrc.lines().forEach { line ->
            val m = regex.find(line)
            if (m != null) {
                val ms = m.groupValues[1].toLong() * 60000 + (m.groupValues[2].toFloat() * 1000).toLong()
                lines.add(LrcLine(ms, m.groupValues[3]))
            }
        }
        return lines
    }
}

// --- UI COMPONENTS ---

@Composable
fun HomeScreen(q: String, onQ: (String) -> Unit, onSearch: () -> Unit, results: List<Track>, logged: Boolean, onLog: () -> Unit, onTrack: (Track) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(modifier = Modifier.height(50.dp))
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayCircle, null, tint = YTRed, modifier = Modifier.size(32.dp))
            Text(" Music", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = onLog, colors = ButtonDefaults.buttonColors(YTDark)) { 
                Text(if(logged) "Metadon" else "Войти в YT", color = Color.White) 
            }
        }

        TextField(
            value = q, onValueChange = onQ, 
            modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(12.dp)),
            placeholder = { Text("Поиск в VibroMusic...") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = TextFieldDefaults.colors(focusedContainerColor = YTDark, unfocusedContainerColor = YTDark, focusedTextColor = Color.White)
        )

        if (results.isEmpty()) {
            LazyRow(Modifier.padding(horizontal = 16.dp)) {
                items(listOf("Релакс", "Энергия", "Спорт", "Вечеринка")) { 
                    Text(it, color = Color.White, modifier = Modifier.background(YTDark, RoundedCornerShape(8.dp)).padding(8.dp))
                    Spacer(Modifier.width(8.dp))
                }
            }
            Text("Здравствуйте, Metadon!", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            RecommendationSection("Рекомендации")
            RecommendationSection("Популярные артисты", isCircle = true)
        } else {
            results.forEach { TrackItem(it, onTrack) }
        }
    }
}

@Composable
fun FullScreenPlayer(track: Track, playing: Boolean, lyrics: List<LrcLine>, time: Long, repeat: Int, onRepeat: () -> Unit, onClose: () -> Unit, onToggle: () -> Unit) {
    var tab by remember { mutableStateOf("текст") }
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(track.accentColor, YTBlack)))) {
        Column(Modifier.padding(24.dp)) {
            IconButton(onClick = onClose, Modifier.padding(top = 20.dp)) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("ДАЛЕЕ", Modifier.padding(16.dp).clickable { tab = "далее" }, color = if(tab=="далее") Color.White else YTGray)
                Text("ТЕКСТ", Modifier.padding(16.dp).clickable { tab = "текст" }, color = if(tab=="текст") Color.White else YTGray)
            }

            if (tab == "текст") {
                LyricsView(lyrics, time)
            } else {
                Spacer(Modifier.height(40.dp))
                AsyncImage(model = track.cover, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Text(track.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
                Text(track.artist, color = YTGray, fontSize = 18.sp)
            }

            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRepeat) { 
                    Icon(if(repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, null, tint = if(repeat != Player.REPEAT_MODE_OFF) VGreen else Color.White) 
                }
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, Modifier.size(40.dp))
                IconButton(onClick = onToggle, Modifier.size(80.dp).background(Color.White, CircleShape)) {
                    Icon(if(playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, Modifier.size(40.dp))
                }
                Icon(Icons.Default.SkipNext, null, tint = Color.White, Modifier.size(40.dp))
                Icon(Icons.Default.Shuffle, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun LyricsView(lines: List<LrcLine>, time: Long) {
    val state = rememberLazyListState()
    val index = lines.indexOfLast { it.time <= time }
    LaunchedEffect(index) { if(index >= 0) state.animateScrollToItem(index) }
    LazyColumn(state = state, modifier = Modifier.fillMaxHeight(0.7f)) {
        itemsIndexed(lines) { i, line ->
            Text(line.text, color = if(i == index) Color.White else Color.White.copy(0.3f), fontSize = if(i == index) 28.sp else 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
fun TrackItem(t: Track, onClick: (Track) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick(t) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)))
        Column(Modifier.padding(start = 12.dp)) {
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold); Text(t.artist, color = YTGray)
        }
    }
}

@Composable
fun MiniPlayer(t: Track, p: Boolean, onToggle: () -> Unit, onClick: () -> Unit) {
    Surface(color = YTDark, modifier = Modifier.fillMaxWidth().height(64.dp).clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(t.title, color = Color.White, fontSize = 14.sp, maxLines = 1); Text(t.artist, color = YTGray, fontSize = 12.sp)
            }
            IconButton(onClick = onToggle) { Icon(if(p) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White) }
        }
    }
}

@Composable
fun BottomNav(curr: String, onNav: (String) -> Unit) {
    NavigationBar(containerColor = YTBlack, modifier = Modifier.height(64.dp)) {
        val navItems = listOf("home" to Icons.Default.Home, "library" to Icons.Default.LibraryMusic)
        navItems.forEach { (route, icon) ->
            NavigationBarItem(selected = curr == route, onClick = { onNav(route) }, icon = { Icon(icon, null, tint = Color.White) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent))
        }
    }
}

@Composable fun LibraryScreen(music: MutableList<Track>, onTrack: (Track) -> Unit) {
    val p = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> 
        uri?.let { music.add(Track(it.toString(), "Свой файл", "Локально", "")) }
    }
    Column(Modifier.padding(16.dp)) {
        Spacer(Modifier.height(50.dp))
        Text("Медиатека", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Button(onClick = { p.launch("audio/*") }, Modifier.padding(vertical = 16.dp)) { Text("Импорт") }
        LazyColumn { items(music) { TrackItem(it, onTrack) } }
    }
}

@Composable fun RecommendationSection(title: String, isCircle: Boolean = false) {
    Column {
        Text(title, color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(16.dp))
        LazyRow(Modifier.padding(horizontal = 16.dp)) {
            items(5) { Box(Modifier.size(if(isCircle) 110.dp else 140.dp).clip(if(isCircle) CircleShape else RoundedCornerShape(8.dp)).background(YTDark)) ; Spacer(Modifier.width(12.dp)) }
        }
    }
}
