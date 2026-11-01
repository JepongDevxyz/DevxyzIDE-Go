/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.services.builder

import com.itsaky.androidide.preferences.internal.BuildPreferences
import com.itsaky.androidide.tooling.api.messages.GradleDistributionParams
import com.itsaky.androidide.utils.readGradleVersion
import java.io.File
import org.adfa.constants.GRADLE_DISTRIBUTIONS_DIR
import org.adfa.constants.GRADLE_DISTRIBUTION_NAME
import org.adfa.constants.GRADLE_DISTRIBUTION_VERSION

/** The Gradle installation bundled with the IDE. */
private val bundledGradleDir: String
  get() = "$GRADLE_DISTRIBUTIONS_DIR/$GRADLE_DISTRIBUTION_NAME"

/**
 * The distribution params for [projectDir]. This considers [gradleInstallationDir] preference
 * as well.
 *
 * An installation directory explicitly chosen in Settings (any value other than the bundled
 * default) always wins, and a blank value keeps the wrapper behavior. Otherwise the Gradle
 * version from the project's own `gradle-wrapper.properties` is honored: a project authored
 * for an older Gradle cannot configure on the bundled Gradle 9.x when its Android Gradle
 * Plugin predates AGP 7.4, because those AGP versions call
 * `Provider.forUseAtConfigurationTime()`, which Gradle 9 removed. A distribution already
 * present in the distributions folder is used as an installation; any other version is
 * requested by version so the Tooling API downloads it once. Projects without a readable
 * wrapper version, and projects already on the bundled version, keep the bundled
 * installation.
 *
 * One floor applies: Gradle before 8.5 cannot run on this IDE's JDK 21 at all, so a project
 * pinned to an older Gradle is floored to 8.5 -- the oldest Gradle that starts on JDK 21,
 * and the one closest to what those projects were authored against.
 */
suspend fun gradleDistributionParams(projectDir: File): GradleDistributionParams {
  val customDir = BuildPreferences.gradleInstallationDir
  if (customDir.isBlank()) {
    return GradleDistributionParams.WRAPPER
  }
  if (customDir != bundledGradleDir) {
    return GradleDistributionParams.forInstallationDir(customDir)
  }

  val projectVersion = readGradleVersion(projectDir)
  if (projectVersion == "Unknown" || projectVersion == GRADLE_DISTRIBUTION_VERSION) {
    return GradleDistributionParams.forInstallationDir(bundledGradleDir)
  }

  val effectiveVersion =
    if (isVersionBelow(projectVersion, MIN_JDK21_GRADLE_VERSION)) {
      MIN_JDK21_GRADLE_VERSION
    } else {
      projectVersion
    }

  val localDir = File(GRADLE_DISTRIBUTIONS_DIR, "gradle-$effectiveVersion")
  if (localDir.isDirectory) {
    return GradleDistributionParams.forInstallationDir(localDir.absolutePath)
  }

  return GradleDistributionParams.forVersion(effectiveVersion)
}

/** The oldest Gradle version that can run on JDK 21. */
private const val MIN_JDK21_GRADLE_VERSION = "8.5"

/** Whether [version] (e.g. "7.6.1") sorts strictly below [reference] (e.g. "8.5"). */
private fun isVersionBelow(version: String, reference: String): Boolean {
  val parts = version.split('.').map { it.toIntOrNull() ?: 0 }
  val refParts = reference.split('.').map { it.toIntOrNull() ?: 0 }
  for (i in 0 until maxOf(parts.size, refParts.size)) {
    val part = parts.getOrElse(i) { 0 }
    val refPart = refParts.getOrElse(i) { 0 }
    if (part != refPart) {
      return part < refPart
    }
  }
  return false
}