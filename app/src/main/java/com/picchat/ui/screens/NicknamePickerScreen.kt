package com.picchat.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NicknamePickerScreen(
    nickname: String,
    birthMonth: Int,
    birthDay: Int,
    onNicknameChange: (String) -> Unit,
    onBirthMonthChange: (Int) -> Unit,
    onBirthDayChange: (Int) -> Unit,
    onNext: () -> Unit
) {
    var monthInput by remember { mutableStateOf(birthMonth.toString()) }
    var dayInput by remember { mutableStateOf(birthDay.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Welcome to PictoChat!", style = MaterialTheme.typography.headlineLarge)
        Text("Choose a nickname and enter your birthday (month & day only).", style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = nickname,
            onValueChange = onNicknameChange,
            label = { Text("Nickname") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = monthInput,
                onValueChange = { if (it.length <= 2 && it.toIntOrNull()?.let { m -> m in 1..12 } == true) { monthInput = it; onBirthMonthChange(monthInput.toInt()) } },
                label = { Text("Month") },
                modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.input.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
            OutlinedTextField(
                value = dayInput,
                onValueChange = { if (it.length <= 2 && it.toIntOrNull()?.let { d -> d in 1..31 } == true) { dayInput = it; onBirthDayChange(dayInput.toInt()) } },
                label = { Text("Day") },
                modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.input.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNext,
            enabled = nickname.isNotBlank() && birthMonth in 1..12 && birthDay in 1..31,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Join Chat")
        }
    }
}
