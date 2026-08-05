package com.corverxis.nexgensocial.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.corverxis.nexgensocial.network.ApiClient
import com.corverxis.nexgensocial.services.PushRegistrar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthState(
    val user: User? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val isSignedIn: Boolean get() = _state.value.user != null

    init { restore() }

    /**
     * A stored token may be expired or revoked, so it's verified against the
     * server rather than trusted. Skipping this shows a signed-in UI whose
     * every request then fails.
     */
    private fun restore() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val token = TokenStore.load(context)
            if (token == null) {
                _state.value = AuthState(isLoading = false)
                return@launch
            }
            runCatching { ApiClient.get<MeResponse>("/api/auth/me") }
                .onSuccess { _state.value = AuthState(user = it.user, isLoading = false) }
                .onFailure {
                    TokenStore.clear(context)
                    _state.value = AuthState(isLoading = false)
                }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(errorMessage = null, isLoading = true)
            runCatching {
                ApiClient.post<AuthResponse>(
                    "/api/auth/login",
                    mapOf("email" to email, "password" to password)
                )
            }.onSuccess { response ->
                TokenStore.save(getApplication(), response.token)
                _state.value = AuthState(user = response.user, isLoading = false)
                PushRegistrar.registerCurrentToken()
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, errorMessage = it.message)
            }
        }
    }

    fun signUp(
        email: String,
        username: String,
        displayName: String,
        password: String,
        acceptedTerms: Boolean,
    ) {
        if (!acceptedTerms) {
            _state.value = _state.value.copy(
                errorMessage = "You must accept the Terms of Use and Privacy Policy."
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(errorMessage = null, isLoading = true)
            runCatching {
                ApiClient.post<AuthResponse>(
                    "/api/auth/register",
                    mapOf(
                        "email" to email,
                        "username" to username,
                        "displayName" to displayName,
                        "password" to password,
                        "acceptedTerms" to true,
                        "policyVersion" to AppConfig.POLICY_VERSION,
                    )
                )
            }.onSuccess { response ->
                TokenStore.save(getApplication(), response.token)
                _state.value = AuthState(user = response.user, isLoading = false)
                PushRegistrar.registerCurrentToken()
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, errorMessage = it.message)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            PushRegistrar.unregister()
            TokenStore.clear(getApplication())
            _state.value = AuthState(isLoading = false)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}

object AppConfig {
    const val POLICY_VERSION = "2026-07-30"
    const val WEBSITE_URL = "https://nexgensocialnet.com"
    const val PRIVACY_URL = "https://nexgensocialnet.com/legal/privacy"
    const val TERMS_URL = "https://nexgensocialnet.com/legal/terms"
}
