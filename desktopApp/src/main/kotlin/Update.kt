import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.lapse.domain.UpdateStatus
import dev.nucleusframework.nativehttp.NativeHttpClient
import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.UpdateResult
import dev.nucleusframework.updater.provider.GitHubProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

private const val UPDATE_OWNER = "kdroidFilter"
private const val UPDATE_REPO = "LapseCompose"

/**
 * Silent updater: one GitHub check at startup, then a background download.
 * Once the installer is on disk the tray grows a badge and an "Update now"
 * item; ignoring it installs the update when Lapse quits. Settings and the
 * tray can also ask for a fresh check through [checkNow].
 */
@Stable
class DesktopUpdate internal constructor(
    private val updater: NucleusUpdater,
    private val downloaded: MutableState<File?>,
    private val statusState: MutableState<UpdateStatus>,
    private val scope: CoroutineScope,
) {
    /** True while an installer is being fetched in the background. */
    val downloading: Boolean get() = statusState.value == UpdateStatus.Downloading

    /** True once the installer is downloaded and waiting. */
    val ready: Boolean get() = downloaded.value != null

    val status: UpdateStatus get() = statusState.value

    private var running = false

    fun installAndRestart() {
        downloaded.value?.let(updater::installAndRestart)
    }

    /** Hands the installer to the OS on the way out. No-op when nothing is pending. */
    fun installOnQuit() {
        downloaded.value?.let(updater::installAndQuit)
    }

    /** Manual re-check. Ignored while one is already in flight. */
    fun checkNow() {
        scope.launch { check() }
    }

    // An offline machine or a rate-limited API must not take the app down with it.
    internal suspend fun check() {
        if (running) return
        running = true
        statusState.value = UpdateStatus.Checking
        try {
            if (!updater.isUpdateSupported()) {
                statusState.value = UpdateStatus.Unsupported
                return
            }
            val available = updater.checkForUpdates() as? UpdateResult.Available
            if (available == null) {
                statusState.value = UpdateStatus.UpToDate
                return
            }
            statusState.value = UpdateStatus.Downloading
            updater.downloadUpdate(available.info).collect { progress ->
                progress.file?.let {
                    downloaded.value = it
                    statusState.value = UpdateStatus.Ready
                }
            }
            if (downloaded.value == null) statusState.value = UpdateStatus.Failed
        } catch (e: Exception) {
            e.printStackTrace()
            statusState.value = UpdateStatus.Failed
        } finally {
            running = false
        }
    }
}

@Composable
fun rememberDesktopUpdate(): DesktopUpdate {
    val scope = rememberCoroutineScope()
    val update = remember {
        DesktopUpdate(
            NucleusUpdater {
                provider = GitHubProvider(owner = UPDATE_OWNER, repo = UPDATE_REPO)
                // The GraalVM image ships no JDK trust store; native SSL uses the OS one.
                httpClient = NativeHttpClient.create()
            },
            mutableStateOf(null),
            mutableStateOf(UpdateStatus.Checking),
            scope,
        )
    }
    LaunchedEffect(update) { update.check() }
    return update
}
