package com.jarvis.android.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.jarvis.android.BuildConfig
import com.jarvis.android.transport.TransportException
import com.jarvis.android.transport.live.JarvisAppSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

@Serializable
data class AppUpdateManifest(
    val schema: String = "",
    val available: Boolean = false,
    val version_code: Long = 0,
    val version_name: String = "",
    val package_name: String = "",
    val sha256: String = "",
    val size_bytes: Long = 0,
    val download_path: String = "",
    val published_utc: String? = null,
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(val versionName: String) : AppUpdateState
    data class Available(val manifest: AppUpdateManifest) : AppUpdateState
    data class Downloading(val manifest: AppUpdateManifest, val percent: Int) : AppUpdateState
    data class ReadyToInstall(val manifest: AppUpdateManifest, val apk: File) : AppUpdateState
    data class PermissionRequired(val manifest: AppUpdateManifest, val apk: File) : AppUpdateState
    data class Installing(val versionName: String) : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}

class AppUpdateManager(
    context: Context,
    private val session: JarvisAppSession,
    private val client: OkHttpClient = JarvisAppSession.defaultClient(),
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val updatesDir = File(appContext.cacheDir, "updates")

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    suspend fun checkForUpdate() = withContext(Dispatchers.IO) {
        _state.value = AppUpdateState.Checking
        runCatching {
            val manifest = fetchManifest()
            validateManifest(manifest)
            if (manifest.version_code <= currentVersionCode()) {
                _state.value = AppUpdateState.UpToDate(BuildConfig.VERSION_NAME)
            } else {
                _state.value = AppUpdateState.Available(manifest)
            }
        }.onFailure { error ->
            _state.value = AppUpdateState.Error(error.message ?: "Could not check for updates.")
        }
    }

    suspend fun downloadUpdate(manifest: AppUpdateManifest) = withContext(Dispatchers.IO) {
        runCatching {
            validateManifest(manifest)
            if (manifest.version_code <= currentVersionCode()) {
                _state.value = AppUpdateState.UpToDate(BuildConfig.VERSION_NAME)
                return@runCatching
            }

            updatesDir.mkdirs()
            val temp = File(updatesDir, "jarvis-update.apk.part")
            val target = File(updatesDir, "jarvis-update-${manifest.version_code}.apk")
            temp.delete()
            target.delete()

            val request = authenticatedRequest(session.baseUrl + manifest.download_path).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw TransportException("update download HTTP ${response.code}")
                }
                val body = response.body ?: throw TransportException("empty update body")
                val expectedSize = manifest.size_bytes
                if (expectedSize <= 0L || expectedSize > MAX_APK_BYTES) {
                    throw TransportException("invalid update size")
                }
                val headerLength = body.contentLength()
                if (headerLength > 0 && headerLength != expectedSize) {
                    throw TransportException("update size does not match manifest")
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var copied = 0L
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            copied += count
                            if (copied > expectedSize || copied > MAX_APK_BYTES) {
                                throw TransportException("update exceeded expected size")
                            }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            val percent = ((copied * 100L) / expectedSize).toInt().coerceIn(0, 100)
                            _state.value = AppUpdateState.Downloading(manifest, percent)
                        }
                        output.fd.sync()
                    }
                }

                if (copied != expectedSize) {
                    throw TransportException("update size mismatch")
                }
                val actualSha = digest.digest().toHex()
                if (!actualSha.equals(manifest.sha256, ignoreCase = true)) {
                    throw TransportException("update SHA-256 verification failed")
                }
            }

            if (!temp.renameTo(target)) {
                throw TransportException("could not finalize update file")
            }

            verifyArchive(target, manifest)
            _state.value = if (canInstallPackages()) {
                AppUpdateState.ReadyToInstall(manifest, target)
            } else {
                AppUpdateState.PermissionRequired(manifest, target)
            }
        }.onFailure { error ->
            _state.value = AppUpdateState.Error(error.message ?: "Update download failed.")
        }
    }

    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || canInstallPackages()) {
            val current = _state.value
            if (current is AppUpdateState.PermissionRequired) {
                _state.value = AppUpdateState.ReadyToInstall(current.manifest, current.apk)
            }
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${appContext.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    fun refreshInstallPermission() {
        val current = _state.value
        if (current is AppUpdateState.PermissionRequired && canInstallPackages()) {
            _state.value = AppUpdateState.ReadyToInstall(current.manifest, current.apk)
        }
    }

    fun installDownloaded(manifest: AppUpdateManifest, apk: File) {
        runCatching {
            if (!apk.isFile) throw TransportException("downloaded update is missing")
            if (!canInstallPackages()) {
                _state.value = AppUpdateState.PermissionRequired(manifest, apk)
                return
            }
            verifyArchive(apk, manifest)
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            _state.value = AppUpdateState.Installing(manifest.version_name)
            appContext.startActivity(intent)
        }.onFailure { error ->
            _state.value = AppUpdateState.Error(error.message ?: "Could not open Android installer.")
        }
    }

    private fun fetchManifest(): AppUpdateManifest {
        val base = session.baseUrl
        if (!session.isAuthenticated) throw TransportException("sign in to JARVIS before checking updates")
        if (!JarvisAppSession.isAllowedLiveHost(base)) {
            throw TransportException("update server is not on the private JARVIS network")
        }
        val request = authenticatedRequest(base + MANIFEST_PATH).build()
        return client.newCall(request).execute().use { response ->
            if (response.code == 404) throw TransportException("no JARVIS update is published")
            if (!response.isSuccessful) throw TransportException("update check HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            json.decodeFromString(AppUpdateManifest.serializer(), body)
        }
    }

    private fun authenticatedRequest(url: String): Request.Builder {
        val auth = session.authHeader() ?: throw TransportException("JARVIS session is not authenticated")
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json, application/vnd.android.package-archive")
            .header("Authorization", auth)
            .header("Cache-Control", "no-store")
    }

    private fun validateManifest(manifest: AppUpdateManifest) {
        if (manifest.schema != SCHEMA || !manifest.available) {
            throw TransportException("invalid update manifest")
        }
        if (manifest.version_code <= 0 || manifest.version_name.isBlank()) {
            throw TransportException("invalid update version")
        }
        if (manifest.package_name != appContext.packageName) {
            throw TransportException("update package does not match this JARVIS app")
        }
        if (!UpdatePolicy.safeDownloadPath(manifest.download_path)) {
            throw TransportException("unsafe update download path")
        }
        if (!manifest.sha256.matches(Regex("^[0-9a-fA-F]{64}$"))) {
            throw TransportException("invalid update SHA-256")
        }
        if (manifest.size_bytes <= 0 || manifest.size_bytes > MAX_APK_BYTES) {
            throw TransportException("invalid update size")
        }
    }

    private fun verifyArchive(apk: File, manifest: AppUpdateManifest) {
        val archive = packageInfoForArchive(apk)
            ?: throw TransportException("Android could not parse downloaded APK")
        if (archive.packageName != appContext.packageName) {
            throw TransportException("downloaded APK package mismatch")
        }
        if (versionCodeOf(archive) != manifest.version_code) {
            throw TransportException("downloaded APK version mismatch")
        }

        val installed = installedPackageInfo()
        val currentSigners = signerDigests(installed)
        val updateSigners = signerDigests(archive)
        if (currentSigners.isEmpty() || updateSigners.isEmpty() || currentSigners != updateSigners) {
            throw TransportException("downloaded APK signing certificate mismatch")
        }
    }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return appContext.packageManager.getPackageInfo(appContext.packageName, flags)
    }

    @Suppress("DEPRECATION")
    private fun packageInfoForArchive(apk: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return appContext.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            info.signatures?.toList().orEmpty()
        }
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHex()
        }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else info.versionCode.toLong()

    private fun currentVersionCode(): Long = BuildConfig.VERSION_CODE.toLong()

    private fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            appContext.packageManager.canRequestPackageInstalls()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val SCHEMA = "jarvis.android.update.v1"
        private const val MANIFEST_PATH = "/api/app/update"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val MAX_APK_BYTES = 200L * 1024L * 1024L
    }
}

object UpdatePolicy {
    fun safeDownloadPath(path: String): Boolean =
        path.startsWith("/api/app/update/") &&
            !path.startsWith("//") &&
            !path.contains("..") &&
            !path.contains('?') &&
            !path.contains('#')
}
