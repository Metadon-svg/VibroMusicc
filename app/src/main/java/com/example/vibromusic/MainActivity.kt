package com.example.vibromusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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

// Цвета как в SimpMusic (Spotify Style)
val VBlack = Color(0xFF121212)
val VGreen = Color(0xFF1DB954)
val VGray = Color(0xFF1E1E1E)

data class Track(val id: String, val title: String, val artist: String, val cover: String, val streamUrl: String)

class MainActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null

    // C++ Мост (теперь он заработает, когда ты создашь файлы из Части 1)
    companion object {
        init { System.loadLibrary("vibromusic") }
    }
    external fun getNativeStatus(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Включаем прозрачные бары (как в SimpMusic)
        enableEdgeToEdge()
        
        exoPlayer = ExoPlayer.Builder(this).build()

        setContent {
            var currentTrack by remember { mutableStateOf<Track?>(null) }
            var isPlaying by remember { mutableStateOf(false) }
            val nativeStatus = remember { getNativeStatus() }

            // Имитация списка из Piped API
            val tracks = listOf(
                Track("1", "Starboy", "The Weeknd", "https://i.ytimg.com/vi/34Na4j8AVgA/mqdefault.jpg", "https://pipedproxy.kavin.rocks/videoplayback?id=34Na4j8AVgA&itag=140"),
                Track("2", "Blinding Lights", "The Weeknd", "https://i.ytimg.com/vi/4NRXx6U8ABQ/mqdefault.jpg", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3")
            )

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = VBlack) {
                    Scaffold(
                        containerColor = Color.Transparent,
                        bottomBar = {
                            if (currentTrack != null) {
                                MiniPlayer(currentTrack!!, isPlaying) {
                                    if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                                    isPlaying = !isPlaying
                                }
                            }
                        }
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                "VibroMusic",
                                modifier = Modifier.padding(16.dp),
                                color = VGreen,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Text(
                                "Status: $nativeStatus",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = Color.Gray,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            tracks.forEach { track ->
                                TrackRow(track) {
                                    currentTrack = track
                                    val mediaItem = MediaItem.fromUri(track.streamUrl)
                                    exoPlayer?.setMediaItem(mediaItem)
                                    exoPlayer?.prepare()
                                    exoPlayer?.play()
                                    isPlaying = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackRow(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.cover,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(track.title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(track.artist, color = Color.Gray, fontSize = 14.sp)
        }
    }
}

@Composable
fun MiniPlayer(track: Track, isPlaying: Boolean, onToggle: () -> Unit) {
    Surface(
        color = VGray,
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(model = track.cover, contentDescription = null, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                Text(track.artist, color = VGreen, fontSize = 12.sp)
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}
