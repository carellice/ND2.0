package com.nd2.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.nd2.app.core.Channels

enum class Screen { Home, PickContacts, PickApps, Settings }

class MainActivity : ComponentActivity() {

    /** Incrementato a ogni ritorno nell'app, per ricontrollare i permessi concessi nelle Impostazioni. */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Nd2Theme {
                var screen by rememberSaveable { mutableStateOf(Screen.Home) }
                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
                when (screen) {
                    Screen.Home -> HomeScreen(
                        resumeTick = resumeTick,
                        onPickContacts = { screen = Screen.PickContacts },
                        onPickApps = { screen = Screen.PickApps },
                        onOpenSettings = { screen = Screen.Settings },
                    )
                    Screen.PickContacts -> ContactPickerScreen(onDone = { screen = Screen.Home })
                    Screen.PickApps -> AppPickerScreen(onDone = { screen = Screen.Home })
                    Screen.Settings -> SettingsScreen(resumeTick = resumeTick, onBack = { screen = Screen.Home })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Channels.ensure(this)
        resumeTick++
    }
}
