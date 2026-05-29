plugins {
    id("java")
    id("application")
}

group = "dev.oddsystems.copeland"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

application {
    mainClass = "dev.oddsystems.copeland.Main"
    applicationName = "CopelandDB"
    // Lucene 10.x MMapDirectory uses FFM (java.lang.foreign). Without
    // --enable-native-access the JVM logs warnings; in future JDKs the
    // call would be denied outright.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

dependencies {
    implementation("org.apache.lucene:lucene-core:10.4.0")
    implementation("org.apache.lucene:lucene-codecs:10.4.0")
    implementation("org.apache.lucene:lucene-backward-codecs:10.4.0")

    // StandardAnalyzer is bundled in lucene-core, so analysis-common stays
    // out until we want richer analyzers / tokenizers.
    // implementation("org.apache.lucene:lucene-analysis-common:10.4.0")

    // Enable in Phase 7 when we wire IndexSearcher / Query.
    // implementation("org.apache.lucene:lucene-queries:10.4.0")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
