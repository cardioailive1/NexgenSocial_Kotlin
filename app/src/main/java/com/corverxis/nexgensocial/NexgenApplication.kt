package com.corverxis.nexgensocial

import android.app.Application
import com.corverxis.nexgensocial.data.TokenStore
import com.corverxis.nexgensocial.services.NexgenFirebaseService

class NexgenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Load the token before any screen makes a request, and create
        // notification channels before anything tries to post one.
        TokenStore.load(this)
        NexgenFirebaseService.createChannels(this)
    }
}
