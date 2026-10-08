package com.batuhanduran.burada.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batuhanduran.burada.ui.ScreenDestination
import com.batuhanduran.burada.ui.theme.*

@Composable
fun MarketplaceBottomNavBar(
    currentScreen: ScreenDestination,
    onTabSelected: (ScreenDestination) -> Unit,
    unreadMessagesCount: Int = 0,
    activeRequestsCount: Int = 0,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.testTag("main_bottom_nav_bar"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        // Tab 1: Vitrin / Ana Sayfa
        val isHome = currentScreen is ScreenDestination.Home
        NavigationBarItem(
            selected = isHome,
            onClick = { onTabSelected(ScreenDestination.Home) },
            icon = {
                Icon(
                    imageVector = if (isHome) Icons.Default.Storefront else Icons.Outlined.Storefront,
                    contentDescription = "Vitrin"
                )
            },
            label = {
                Text(
                    text = "Vitrin",
                    fontSize = 11.sp,
                    fontWeight = if (isHome) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = TealPrimary,
                selectedTextColor = TealPrimary,
                indicatorColor = TealContainer
            ),
            modifier = Modifier.testTag("nav_item_home")
        )

        // Tab 2: Neighborhood discovery
        val isMap = currentScreen is ScreenDestination.MapView
        NavigationBarItem(
            selected = isMap,
            onClick = { onTabSelected(ScreenDestination.MapView) },
            icon = {
                Icon(
                    imageVector = if (isMap) Icons.Default.Explore else Icons.Outlined.Explore,
                    contentDescription = "Mahalle keşfi"
                )
            },
            label = {
                Text(
                    text = "Mahalleler",
                    fontSize = 11.sp,
                    fontWeight = if (isMap) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = TealPrimary,
                selectedTextColor = TealPrimary,
                indicatorColor = TealContainer
            ),
            modifier = Modifier.testTag("nav_item_map")
        )

        // Tab 3: Taleplerim & Havuz
        val isRequests = currentScreen is ScreenDestination.MyRequests
        NavigationBarItem(
            selected = isRequests,
            onClick = { onTabSelected(ScreenDestination.MyRequests) },
            icon = {
                BadgedBox(
                    badge = {
                        if (activeRequestsCount > 0) {
                            Badge(containerColor = FestiveCoral, contentColor = Color.White) {
                                Text("$activeRequestsCount")
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isRequests) Icons.Default.Assignment else Icons.Outlined.Assignment,
                        contentDescription = "Taleplerim"
                    )
                }
            },
            label = {
                Text(
                    text = "Taleplerim",
                    fontSize = 11.sp,
                    fontWeight = if (isRequests) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = TealPrimary,
                selectedTextColor = TealPrimary,
                indicatorColor = TealContainer
            ),
            modifier = Modifier.testTag("nav_item_requests")
        )

        // Tab 4: Mesajlarım
        val isMessages = currentScreen is ScreenDestination.ConversationsList
        NavigationBarItem(
            selected = isMessages,
            onClick = { onTabSelected(ScreenDestination.ConversationsList) },
            icon = {
                BadgedBox(
                    badge = {
                        if (unreadMessagesCount > 0) {
                            Badge(containerColor = FestiveCoral, contentColor = Color.White) {
                                Text("$unreadMessagesCount")
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isMessages) Icons.Default.Chat else Icons.Outlined.Chat,
                        contentDescription = "Mesajlar"
                    )
                }
            },
            label = {
                Text(
                    text = "Mesajlar",
                    fontSize = 11.sp,
                    fontWeight = if (isMessages) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = TealPrimary,
                selectedTextColor = TealPrimary,
                indicatorColor = TealContainer
            ),
            modifier = Modifier.testTag("nav_item_messages")
        )
    }
}
