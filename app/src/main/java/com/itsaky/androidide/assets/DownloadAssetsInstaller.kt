package com.itsaky.androidide.assets

import android.content.Context
import androidx.annotation.WorkerThread
import com.aayushatharva.brotli4j.decoder.BrotliInputStream
import com.itsaky.androidide.app.configuration.CpuArch
import com.itsaky.androidide.app.configuration.IDEBuildConfigProvider
import com.itsaky.androidide.managers.ToolsManager
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.TerminalInstaller
import com.itsaky.androidide.utils.retryOnceOnNoSuchFile
import com.itsaky.androidide.utils.throwIfNotSuccess
import com.itsaky.androidide.utils.withTempZipChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.adfa.constants.ANDROID_SDK_ZIP
import org.adfa.constants.DOCUMENTATION_DB
import org.adfa.constants.GRADLE_API_NAME_JAR
import org.adfa.constants.GRADLE_API_NAME_JAR_BR
import org.adfa.constants.GRADLE_API_NAME_JAR_ZIP
import org.adfa.constants.GRADLE_DISTRIBUTION_ARCHIVE_NAME
import org.adfa.constants.LOCAL_MAVEN_REPO_ARCHIVE_ZIP_NAME
import org.adfa.constants.PLUGIN_MAVEN_REPO_ZIP_BR
import org.adfa.constants.TEMPLATE_CORE_ARCHIVE
import org.adfa.constants.TEMPLATE_CORE_ARCHIVE_BR
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/**
 * DevxyzIDE Go addition: same install behaviour as [BundledAssetsInstaller], but the large
 * toolchain asset archives are NOT bundled in the APK. They are downloaded once from the
 * upstream dev-assets mirror during [preInstall] (MD5-verified against the mirror's .md5
 * sidecars, exactly like the build-time assetsDownload tasks) into filesDir/downloaded-assets,
 * keeping the APK small. The small build-generated assets (plugin artifacts, plugin maven
 * repo) still ship inside the APK and are read from it, so [doInstall] simply prefers a
 * downloaded file when one exists for the requested asset path.
 */
data object DownloadAssetsInstaller : BaseAssetsInstaller() {
	private val logger = LoggerFactory.getLogger(DownloadAssetsInstaller::class.java)

	private const val DEV_ASSETS_BASE_URL = "https://dev-assets.appdevforall.org"
	private const val DOWNLOADED_ASSETS_DIR = "downloaded-assets"

	private fun downloadedRoot(context: Context): File = File(context.filesDir, DOWNLOADED_ASSETS_DIR)

	private fun archDir(cpuArch: CpuArch): String =
		when (cpuArch) {
			CpuArch.AARCH64 -> "v8"
			CpuArch.ARM -> "v7"
			else -> throw IOException("No downloadable assets for CPU architecture: $cpuArch")
		}

	/** APK asset path -> path of the same file on the dev-assets mirror. */
	private fun downloads(archDir: String): List<Pair<String, String>> =
		listOf(
			ToolsManager.getCommonAsset("$GRADLE_DISTRIBUTION_ARCHIVE_NAME.br") to
				"release/$GRADLE_DISTRIBUTION_ARCHIVE_NAME.br",
			ToolsManager.getCommonAsset("$ANDROID_SDK_ZIP.br") to
				"release/$archDir/$ANDROID_SDK_ZIP.br",
			ToolsManager.getDatabaseAsset("$DOCUMENTATION_DB.br") to
				"release/$DOCUMENTATION_DB.br",
			ToolsManager.getCommonAsset("$LOCAL_MAVEN_REPO_ARCHIVE_ZIP_NAME.br") to
				"release/$LOCAL_MAVEN_REPO_ARCHIVE_ZIP_NAME.br",
			ToolsManager.getCommonAsset("${AssetsInstallationHelper.BOOTSTRAP_ENTRY_NAME}.br") to
				"release/$archDir/${AssetsInstallationHelper.BOOTSTRAP_ENTRY_NAME}.br",
			ToolsManager.getCommonAsset(GRADLE_API_NAME_JAR_BR) to
				"release/$GRADLE_API_NAME_JAR_BR",
			ToolsManager.getCommonAsset(TEMPLATE_CORE_ARCHIVE_BR) to
				"release/$TEMPLATE_CORE_ARCHIVE_BR",
		)

	override suspend fun preInstall(
		context: Context,
		stagingDir: Path,
	): Unit =
		withContext(Dispatchers.IO) {
			val archDir = archDir(IDEBuildConfigProvider.getInstance().cpuArch)
			val root = downloadedRoot(context)
			for ((assetPath, urlPath) in downloads(archDir)) {
				downloadIfNeeded(url = "$DEV_ASSETS_BASE_URL/$urlPath", target = File(root, assetPath))
			}
		}

