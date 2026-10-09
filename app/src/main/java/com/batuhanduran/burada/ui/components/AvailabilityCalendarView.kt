package com.batuhanduran.burada.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batuhanduran.burada.ui.theme.*
import java.util.Date

@Composable
fun AvailabilityCalendarView(
    bookedDatesJson: String,
    isOpenForOffers: Boolean,
    isEditable: Boolean = false,
    onDateToggle: (String) -> Unit = {},
    onToggleOpenForOffers: () -> Unit = {},
    today: Date = Date()
) {
    val bookedList = parseJsonList(bookedDatesJson)
    val upcomingDays = generateUpcomingCalendarDays(today)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Müsaitlik & Etkinlik Takvimi",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Status chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOpenForOffers) SafeBadgeGreenContainer else EmergencyRedContainer
                ) {
                    Text(
                        text = if (isOpenForOffers) "Teklife Açık" else "Dolu / Kapalı",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOpenForOffers) SafeBadgeText else EmergencyRed,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isEditable)
                    "Tarihe dokunarak 'Dolu' veya 'Müsait' durumunu değiştirebilirsiniz."
                else
                    "Palyaço, maskot ve ustaların güncel takvimi. Kırmızı günler rezerve edilmiştir.",
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Calendar Days Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                upcomingDays.take(5).forEach { day ->
                    DaySlotItem(
                        day = day,
                        isBooked = bookedList.contains(day.dateIso) || !isOpenForOffers,
                        isEditable = isEditable,
                        onDateToggle = onDateToggle
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                upcomingDays.drop(5).take(5).forEach { day ->
                    DaySlotItem(
                        day = day,
                        isBooked = bookedList.contains(day.dateIso) || !isOpenForOffers,
                        isEditable = isEditable,
                        onDateToggle = onDateToggle
                    )
                }
            }

            if (isEditable) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onToggleOpenForOffers,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOpenForOffers) EmergencyRed else SafeBadgeGreen
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_toggle_offers")
                ) {
                    Text(
                        text = if (isOpenForOffers) "⏸️ Tüm Teklif Alımını Kapat (İzne Ayrıl)" else "▶️ Teklif Alımını Tekrar Aç",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DaySlotItem(
    day: CalendarDay,
    isBooked: Boolean,
    isEditable: Boolean,
    onDateToggle: (String) -> Unit
) {
    val bgColor = if (isBooked) EmergencyRedContainer else SafeBadgeGreenContainer
    val textColor = if (isBooked) EmergencyRed else SafeBadgeText

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(enabled = isEditable) { onDateToggle(day.dateIso) }
            .padding(vertical = 6.dp)
            .testTag("calendar_day_${day.dateIso}")
    ) {
        Text(
            text = day.dayName,
            fontSize = 11.sp,
            fontWeight = if (day.isWeekend) FontWeight.Bold else FontWeight.Medium,
            color = if (day.isWeekend && !isBooked) FestiveCoral else textColor
        )
        Text(
            text = day.dayNumber,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
        Text(
            text = if (isBooked) "Dolu" else "Boş",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}
