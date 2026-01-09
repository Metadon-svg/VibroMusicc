package com.example.vibromusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

val VBlack = Color(0xFF121212)
val VGreen = Color(0xFF1DB954)
val VGray = Color(0xFF282828)

data class Song(val title: String, val artist: String, val cover: String, val url: String)

class MainActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null

    // Загрузка C++ библиотеки
    companion object {
        init { System.loadLibrary("vibromusic") }
    }
    external fun getNativeStatus(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exoPlayer = ExoPlayer.Builder(this).build()

        setContent {
            var currentSong by remember { mutableStateOf<Song?>(null) }
            var isPlaying by remember { mutableStateOf(false) }
            val nativeStatus = remember { getNativeStatus() }

            val playlist = listOf(
                Song("Lose Yourself", "Eminem", "https://i.ytimg.com/vi/xFYQQPAOz78/mqdefault.jpg", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
                Song("Starboy", "The Weeknd", "https://i.ytimg.com/vi/34Na4j8AVgA/mqdefault.jpg", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3")
            )

            MaterialTheme {
                Scaffold(
                    containerColor = VBlack,
                    bottomBar = {
                        currentSong?.let { song ->
                            MiniPlayer(song, isPlaying) {
                                if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                                isPlaying = !isPlaying
                            }
                        }
                    }
                ) { padding ->
                    Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text("VibroMusic", color = VGreen, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                        
                        // Показываем статус из C++
                        Text(nativeStatus, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))

                        Spacer(modifier = Modifier.height(20.dp))
                        
                        playlist.forEach { song ->
                            SongItem(song) {
                                currentSong = song
                                val mediaItem = MediaItem.fromUri(song.url)
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

@Composable
fun SongItem(song: Song, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = song.cover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(song.artist, color = Color.Gray, fontSize = 14.sp)
        }
    }
}

@Composable
fun MiniPlayer(song: Song, isPlaying: Boolean, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(70.dp).background(VGray).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = song.cover, contentDescription = null, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)))
        Text(song.title, color = Color.White, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1)
        IconButton(onClick = onToggle) {
            Icon(if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
    }
}