	private fun downloadIfNeeded(
		url: String,
		target: File,
	) {
		val expectedMd5 = fetchMd5("$url.md5")
		if (target.isFile && fileMd5(target) == expectedMd5) {
			logger.debug("Downloaded asset is up to date: {}", target)
			return
		}
		target.parentFile?.mkdirs()
		val part = File(target.parentFile, target.name + ".part")
		logger.info("Downloading {} -> {}", url, target)
		val conn = URL(url).openConnection() as HttpURLConnection
		conn.requestMethod = "GET"
		conn.setRequestProperty("User-Agent", "Mozilla/5.0")
		conn.instanceFollowRedirects = true
		conn.connectTimeout = 15_000
		conn.readTimeout = 120_000
		try {
			val status = conn.responseCode
			if (status != HttpURLConnection.HTTP_OK) {
				throw IOException("Failed to download $url (HTTP $status: ${conn.responseMessage})")
			}
			conn.inputStream.use { input ->
				part.outputStream().use { output -> input.copyTo(output) }
			}
		} finally {
			conn.disconnect()
		}
		val actualMd5 = fileMd5(part)
		if (actualMd5 != expectedMd5) {
			part.delete()
			throw IOException("MD5 mismatch for $url (expected $expectedMd5, got $actualMd5)")
		}
		if (target.exists()) {
			target.delete()
		}
		if (!part.renameTo(target)) {
			part.copyTo(target, overwrite = true)
			part.delete()
		}
	}

	private fun fetchMd5(checksumUrl: String): String {
		val conn = URL(checksumUrl).openConnection() as HttpURLConnection
		conn.requestMethod = "GET"
		conn.setRequestProperty("User-Agent", "Mozilla/5.0")
		conn.instanceFollowRedirects = true
		conn.connectTimeout = 15_000
		conn.readTimeout = 15_000
		try {
			val status = conn.responseCode
			if (status != HttpURLConnection.HTTP_OK) {
				throw IOException("Failed to fetch checksum from $checksumUrl (HTTP $status: ${conn.responseMessage})")
			}
			val checksum = conn.inputStream.bufferedReader().use { it.readText().trim() }
			if (!checksum.matches(Regex("^[a-fA-F0-9]{32}$"))) {
				throw IOException("Invalid MD5 checksum from $checksumUrl (got: '${checksum.take(50)}')")
			}
			return checksum.lowercase()
		} finally {
			conn.disconnect()
		}
	}

	private fun fileMd5(file: File): String {
		val digest = MessageDigest.getInstance("MD5")
		file.inputStream().use { input ->
			val buffer = ByteArray(8192)
			var read: Int
			while (input.read(buffer).also { read = it } > 0) {
				digest.update(buffer, 0, read)
			}
		}
		return digest.digest().joinToString("") { "%02x".format(it) }
	}

	/** Downloaded file when one exists for [assetPath]; APK asset otherwise. */
	private fun openAsset(
		context: Context,
		assetPath: String,
	): InputStream {
		val downloaded = File(downloadedRoot(context), assetPath)
		return if (downloaded.isFile) FileInputStream(downloaded) else context.assets.open(assetPath)
	}

	private fun writeDownloadedBrotliToPath(
		context: Context,
		assetPath: String,
		destPath: Path,
	) {
		openAsset(context, assetPath).use { stream ->
			BrotliInputStream(stream).use { brotli ->
				Files.newOutputStream(destPath).use { output -> brotli.copyTo(output) }
			}
		}
	}

