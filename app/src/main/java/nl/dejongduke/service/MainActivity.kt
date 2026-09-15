package nl.dejongduke.service

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.dejongduke.service.data.Locales
import nl.dejongduke.service.ui.AppShell
import nl.dejongduke.service.ui.AppViewModel
import nl.dejongduke.service.ui.theme.DukeTheme
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {

    /** Android hands out resources before onCreate, so the chosen language is
     *  applied here — everything the activity shows then comes out translated. */
    override fun attachBaseContext(base: Context) = super.attachBaseContext(Locales.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            val theme by vm.theme.collectAsStateWithLifecycle()
            val language by vm.language.collectAsStateWithLifecycle()
            // The resources were picked at attachBaseContext; changing the
            // language therefore means starting the activity over. The view
            // model — and with it the open screen — survives that.
            val started = remember { language }
            LaunchedEffect(language) { if (language != started) recreate() }
            DukeTheme(theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppShell(vm)
                }
            }
        }
    }
}
