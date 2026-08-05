package com.corverxis.nexgensocial.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corverxis.nexgensocial.data.AppConfig
import com.corverxis.nexgensocial.data.AuthViewModel
import com.corverxis.nexgensocial.network.ApiClient
import com.corverxis.nexgensocial.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(authViewModel: AuthViewModel) {
    val state by authViewModel.state.collectAsState()
    val user = state.user
    val uriHandler = LocalUriHandler.current
    var showSignOutDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Profile") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Avatar(user?.avatarUrl, user?.username ?: "?", 84.dp)
                    Text(user?.displayName.orEmpty(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("@${user?.username.orEmpty()}", fontSize = 13.sp, color = Slate400)
                    user?.bio?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 13.sp, color = Slate300, textAlign = TextAlign.Center)
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column {
                    SettingsRow(Icons.Filled.Download, "Export my data") {
                        uriHandler.openUri(ApiClient.BASE_URL + "/api/users/me/export")
                    }
                    HorizontalDivider(color = LineColor)
                    SettingsRow(Icons.Filled.Description, "Privacy Policy") {
                        uriHandler.openUri(AppConfig.PRIVACY_URL)
                    }
                    HorizontalDivider(color = LineColor)
                    SettingsRow(Icons.Filled.Gavel, "Terms of Use") {
                        uriHandler.openUri(AppConfig.TERMS_URL)
                    }
                }
            }

            OutlinedButton(
                onClick = { showSignOutDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sign out") }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out?") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    authViewModel.signOut()
                }) { Text("Sign out", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Cyan400, modifier = Modifier.size(22.dp))
        Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Slate400)
    }
}
