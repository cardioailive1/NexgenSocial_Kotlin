package com.corverxis.nexgensocial.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corverxis.nexgensocial.data.AppConfig
import com.corverxis.nexgensocial.data.AuthViewModel
import com.corverxis.nexgensocial.ui.theme.*

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    var isSignUp by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(50.dp))
        Text("NexgenSocial", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "A social platform that shows its working",
            fontSize = 14.sp, color = Slate400,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
        )

        if (isSignUp) SignUpForm(viewModel, state.errorMessage, state.isLoading)
        else SignInForm(viewModel, state.errorMessage, state.isLoading)

        TextButton(onClick = { isSignUp = !isSignUp; viewModel.clearError() }) {
            Text(
                if (isSignUp) "Already have an account? Sign in"
                else "New here? Create an account",
                color = Cyan300, fontSize = 13.sp,
            )
        }

        if (isSignUp) HighlightsCard()
        Spacer(Modifier.height(40.dp))
    }
}

/**
 * Password input with a Show/Hide toggle (UAT-006).
 *
 * Only the VisualTransformation changes -- the underlying value is never
 * touched, so revealing a password can't alter what gets submitted.
 */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Password",
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it, color = Danger) } },
        visualTransformation =
            if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(if (visible) "Hide" else "Show", fontSize = 12.sp, color = Cyan300)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SignInForm(viewModel: AuthViewModel, error: String?, busy: Boolean) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val emailValid = email.isEmpty() || EMAIL_REGEX.matches(email)
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                isError = !emailValid,
                supportingText = if (!emailValid) {
                    { Text("That doesn't look like a valid email address.", color = Danger) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(value = password, onValueChange = { password = it })
            error?.let { Text(it, color = Danger, fontSize = 13.sp) }
            Button(
                onClick = { viewModel.signIn(email, password) },
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Signing in…" else "Sign in") }

            // Opens the web reset flow rather than duplicating it natively:
            // the reset link arrives by email and lands on the site anyway.
            TextButton(
                onClick = { uriHandler.openUri("${AppConfig.WEBSITE_URL}/forgot-password") },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Forgot your password?", fontSize = 12.sp, color = Cyan300) }
        }
    }
}

@Composable
private fun SignUpForm(viewModel: AuthViewModel, error: String?, busy: Boolean) {
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var acceptedTerms by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    // Mirrors the server rule exactly, so the person sees the problem
    // inline instead of after a round-trip that returns 400.
    val usernameValid = username.matches(Regex("^[a-zA-Z0-9_.-]{3,30}$"))

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val emailValid = email.isEmpty() || EMAIL_REGEX.matches(email)
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") }, singleLine = true,
                isError = !emailValid,
                supportingText = if (!emailValid) {
                    { Text("That doesn't look like a valid email address.", color = Danger) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text("Username") }, singleLine = true,
                isError = username.isNotEmpty() && !usernameValid,
                supportingText = {
                    if (username.isNotEmpty() && !usernameValid) {
                        Text("Letters, numbers, and _ . - only. No spaces.", color = Danger)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = displayName, onValueChange = { displayName = it },
                label = { Text("Display name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Password (8+ characters)",
                isError = password.isNotEmpty() && password.length < 8,
                supportingText = if (password.isNotEmpty() && password.length < 8)
                    "Password must be at least 8 characters." else null,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = acceptedTerms, onCheckedChange = { acceptedTerms = it })
                Text(
                    "I agree to the Terms of Use and Privacy Policy",
                    fontSize = 12.sp, color = Slate300,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TextButton(onClick = { uriHandler.openUri(AppConfig.TERMS_URL) }) {
                    Text("Terms", fontSize = 12.sp, color = Cyan300)
                }
                TextButton(onClick = { uriHandler.openUri(AppConfig.PRIVACY_URL) }) {
                    Text("Privacy", fontSize = 12.sp, color = Cyan300)
                }
            }

            error?.let { Text(it, color = Danger, fontSize = 13.sp) }

            Button(
                onClick = {
                    viewModel.signUp(email, username, displayName, password, acceptedTerms)
                },
                enabled = !busy && acceptedTerms && usernameValid &&
                        email.isNotBlank() && displayName.isNotBlank() && password.length >= 8,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Creating…" else "Create account") }
        }
    }
}

/** The same highlights shown on the web signup page and in the iOS app. */
@Composable
private fun HighlightsCard() {
    val items = listOf(
        Triple(Icons.Filled.PlayArrow, "Reels that reach beyond your followers",
            "Reels rank on whether people watch to the end, not on follower count."),
        Triple(Icons.Filled.Tune, "Your feed, your rules",
            "Set how your feed weights recency, engagement and diversity."),
        Triple(Icons.Filled.Lock, "Ad settings off by default",
            "Interest targeting is opt-in. We never sell your profile."),
        Triple(Icons.Filled.Call, "Messages, voice and video calls",
            "Talk to anyone on NexgenSocial from anywhere with internet."),
        Triple(Icons.Filled.Videocam, "NexgenMeet",
            "Video meetings with waiting rooms and host controls."),
        Triple(Icons.Filled.ShoppingBag, "Marketplace and jobs",
            "Real photo and video listings; salary ranges shown up front."),
        Triple(Icons.Filled.Download, "Take your data with you",
            "Export everything you've posted in one tap."),
    )

    Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("What you get", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            items.forEach { (icon, title, body) ->
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Icon(icon, contentDescription = null, tint = Cyan400,
                        modifier = Modifier.size(22.dp))
                    Column {
                        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(body, fontSize = 12.sp, color = Slate400)
                    }
                }
            }
        }
    }
}


// Shared so Login and Registration validate identically -- two slightly
// different rules would be worse than one.
val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
