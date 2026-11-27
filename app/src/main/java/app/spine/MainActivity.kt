package app.spine

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.spine.library.LibraryViewModel
import app.spine.reader.ReaderActivity
import app.spine.ui.LibraryScreen

class MainActivity : ComponentActivity() {
    private val vm: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LibraryScreen(
                items = vm.items.collectAsStateWithLifecycle().value,
                collections = vm.collections.collectAsStateWithLifecycle().value,
                status = vm.status.collectAsStateWithLifecycle().value,
                hasAccess = vm.hasAccess.collectAsStateWithLifecycle().value,
                onGrantAccess = {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                },
                onRescan = vm::rescan,
                onOpen = { book -> startActivity(Intent(this, ReaderActivity::class.java).setData(Uri.parse(book.uri))) },
                onGroup = vm::group,
                onUngroup = vm::ungroup,
                onSort = vm::setSort,
                onMove = vm::move,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        vm.recheckAccess()
        vm.refreshProgress()
    }
}
