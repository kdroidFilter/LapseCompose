import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import dev.nucleusframework.nativehttp.NativeHttpClient
import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.UpdateResult
import dev.nucleusframework.updater.provider.GitHubProvider
import java.io.File

private const val UPDATE_OWNER = "kdroidFilter"
private const val UPDATE_REPO = "LapseCompose"

/**
 * Silent updater: one GitHub check at startup, then a background download.
 * Once the installer is on disk the tray grows a badge and an "Update now"
 * item; ignoring it installs the update when Lapse quits.
 */
@Stable
class DesktopUpdate internal constructor(
    private val updater: NucleusUpdater,
    private val downloaded: MutableState<File?>,
) {
    /** True once the installer is downloaded and waiting. */
    val ready: Boolean get() = downloaded.value != null

    fun installAndRestart() {
        downloaded.value?.let(updater::installAndRestart)
    }

    /** Hands the installer to the OS on the way out. No-op when nothing is pending. */
    fun installOnQuit() {
        downloaded.value?.let(updater::installAndQuit)
    }

    internal suspend fun check() {
        if (!updater.isUpdateSupported()) return
        val available = updater.checkForUpdates() as? UpdateResult.Available ?: return
        updater.downloadUpdate(available.info).collect { progress ->
            progress.file?.let { downloaded.value = it }
        }
    }
}

@Composable
fun rememberDesktopUpdate(): DesktopUpdate {
    val update = remember {
        DesktopUpdate(
            NucleusUpdater {
                provider = GitHubProvider(owner = UPDATE_OWNER, repo = UPDATE_REPO)
                // The GraalVM image ships no JDK trust store; native SSL uses the OS one.
                httpClient = NativeHttpClient.create()
            },
            mutableStateOf(null),
        )
    }
    // An offline machine or a rate-limited API must not take the app down with it.
    LaunchedEffect(update) { runCatching { update.check() }.onFailure { it.printStackTrace() } }
    return update
}
