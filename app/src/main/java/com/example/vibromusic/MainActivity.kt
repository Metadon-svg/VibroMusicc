package com.example.vibromusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

data class Song(val id: String, val title: String, val artist: String, val cover: String, val streamUrl: String)

class MainActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exoPlayer = ExoPlayer.Builder(this).build()

        setContent {
            var currentSong by remember { mutableStateOf<Song?>(null) }
            var isPlaying by remember { mutableStateOf(false) }

            val playlist = listOf(
                Song("1", "Lose Yourself", "Eminem", "https://i1.sndcdn.com/artworks-000030536712-l9f5h6-t500x500.jpg", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
                Song("2", "Starboy", "The Weeknd", "https://upload.wikimedia.org/wikipedia/ru/3/39/The_Weeknd_-_Starboy.png", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3")
            )

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
                    
                    playlist.forEach { song ->
                        SongRow(song) {
                            currentSong = song
                            playStream(song.streamUrl)
                            isPlaying = true
                        }
                    }
                }
            }
        }
    }

    private fun playStream(url: String) {
        val mediaItem = MediaItem.fromUri(url)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.play()
    }
}

@Composable
fun SongRow(song: Song, onClick: () -> Unit) {
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