// tag::avoid-this[]
buildscript {
    repositories {
        gradlePluginPortal() // <1>
    }

    dependencies {
        classpath("com.google.protobuf:com.google.protobuf.gradle.plugin:0.9.4") // <2>
    }
}

apply(plugin = "java") // <3>
apply(plugin = "com.google.protobuf") // <4>
// end::avoid-this[]
