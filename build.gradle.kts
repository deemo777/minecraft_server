plugins {
    java
}

group = "com.htk"
version = "4.11.0"

val paperVersion: String by project
val javaVersion: String by project

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion.toInt()))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    inputs.property("version", project.version)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

sourceSets.test {
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().compileClasspath
}

val verifyLandRules by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.land.LandRulesTest")
}

tasks.check { dependsOn(verifyLandRules) }

val verifyEnhancementRules by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.tools.UpgradeRulesTest")
}
tasks.check { dependsOn(verifyEnhancementRules) }

// Tests use a dependency-free assertion runner via verifyLandRules, rather than JUnit.
tasks.test { failOnNoDiscoveredTests = false }

// Windows native console encoding may differ from Gradle's UTF-8 output decoder.
tasks.withType<JavaExec>().configureEach {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

val verifyRankingRules by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.ranking.RankingRulesTest")
}
tasks.check { dependsOn(verifyRankingRules) }

val verifyPurchaseRules by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.merchant.PurchaseRulesTest")
}
tasks.check { dependsOn(verifyPurchaseRules) }

val verifyNpcFacing by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.npc.NpcFacingTest")
}
tasks.check { dependsOn(verifyNpcFacing) }

val verifyCommandNames by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.common.CommandNamesTest")
}
tasks.check { dependsOn(verifyCommandNames) }

val verifyDebtSettlement by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.land.DebtSettlementTest")
}
tasks.check { dependsOn(verifyDebtSettlement) }

val verifyMessages by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.common.MessagesTest")
}
tasks.check { dependsOn(verifyMessages) }

val verifyLobbyVision by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.lobby.LobbyVisionTest")
}
tasks.check { dependsOn(verifyLobbyVision) }

val verifyArtisanRules by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.tools.ArtisanRulesTest")
}
tasks.check { dependsOn(verifyArtisanRules) }

val verifyEnhancementPayment by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.tools.EnhancementPaymentTest")
}
tasks.check { dependsOn(verifyEnhancementPayment) }

val verifyAchievements by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.achievement.AchievementRulesTest")
}
val verifyUniqueRewards by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.economy.UniqueRewardTest")
}
tasks.check { dependsOn(verifyAchievements,verifyUniqueRewards) }

val verifyGlyphBars by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.common.GlyphBarTest")
}
tasks.check { dependsOn(verifyGlyphBars) }

val verifyLegacyRefund by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.economy.LegacyStakeRefundTest")
}
tasks.check { dependsOn(verifyLegacyRefund) }

val verifyRepairRules by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.tools.RepairRulesTest")
}
tasks.check { dependsOn(verifyRepairRules) }

val verifySlotRules by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.slots.SlotRulesTest")
}
val verifySlotSettlement by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.economy.SlotCoinSettlementTest")
}
tasks.check { dependsOn(verifySlotRules, verifySlotSettlement) }

val verifyWeapons by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.tools.WeaponPerformanceTest")
}
tasks.check { dependsOn(verifyWeapons) }

val verifyNumericEffects by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.tools.NumericEffectsTest")
}
tasks.check { dependsOn(verifyNumericEffects) }

val exportEquipmentStats by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.tools.EquipmentStatsReport")
}

val verifyProgression by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.tools.ProgressionTest")
}
tasks.check { dependsOn(verifyProgression) }

val verifyWeaponTraits by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.tools.WeaponTraitStateTest")
}
tasks.check { dependsOn(verifyWeaponTraits) }

val verifyWarpRules by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.menu.WarpRulesTest")
}
tasks.check { dependsOn(verifyWarpRules) }

val verifySkinPurchases by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.htk.core.economy.SkinPurchaseTest")
}
tasks.check { dependsOn(verifySkinPurchases) }
