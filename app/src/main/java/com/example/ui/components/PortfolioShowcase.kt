package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

data class BeforeAfterItem(
    val title: String,
    val beforeDesc: String,
    val afterDesc: String
)

data class VideoShowcaseItem(
    val title: String,
    val duration: String,
    val views: String
)

@Composable
fun BeforeAfterSection(beforeAfterItems: List<BeforeAfterItem>) {
    if (beforeAfterItems.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CompareArrows,
                contentDescription = null,
                tint = TealPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Öncesi / Sonrası (Uygulama Referansları)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        beforeAfterItems.forEach { item ->
            BeforeAfterCard(item = item)
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun BeforeAfterCard(item: BeforeAfterItem) {
    var showAfter by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Toggle Button (Önce vs Sonra)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                ) {
                    Surface(
                        color = if (!showAfter) Slate700 else Color.Transparent,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showAfter = false }
                    ) {
                        Text(
                            text = "Öncesi",
                            fontSize = 11.sp,
                            fontWeight = if (!showAfter) FontWeight.Bold else FontWeight.Normal,
                            color = if (!showAfter) Color.White else Slate600,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        color = if (showAfter) TealPrimary else Color.Transparent,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showAfter = true }
                    ) {
                        Text(
                            text = "Sonrası",
                            fontSize = 11.sp,
                            fontWeight = if (showAfter) FontWeight.Bold else FontWeight.Normal,
                            color = if (showAfter) Color.White else Slate600,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Simulated photo canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (!showAfter)
                            Brush.linearGradient(listOf(Color(0xFF78716C), Color(0xFF57534E)))
                        else
                            Brush.linearGradient(listOf(Color(0xFF0D9488), Color(0xFF14B8A6)))
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (!showAfter) Icons.Default.Construction else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (!showAfter) "⚠️ DURUM: ${item.beforeDesc}" else "✨ TESLİMAT: ${item.afterDesc}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun VideoShowcaseSection(videos: List<VideoShowcaseItem>) {
    if (videos.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                tint = FestiveCoral,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Canlı Performans & Gösteri Vitrini (Video)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        videos.forEach { video ->
            VideoShowcaseCard(video = video)
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun VideoShowcaseCard(video: VideoShowcaseItem) {
    var isPlaying by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF312E81), Color(0xFF4C1D95), Color(0xFF831843))
                        )
                    )
                    .clickable { isPlaying = !isPlaying },
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = FestiveCoralLight,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "▶ Performans Önizlemesi Oynatılıyor: ${video.title}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "(Durdurmak için dokunun)",
                            style = MaterialTheme.typography.labelSmall,
                            color = FestiveCoralLight
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(FestiveCoral),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Oynat",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }

                // Video badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${video.duration} • ${video.views}",
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
