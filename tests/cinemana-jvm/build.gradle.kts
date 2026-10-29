plugins { kotlin("jvm") version "2.3.0" }
repositories { mavenCentral() }
kotlin { jvmToolchain(17) }
sourceSets {
    main {
        kotlin.srcDir("../../mobile-overlay/composeApp/src/androidMain/kotlin")
        kotlin.include("com/nuvio/android/cinemana/**")
        kotlin.exclude("**/CinemanaProvider.kt")
    }
    test { kotlin.srcDir("../../mobile-overlay/composeApp/src/androidHostTest/kotlin") }
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.json:json:20240303")
    testImplementation(kotlin("test-junit"))
}
tasks.test { testLogging { events("passed", "failed", "skipped") } }
