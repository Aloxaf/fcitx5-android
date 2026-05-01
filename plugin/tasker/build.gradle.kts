plugins {
    id("org.fcitx.fcitx5.android.app-convention")
    id("org.fcitx.fcitx5.android.plugin-app-convention")
    id("org.fcitx.fcitx5.android.build-metadata")
    id("org.fcitx.fcitx5.android.data-descriptor")
}

android {
    namespace = "org.fcitx.fcitx5.android.plugin.tasker"

    defaultConfig {
        applicationId = "org.fcitx.fcitx5.android.plugin.tasker"
    }

    buildFeatures {
        resValues = true
    }

    buildTypes {
        release {
            resValue("string", "app_name", "@string/app_name_release")
            proguardFile("proguard-rules.pro")
        }
        debug {
            resValue("string", "app_name", "@string/app_name_debug")
        }
    }
}

dependencies {
    implementation(project(":lib:plugin-base"))
    implementation("com.joaomgcd:taskerpluginlibrary:0.4.10")
}
