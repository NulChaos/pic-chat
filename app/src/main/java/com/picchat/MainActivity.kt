package com.picchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.picchat.ui.theme.PictoChatTheme
import com.picchat.viewmodel.ChatViewModel
import com.picchat.ui.screens.ChatScreen
import com.picchat.ui.screens.NicknamePickerScreen

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PictoChatTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Check if nickname is set (persisted in real app via SharedPreferences)
                    var showPicker by remember { mutableStateOf(viewModel.userNickname.isBlank()) }
                    
                    if (showPicker) {
                        NicknamePickerScreen(
                            nickname = viewModel.userNickname,
                            birthMonth = viewModel.birthMonth,
                            birthDay = viewModel.birthDay,
                            onNicknameChange = { viewModel.updateNickname(it) },
                            onBirthMonthChange = { viewModel.updateBirthMonth(it) },
                            onBirthDayChange = { viewModel.updateBirthDay(it) },
                            onNext = { showPicker = false }
                        )
                    } else {
                        ChatScreen(
                            viewModel = viewModel,
                            onUserNickChange = { viewModel.updateNickname(it) },
                            onSaveNickname = { /* persist to prefs in real app */ }
                        )
                    }
                }
            }
        }
    }
}
