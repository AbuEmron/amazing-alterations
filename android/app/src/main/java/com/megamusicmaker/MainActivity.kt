package com.megamusicmaker

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.megamusicmaker.audio.AudioEngine
import com.megamusicmaker.audio.ProjectStore
import com.megamusicmaker.audio.SampleLibrary
import com.megamusicmaker.ui.StudioScreen

class MainActivity : ComponentActivity() {

    private val engine = AudioEngine()
    private lateinit var library: SampleLibrary

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ProjectStore.load(this, engine)
        library = SampleLibrary(assets)
        library.loadAsync()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                StudioScreen(engine, library)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        engine.start()
    }

    override fun onStop() {
        engine.stop()
        val appContext = applicationContext
        Thread { ProjectStore.save(appContext, engine) }.start()
        super.onStop()
    }
}
