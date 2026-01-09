package com.example.vibromusic

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException

// Темы оформления
val YTBlack = Color(0xFF000000)
val YTRed = Color(0xFFFF0000)
val YTGray = Color(0xFFB3B3B3)
val YTDarkGray = Color(0xFF121212)
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
            val localQueue = remember { mutableStateListOf<Track>() }
            var userName by remember { mutableStateOf("Metadon") }
            var isLoggedIn by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            // Google Sign-In Setup
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
            val googleSignInClient = GoogleSignIn.getClient(this, gso)
            val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.result
                    userName = account?.displayName ?: "User"
                    isLoggedIn = true
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка входа", Toast.LENGTH_SHORT).show()
                }
            }

            // Уведомления
            val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Таймер для караоке
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
                                        withContext(Dispatchers.Main) { searchResults.clear(); searchResults.addAll(res) }
                                    }},
                                    searchResults, isLoggedIn, userName,
                                    onLogin = { authLauncher.launch(googleSignInClient.signInIntent) },
                                    onTrackClick = { track ->
                                        playTrack(track) { 
                                            currentTrack = it; isPlaying = true
                                            fetchLyrics(it) { lrc -> lyricsLines = lrc }
                                        }
                                    }
                                )
                                "library" -> LibraryScreen(localQueue) { track ->
                                    currentTrack = track; isPlaying = true
                                }
                            }
                        }

                        // ПЛЕЕРЫ
                        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                            if (currentTrack != null && !isFullScreen) {
                                MiniPlayer(currentTrack!!, isPlaying, 
                                    onToggle = { if(player.isPlaying) player.pause() else player.play(); isPlaying = player.isPlaying },
                                    onClick = { isFullScreen = true }
                                )
                            }
                            BottomNav(currentScreen) { currentScreen = it }
                        }

                        // ПОЛНОЭКРАННЫЙ ПЛЕЕР
                        AnimatedVisibility(isFullScreen, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                            currentTrack?.let { track ->
                                FullScreenPlayer(
                                    track = track, isPlaying = isPlaying,
                                    lyricsLines = lyricsLines, currentTime = currentTime,
                                    repeatMode = repeatMode,
                                    onRepeatClick = {
                                        repeatMode = when(repeatMode) {
                                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                                            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                                            else -> Player.REPEAT_MODE_OFF
                                        }
                                        player.repeatMode = repeatMode
                                    },
                                    onClose = { isFullScreen = false },
                                    onToggle = { if(player.isPlaying) player.pause() else player.play(); isPlaying = player.isPlaying }
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

    private fun playTrack(track: Track, onReady: (Track) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = client.newCall(Request.Builder().url("https://pipedapi.kavin.rocks/streams/${track.id}").build()).execute()
                val streamUrl = JsonParser.parseString(resp.body?.string()).asJsonObject.getAsJsonArray("audioStreams")[0].asJsonObject.get("url").asString
                withContext(Dispatchers.Main) {
                    player.setMediaItem(MediaItem.fromUri(streamUrl))
                    player.prepare(); player.play()
                    onReady(track.copy(streamUrl = streamUrl))
                }
            } catch(e: Exception) {}
        }
    }

    private fun fetchLyrics(track: Track, onResult: (List<LrcLine>) -> Unit) {
        val url = "https://lrclib.net/api/get?artist_name=${track.artist}&track_name=${track.title}"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                val lrc = JsonParser.parseString(body).asJsonObject.get("syncedLyrics")?.asString ?: ""
                onResult(parseLrc(lrc))
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

// --- КОМПОНЕНТЫ UI ---

@Composable
fun HomeScreen(q: String, onQ: (String) -> Unit, onSearch: () -> Unit, results: List<Track>, isLogged: Boolean, user: String, onLogin: () -> Unit, onTrackClick: (Track) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(modifier = Modifier.height(50.dp))
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayCircle, null, tint = YTRed, modifier = Modifier.size(32.dp))
            Text(" Music", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if(!isLogged) Button(onClick = onLogin, colors = ButtonDefaults.buttonColors(YTDarkGray)) { Text("Войти в YT") }
            else Text(user, color = VGreen, fontWeight = FontWeight.Bold)
        }

        TextField(
            value = q, onValueChange = onQ, 
            modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(12.dp)),
            placeholder = { Text("Поиск в VibroMusic...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = TextFieldDefaults.colors(focusedContainerColor = YTDarkGray, unfocusedContainerColor = YTDarkGray, focusedTextColor = Color.White)
        )

        if (results.isEmpty()) {
            CategoryChips()
            Text("Здравствуйте, $user!", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            PlaylistCarousel("Рекомендации для вас")
            ArtistCarousel("Популярные артисты")
        } else {
            results.forEach { TrackItem(it, onTrackClick) }
        }
        Spacer(modifier = Modifier.height(150.dp))
    }
}

@Composable
fun FullScreenPlayer(track: Track, isPlaying: Boolean, lyricsLines: List<LrcLine>, currentTime: Long, repeatMode: Int, onRepeatClick: () -> Unit, onClose: () -> Unit, onToggle: () -> Unit) {
    var tab by remember { mutableStateOf("текст") }
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(track.accentColor, YTBlack)))) {
        Column(modifier = Modifier.padding(24.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.padding(top = 20.dp)) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White) }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TabItem("ДАЛЕЕ", tab == "далее") { tab = "далее" }
                TabItem("ТЕКСТ", tab == "текст") { tab = "текст" }
                TabItem("ПОХОЖИЕ", tab == "похожие") { tab = "похожие" }
            }

            if (tab == "текст") {
                LyricsKaraokeView(lyricsLines, currentTime)
            } else {
                Spacer(modifier = Modifier.height(40.dp))
                AsyncImage(model = track.cover, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Text(track.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
                Text(track.artist, color = YTGray, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.weight(1f))
            
            // Управление
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRepeatClick) { 
                    Icon(if(repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, null, tint = if(repeatMode != Player.REPEAT_MODE_OFF) VGreen else Color.White) 
                }
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(45.dp))
                IconButton(onClick = onToggle, modifier = Modifier.size(80.dp).background(Color.White, CircleShape)) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(40.dp))
                }
                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(45.dp))
                Icon(Icons.Default.Shuffle, null, tint = Color.White)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun LyricsKaraokeView(lines: List<LrcLine>, currentTime: Long) {
    val state = rememberLazyListState()
    val index = lines.indexOfLast { it.time <= currentTime }
    LaunchedEffect(index) { if(index >= 0) state.animateScrollToItem(index) }

    LazyColumn(state = state, modifier = Modifier.fillMaxHeight(0.7f)) {
        itemsIndexed(lines) { i, line ->
            Text(
                line.text, 
                color = if(i == index) Color.White else Color.White.copy(0.3f), 
                fontSize = if(i == index) 30.sp else 22.sp, 
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp).animateContentSize()
            )
        }
    }
}

