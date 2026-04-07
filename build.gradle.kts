// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // Add the kapt plugin
    alias(libs.plugins.kotlin.kapt) apply false
    // Add the ObjectBox plugin
    alias(libs.plugins.objectbox) apply false
}