	@OptIn(ExperimentalPathApi::class)
	@WorkerThread
	override suspend fun doInstall(
		context: Context,
		stagingDir: Path,
		cpuArch: CpuArch,
		entryName: String,
	): Unit =
		withContext(Dispatchers.IO) {
			when (entryName) {
				GRADLE_DISTRIBUTION_ARCHIVE_NAME,
				ANDROID_SDK_ZIP,
				-> {
					val destDir = destinationDirForArchiveEntry(entryName).toPath()
					if (Files.exists(destDir)) {
						destDir.deleteRecursively()
					}
					Files.createDirectories(destDir)
					val assetPath = ToolsManager.getCommonAsset("$entryName.br")
					openAsset(context, assetPath).use { assetStream ->
						BrotliInputStream(assetStream).use { srcStream ->
							AssetsInstallationHelper.extractZipToDir(srcStream, destDir)
						}
					}
				}

				LOCAL_MAVEN_REPO_ARCHIVE_ZIP_NAME -> {
					val destDir = destinationDirForArchiveEntry(entryName).toPath()
					if (Files.exists(destDir)) {
						destDir.deleteRecursively()
					}
					Files.createDirectories(destDir)
					// 1) harvested repo
					openAsset(context, ToolsManager.getCommonAsset("$entryName.br")).use { assetStream ->
						BrotliInputStream(assetStream).use { srcStream ->
							AssetsInstallationHelper.extractZipToDir(srcStream, destDir)
						}
					}
					// 2) plugin coordinate overlay -- merged (no wipe) into the same repo
					openAsset(context, ToolsManager.getCommonAsset(PLUGIN_MAVEN_REPO_ZIP_BR)).use { assetStream ->
						BrotliInputStream(assetStream).use { srcStream ->
							AssetsInstallationHelper.extractZipToDir(srcStream, destDir)
						}
					}
					logger.debug("Merged plugin coordinates into {}", destDir)
				}

				GRADLE_API_NAME_JAR_ZIP -> {
					val assetPath = ToolsManager.getCommonAsset(GRADLE_API_NAME_JAR_BR)
					BrotliInputStream(openAsset(context, assetPath)).use { input ->
						val destFile = Environment.GRADLE_GEN_JARS.resolve(GRADLE_API_NAME_JAR)
						destFile.outputStream().use { output ->
							input.copyTo(output)
						}
					}
				}

				TEMPLATE_CORE_ARCHIVE -> {
					val assetPath = ToolsManager.getCommonAsset(TEMPLATE_CORE_ARCHIVE_BR)
					BrotliInputStream(openAsset(context, assetPath)).use { input ->
						val destFile = Environment.TEMPLATES_DIR.resolve(TEMPLATE_CORE_ARCHIVE)
						destFile.outputStream().use { output ->
							input.copyTo(output)
						}
					}
				}

				AssetsInstallationHelper.BOOTSTRAP_ENTRY_NAME -> {
					val assetPath =
						ToolsManager.getCommonAsset("${AssetsInstallationHelper.BOOTSTRAP_ENTRY_NAME}.br")

					val result =
						retryOnceOnNoSuchFile(
							onFirstFailure = { Files.createDirectories(stagingDir) },
							onSecondFailure = { e2 ->
								throw IOException(
									context.getString(R.string.terminal_installation_failed_low_storage),
									e2,
								)
							},
						) {
							withTempZipChannel(
								stagingDir = stagingDir,
								prefix = "bootstrap",
								writeTo = { path -> writeDownloadedBrotliToPath(context, assetPath, path) },
								useChannel = { ch -> TerminalInstaller.installIfNeeded(context, ch) },
							)
						}

					result.throwIfNotSuccess(context)
				}

				DOCUMENTATION_DB -> {
					BrotliInputStream(openAsset(context, ToolsManager.getDatabaseAsset("${DOCUMENTATION_DB}.br"))).use { input ->
						Environment.DOC_DB.outputStream().use { output ->
							input.copyTo(output)
						}
					}
				}

				AssetsInstallationHelper.PLUGIN_ARTIFACTS_ZIP -> {
					logger.debug("Extracting plugin artifacts from '{}'", entryName)
					val pluginDir =
						Environment.PLUGIN_API_JAR.parentFile
							?: throw IllegalStateException("Plugin API parent directory is null")
					val pluginDirPath = pluginDir.toPath().toAbsolutePath().normalize()
					if (Files.exists(pluginDirPath)) {
						pluginDirPath.deleteRecursively()
					}
					Files.createDirectories(pluginDirPath)

					val assetPath = ToolsManager.getCommonAsset("$entryName.br")
					openAsset(context, assetPath).use { assetStream ->
						BrotliInputStream(assetStream).use { brotliStream ->
							AssetsInstallationHelper.extractZipToDir(brotliStream, pluginDirPath)
						}
					}
					logger.debug("Completed extracting plugin artifacts")
				}

				else -> {
					throw IllegalStateException("Unknown entry: $entryName")
				}
			}
		}

	override fun expectedSize(entryName: String): Long =
		when (entryName) {
			GRADLE_DISTRIBUTION_ARCHIVE_NAME -> 63399283L
			ANDROID_SDK_ZIP -> 254814511L
			DOCUMENTATION_DB -> 297763377L
			LOCAL_MAVEN_REPO_ARCHIVE_ZIP_NAME -> 97485855L
			AssetsInstallationHelper.BOOTSTRAP_ENTRY_NAME -> 124120151L
			GRADLE_API_NAME_JAR_ZIP -> 29447748L
			AssetsInstallationHelper.PLUGIN_ARTIFACTS_ZIP -> 86442L
			TEMPLATE_CORE_ARCHIVE -> 133120L
			else -> 0L
		}

	private fun destinationDirForArchiveEntry(entryName: String): File =
		when (entryName) {
			GRADLE_DISTRIBUTION_ARCHIVE_NAME -> Environment.GRADLE_DISTS
			ANDROID_SDK_ZIP -> Environment.ANDROID_HOME
			LOCAL_MAVEN_REPO_ARCHIVE_ZIP_NAME -> Environment.LOCAL_MAVEN_DIR
			GRADLE_API_NAME_JAR_ZIP -> Environment.GRADLE_GEN_JARS
			else -> throw IllegalStateException("Entry '$entryName' is not expected to be an archive")
		}
}