@Composable
fun TabItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, modifier = Modifier.padding(16.dp).clickable { onClick() }, color = if(selected) Color.White else YTGray, fontWeight = FontWeight.Bold)
}

@Composable
fun BottomNav(curr: String, onNav: (String) -> Unit) {
    NavigationBar(containerColor = YTBlack, modifier = Modifier.height(64.dp)) {
        val navItems = listOf("home" to Icons.Default.Home, "samples" to Icons.Default.SlowMotionVideo, "explore" to Icons.Default.Explore, "library" to Icons.Default.LibraryMusic)
        navItems.forEach { (route, icon) ->
            NavigationBarItem(
                selected = curr == route, 
                onClick = { onNav(route) }, 
                icon = { Icon(icon, null, tint = Color.White) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )
        }
    }
}

@Composable fun LibraryScreen(music: MutableList<Track>, onTrack: (Track) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { music.add(Track(it.toString(), "Локальный файл", "Импорт", "")) }
    }
    Column(modifier = Modifier.padding(16.dp)) {
        Spacer(modifier = Modifier.height(50.dp))
        Text("Медиатека", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Button(onClick = { picker.launch("audio/*") }, modifier = Modifier.padding(vertical = 16.dp)) { Text("Импорт своей музыки") }
        LazyColumn { items(music) { TrackItem(it, onTrack) } }
    }
}

@Composable fun TrackItem(t: Track, onClick: (Track) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick(t) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold); Text(t.artist, color = YTGray)
        }
    }
}

@Composable fun MiniPlayer(t: Track, isPlaying: Boolean, onToggle: () -> Unit, onClick: () -> Unit) {
    Surface(color = YTDarkGray, modifier = Modifier.fillMaxWidth().height(64.dp).clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            AsyncImage(model = t.cover, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(t.title, color = Color.White, fontSize = 14.sp, maxLines = 1); Text(t.artist, color = YTGray, fontSize = 12.sp)
            }
            IconButton(onClick = onToggle) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White) }
        }
    }
}

// Заглушки дизайна
@Composable fun CategoryChips() { LazyRow(Modifier.padding(horizontal = 16.dp)) { items(listOf("Релакс", "Энергия", "Спорт", "Вечеринка")) { Text(it, color = Color.White, modifier = Modifier.background(YTDarkGray, RoundedCornerShape(8.dp)).padding(8.dp)); Spacer(Modifier.width(8.dp)) } } }
@Composable fun PlaylistCarousel(t: String) { Text(t, color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(16.dp)); LazyRow { items(4) { Box(Modifier.size(150.dp).background(YTDarkGray).padding(8.dp)); Spacer(Modifier.width(12.dp)) } } }
@Composable fun ArtistCarousel(t: String) { Text(t, color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(16.dp)); LazyRow { items(4) { Box(Modifier.size(110.dp).clip(CircleShape).background(YTDarkGray)); Spacer(Modifier.width(16.dp)) } } }
