plugins {
    id("com.android.library") version "8.4.0"
}
android {
    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}
