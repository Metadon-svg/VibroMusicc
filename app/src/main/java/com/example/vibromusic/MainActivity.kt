package com.example.vibromusic

import android.Manifest
import android.net.Uri
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.items
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

// Темы YT Music
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
            var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
            val localQueue = remember { mutableStateListOf<Track>() }
            var isLoggedIn by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            // Разрешение на уведомления
            val pLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ _ -> }
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

            // Таймер караоке
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
                                    searchResults.toList(), isLoggedIn, { isLoggedIn = true },
                                    onTrackClick = { track: Track ->
                                        scope.launch(Dispatchers.IO) {
                                            val url = getStreamUrl(track.id)
                                            withContext(Dispatchers.Main) {
                                                if (url.isNotEmpty()) {
                                                    currentTrack = track.copy(streamUrl = url)
                                                    player.setMediaItem(MediaItem.fromUri(url))
                                                    player.prepare(); player.play()
                                                    fetchLyrics(track) { lrc: List<LrcLine> -> lyricsLines = lrc }
                                                }
                                            }
                                        }
                                    }
                                )
                                "library" -> LibraryScreen(localQueue.toList(), { uri: Uri? ->
                                    uri?.let { localQueue.add(Track(it.toString(), "Свой файл", "Локально", "")) }
                                }) { track: Track ->
                                    currentTrack = track; isPlaying = true
                                    player.setMediaItem(MediaItem.fromUri(track.id))
                                    player.prepare(); player.play()
                                }
                            }
                            Spacer(modifier = Modifier.height(130.dp))
                        }

                        // Мини-плеер
                        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                            if (currentTrack != null && !isFullScreen) {
                                MiniPlayer(currentTrack!!, isPlaying, 
                                    onToggle = { if (isPlaying) player.pause() else player.play() },
                                    onClick = { isFullScreen = true }
                                )
                            }
                            BottomNav(currentScreen) { route: String -> currentScreen = route }
                        }

                        // Полноэкранный плеер
                        AnimatedVisibility(isFullScreen, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                            currentTrack?.let { track: Track ->
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

    private fun searchPiped(q: String): List<Track> {
        val url = "https://pipedapi.kavin.rocks/search?q=$q&filter=music_songs"
        return try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            val json = JsonParser.parseString(response.body?.string()).asJsonObject
            json.getAsJsonArray("items").map { 
                val o = it.asJsonObject
                Track(o.get("url").asString.split("=").last(), o.get("title").asString, o.get("uploaderName").asString, o.get("thumbnail").asString)
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

// --- UI ---

@Composable
fun HomeScreen(q: String, onQ: (String) -> Unit, onSearch: () -> Unit, results: List<Track>, logged: Boolean, onLog: () -> Unit, onTrack: (Track) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(modifier = Modifier.height(50.dp))
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayCircle, null, tint = YTRed, modifier = Modifier.size(32.dp))
            Text(" Music", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = onLog, colors = ButtonDefaults.buttonColors(containerColor = YTDark)) { Text(if(logged) "Metadon" else "Войти в YT", color = Color.White) }
        }
        TextField(
            value = q, onValueChange = onQ, modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(12.dp)),
            placeholder = { Text("Поиск в VibroMusic...") }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = TextFieldDefaults.colors(focusedContainerColor = YTDark, unfocusedContainerColor = YTDark, focusedTextColor = Color.White)
        )
        if (results.isEmpty()) {
            Text("Здравствуйте, Metadon!", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        } else {
            results.forEach { track: Track -> TrackItem(track, onTrack) }
        }
    }
}

@Composable
fun FullScreenPlayer(track: Track, playing: Boolean, lyrics: List<LrcLine>, time: Long, repeat: Int, onRepeat: () -> Unit, onClose: () -> Unit, onToggle: () -> Unit) {
    var tab by remember { mutableStateOf("текст") }
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(track.accentColor, YTBlack)))) {
        Column(Modifier.padding(24.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.padding(top = 20.dp)) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(35.dp)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("ТЕКСТ", Modifier.padding(16.dp).clickable { tab = "текст" }, color = if(tab=="текст") Color.White else YTGray, fontWeight = FontWeight.Bold)
                Text("ДАЛЕЕ", Modifier.padding(16.dp).clickable { tab = "далее" }, color = if(tab=="далее") Color.White else YTGray, fontWeight = FontWeight.Bold)
            }
            if (tab == "текст") LyricsView(lyrics, time)
            else {
                Spacer(Modifier.height(40.dp))
                AsyncImage(model = track.cover, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Text(track.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
                Text(track.artist, color = YTGray, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRepeat) { Icon(if(repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, null, tint = if(repeat != Player.REPEAT_MODE_OFF) VGreen else Color.White) }
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(45.dp))
                IconButton(onClick = onToggle, modifier = Modifier.size(80.dp).background(Color.White, CircleShape)) {
                    Icon(if(playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(40.dp))
                }
                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(45.dp))
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
        itemsIndexed(items = lines) { i: Int, line: LrcLine ->
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(t.title, color = Color.White, fontSize = 14.sp, maxLines = 1); Text(t.artist, color = YTGray, fontSize = 12.sp)
            }
            IconButton(onClick = onToggle) { Icon(if(p) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
        }
    }
}

@Composable
fun BottomNav(curr: String, onNav: (String) -> Unit) {
    NavigationBar(containerColor = YTBlack, modifier = Modifier.height(64.dp)) {
        NavigationBarItem(selected = curr == "home", onClick = { onNav("home") }, icon = { Icon(Icons.Default.Home, null, tint = Color.White) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent))
        NavigationBarItem(selected = curr == "library", onClick = { onNav("library") }, icon = { Icon(Icons.Default.LibraryMusic, null, tint = Color.White) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent))
    }
}

@Composable fun LibraryScreen(music: List<Track>, onPick: (Uri?) -> Unit, onTrack: (Track) -> Unit) {
    val p = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> onPick(uri) }
    Column(Modifier.padding(16.dp)) {
        Spacer(Modifier.height(50.dp))
        Text("Медиатека", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Button(onClick = { p.launch("audio/*") }, modifier = Modifier.padding(vertical = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = YTDark)) { Text("Импорт") }
        LazyColumn { items(items = music) { track: Track -> TrackItem(track, onTrack) } }
    }
}
