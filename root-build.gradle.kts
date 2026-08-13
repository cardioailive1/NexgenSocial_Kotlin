plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
    // Version per the Firebase console setup instructions. "apply false"
    // declares it for the whole project without applying it here -- the app
    // module applies it conditionally, so a missing google-services.json
    // warns instead of failing the build outright.
    id("com.google.gms.google-services") version "4.5.0" apply false
}
