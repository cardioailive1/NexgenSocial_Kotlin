package com.corverxis.nexgensocial.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.corverxis.nexgensocial.data.AuthViewModel
import com.corverxis.nexgensocial.ui.screens.*

@Composable
fun RootScreen(
    authViewModel: AuthViewModel,
    initialDeepLink: String? = null,
    answerCallId: String? = null,
) {
    val state by authViewModel.state.collectAsState()

    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.user == null -> AuthScreen(authViewModel)
        else -> MainScaffold(authViewModel, initialDeepLink, answerCallId)
    }
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("feed", "Feed", Icons.Filled.Home),
    TabItem("reels", "Reels", Icons.Filled.PlayArrow),
    TabItem("explore", "Explore", Icons.Filled.Search),
    TabItem("messages", "Messages", Icons.Filled.Email),
    TabItem("profile", "Profile", Icons.Filled.Person),
)

@Composable
fun MainScaffold(
    authViewModel: AuthViewModel,
    initialDeepLink: String?,
    answerCallId: String?,
) {
    val navController = rememberNavController()

    // Arriving from a notification should land on the right screen rather
    // than dumping the person on the feed.
    LaunchedEffect(initialDeepLink, answerCallId) {
        when {
            answerCallId != null -> navController.navigate("call/$answerCallId")
            initialDeepLink?.contains("/messages") == true -> navController.navigate("messages")
            initialDeepLink?.contains("/reels") == true -> navController.navigate("reels")
        }
    }

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination

            // The call screen is full-bleed; a nav bar under it looks wrong
            // and invites tapping away mid-call.
            if (currentDestination?.route?.startsWith("call/") != true) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "feed",
            modifier = Modifier.padding(padding),
        ) {
            composable("feed") { FeedScreen() }
            composable("reels") { ReelsScreen() }
            composable("explore") { ExploreScreen() }
            composable("messages") { MessagesScreen(navController) }
            composable("profile") { ProfileScreen(authViewModel) }
            composable("conversation/{id}") { entry ->
                ConversationScreen(
                    conversationId = entry.arguments?.getString("id").orEmpty(),
                    navController = navController,
                )
            }
            composable("call/{id}") { entry ->
                CallScreen(
                    callId = entry.arguments?.getString("id").orEmpty(),
                    navController = navController,
                )
            }
        }
    }
}
