package com.gateway.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onRequestIgnoreBatteryOptimizations: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingTong by remember { mutableStateOf<TongConfig?>(null) }

    // Dialog for adding and editing alarm checklist steps (tongs)
    if (showAddDialog || editingTong != null) {
        val isEdit = editingTong != null
        var stepInput by remember { mutableStateOf(if (isEdit) editingTong!!.step.toString() else "") }
        var titleInput by remember { mutableStateOf(if (isEdit) editingTong!!.title else "") }
        var subtitleInput by remember { mutableStateOf(if (isEdit) editingTong!!.subtitle else "") }
        var selectedAlarmType by remember { mutableStateOf(if (isEdit) editingTong!!.type else AudioAlertManager.AlarmType.DIGITAL_BEEP) }
        var durationInput by remember { mutableStateOf(if (isEdit) editingTong!!.durationSeconds.toString() else "10") }
        var volumeInput by remember { mutableStateOf(if (isEdit) (editingTong!!.volumePercent ?: 100).toString() else "100") }

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingTong = null
            },
            title = {
                Text(
                    text = if (isEdit) "ধাপ এডিট করুন ✏️" else "নতুন ধাপ যুক্ত করুন ➕",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 18.sp
                )
            },
            containerColor = Color(0xFF1E1E24),
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = stepInput,
                        onValueChange = { stepInput = it },
                        label = { Text("ধাপ নম্বর (Step Index)", color = Color.LightGray) },
                        placeholder = { Text("যেমন: 5", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !isEdit, // Index cannot be changed once created
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFC5A059),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("ধাপের শিরোনাম (Title)", color = Color.LightGray) },
                        placeholder = { Text("যেমন: Step 5: OTP Verified", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFC5A059),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = subtitleInput,
                        onValueChange = { subtitleInput = it },
                        label = { Text("ধাপের বর্ণনা (Subtitle)", color = Color.LightGray) },
                        placeholder = { Text("যেমন: Customer successfully verify code", color = Color.Gray) },
                        singleLine = false,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFC5A059),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = durationInput,
                        onValueChange = { durationInput = it },
                        label = { Text("অ্যালার্মের সময়সীমা (Duration in seconds)", color = Color.LightGray) },
                        placeholder = { Text("যেমন: 10", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFC5A059),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = volumeInput,
                        onValueChange = { volumeInput = it },
                        label = { Text("ভলিউম কতো শতাংশ (Volume: 0-100%)", color = Color.LightGray) },
                        placeholder = { Text("যেমন: 100", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFC5A059),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("সাউন্ড অ্যালার্ম নির্বাচন করুন:", fontSize = 12.sp, color = Color.LightGray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AudioAlertManager.AlarmType.values().forEach { type ->
                            val isSelected = selectedAlarmType == type
                            Button(
                                onClick = { selectedAlarmType = type },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) Color(0xFFC5A059) else Color(0xFF2C2C35)
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = type.name.replace("_", " "),
                                    fontSize = 9.sp,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val step = stepInput.toIntOrNull() ?: 0
                        val duration = durationInput.toIntOrNull() ?: 10
                        val volume = volumeInput.toIntOrNull()?.coerceIn(0, 100) ?: 100
                        if (isEdit) {
                            viewModel.editTong(step, titleInput, subtitleInput, selectedAlarmType, duration, volume)
                            editingTong = null
                        } else {
                            viewModel.addTong(step, titleInput, subtitleInput, selectedAlarmType, duration, volume)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059)),
                    enabled = titleInput.isNotBlank() && (stepInput.isNotBlank() || isEdit)
                ) {
                    Text("সংরক্ষণ করুন", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showAddDialog = false
                        editingTong = null
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                ) {
                    Text("বাতিল")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Gateway Admin Monitor 📡", 
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC5A059)
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121215)
                )
            )
        },
        modifier = modifier,
        containerColor = Color(0xFF0F0F12) // Dark background for Dark Mode
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFF0F0F12))
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.deviceStatus != "approved") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.deviceStatus == "blocked") Color(0xFF3E1F1F) else Color(0xFF16161B)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (state.deviceStatus == "blocked") "ডিভাইস ব্লকড ⛔" else "অ্যাক্টিভেশন প্রয়োজন 🔐",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = if (state.deviceStatus == "blocked") Color(0xFFEF5350) else Color(0xFFC5A059)
                        )

                        Text(
                            text = if (state.deviceStatus == "blocked") {
                                "আপনার এই ডিভাইসটি অ্যাডমিন ব্লক করেছেন! কোনো লাইভ ট্র্যাকিং পোলিং বা ব্যাকগ্রাউন্ড সার্ভিস চালু করা সম্ভব নয়।"
                            } else {
                                "এটি একটি নতুন অননুমোদিত ডিভাইস। এই মনিটর অ্যাপটি সচল করার জন্য মেইন অ্যাডমিনের কাছ থেকে লাইসেন্স কী (Serial Key) দিয়ে ডিভাইস এক্টিভেট করুন।"
                            },
                            fontSize = 13.sp,
                            color = Color.LightGray,
                            lineHeight = 18.sp
                        )

                        OutlinedTextField(
                            value = state.baseUrl,
                            onValueChange = { viewModel.updateBaseUrl(it) },
                            label = { Text("Gateway Web Server API Url", color = Color.Gray) },
                            placeholder = { Text("https://your-domain.run.app") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFC5A059),
                                unfocusedBorderColor = Color.Gray
                            )
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF26262B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("ডিভাইসের বিবরণী:", fontSize = 11.sp, color = Color.LightGray, fontWeight = FontWeight.Bold)
                                Text("ডিভাইস আইডি (ID): ${state.deviceId}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                Text("মডেল (Name): ${state.deviceName}", fontSize = 11.sp, color = Color.Gray)
                            }
                        }

                        if (state.deviceStatus != "blocked") {
                            var activationKeyInput by remember { mutableStateOf("") }
                            
                            OutlinedTextField(
                                value = activationKeyInput,
                                onValueChange = { activationKeyInput = it },
                                label = { Text("লাইসেন্স অ্যাক্টিভেশন কী", color = Color.Gray) },
                                placeholder = { Text("RING-XXXX-XXXX-XXXX") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFC5A059),
                                    unfocusedBorderColor = Color.Gray
                                )
                            )

                            if (state.activationError != null) {
                                Text(
                                    text = state.activationError ?: "",
                                    color = Color(0xFFEF5350),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.align(Alignment.Start)
                                )
                            }

                            Button(
                                onClick = { viewModel.activateDeviceWithKey(activationKeyInput) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059)),
                                enabled = !state.isCheckingStatus && state.baseUrl.isNotBlank()
                            ) {
                                if (state.isCheckingStatus) {
                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                                } else {
                                    Text("এক্টিভেট করুন", fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.checkDeviceStatusFromServer() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC5A059)),
                            enabled = !state.isCheckingStatus && state.baseUrl.isNotBlank()
                        ) {
                            Text("পুনরায় যাচাই করুন")
                        }
                    }
                }
            } else {
                // Service Status Manager Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.isServiceRunning) Color(0xFF142B1B) else Color(0xFF331515)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.isServiceRunning) "Foreground Active 🟢" else "Service Inactive 🛑",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = if (state.isServiceRunning) Color(0xFF81C784) else Color(0xFFEF5350)
                            )
                            Text(
                                text = if (state.isServiceRunning) "Alarms will ring even if screen is locked." else "Activate polling background service.",
                                fontSize = 12.sp,
                                color = Color.LightGray
                            )
                        }
                        Switch(
                            checked = state.isServiceRunning,
                            onCheckedChange = { viewModel.toggleService(it) }
                        )
                    }
                }

                // Connection Configuration Settings
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16161B))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Server Sync Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)

                        OutlinedTextField(
                            value = state.baseUrl,
                            onValueChange = { viewModel.updateBaseUrl(it) },
                            label = { Text("Gateway Web Server API Url", color = Color.Gray) },
                            placeholder = { Text("https://your-domain.run.app") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFC5A059),
                                unfocusedBorderColor = Color.Gray
                            )
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = state.pollingInterval.toString(),
                                onValueChange = { 
                                    val value = it.toIntOrNull() ?: 5
                                    viewModel.updatePollingInterval(value)
                                },
                                label = { Text("Poll Sec", color = Color.Gray) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFC5A059),
                                    unfocusedBorderColor = Color.Gray
                                )
                            )

                            OutlinedTextField(
                                value = state.alarmDuration.toString(),
                                onValueChange = { 
                                    val value = it.toIntOrNull() ?: 10
                                    viewModel.updateAlarmDuration(value)
                                },
                                label = { Text("Alarm Sec", color = Color.Gray) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFC5A059),
                                    unfocusedBorderColor = Color.Gray
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF26262B)))
                        Spacer(modifier = Modifier.height(4.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Connection Status",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.Gray
                                )
                                val isError = state.lastSyncStatus.startsWith("Error", ignoreCase = true)
                                val isSuccess = state.lastSyncStatus.contains("success", ignoreCase = true)
                                val statusColor = when {
                                    isSuccess -> Color(0xFF81C784)
                                    isError -> Color(0xFFEF5350)
                                    else -> Color(0xFF9E9EAA)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(statusColor, shape = androidx.compose.foundation.shape.CircleShape)
                                )
                            }
                            Text(
                                text = state.lastSyncStatus,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (state.lastSyncStatus.startsWith("Error", ignoreCase = true)) Color(0xFFEF5350) else Color.White
                            )
                            Text(
                                text = "Last Synced: ${state.lastSyncTime}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // Battery Optimizer / Wake Lock Whitelister
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2215)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = "Battery Config Info", tint = Color(0xFFFFB74D))
                            Text("Hold Static Wake Lock", fontWeight = FontWeight.Bold, color = Color(0xFFFFB74D))
                        }
                        Text(
                            "Whitelist the monitor from Doze/Battery optimization to ensure real-time alarms ring instantly when sleeping.",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                        Button(
                            onClick = onRequestIgnoreBatteryOptimizations,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Disable Optimization", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Header for Checklist Steps with + Add Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Trigger Alarms by Checklist step",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFFC5A059)
                    )
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33333D)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("➕ Add Step", fontSize = 12.sp, color = Color.White)
                    }
                }

                state.tongs.forEach { tong ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161B))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        tong.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        tong.subtitle,
                                        fontSize = 11.sp,
                                        color = Color.LightGray
                                    )
                                    Text(
                                        "সময়সীমা: ${tong.durationSeconds} সেকেন্ড | ভলিউম: ${tong.getSafeVolume()}%",
                                        fontSize = 11.sp,
                                        color = Color(0xFFC5A059),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Switch(
                                        checked = tong.isActive,
                                        onCheckedChange = { viewModel.toggleTong(tong.step, it) }
                                    )
                                }
                            }

                            if (tong.isActive) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    var expanded by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedButton(
                                            onClick = { expanded = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                        ) {
                                            Text(tong.type.name.replace("_", " "))
                                        }
                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            modifier = Modifier.background(Color(0xFF1E1E24))
                                        ) {
                                            AudioAlertManager.AlarmType.values().forEach { alarmType ->
                                                DropdownMenuItem(
                                                    text = { Text(alarmType.name.replace("_", " "), color = Color.White) },
                                                    onClick = {
                                                        viewModel.updateTongType(tong.step, alarmType)
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = { viewModel.testSound(tong.type) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFC5A059)
                                        )
                                    ) {
                                        Text("🔊 Test", color = Color.Black)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.stopTestSound() },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                    ) {
                                        Text("⏹️")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { editingTong = tong },
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("✏️ Edit (এডিট)", fontSize = 12.sp, color = Color(0xFF64B5F6))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(
                                        onClick = { viewModel.deleteTong(tong.step) },
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("🗑️ Delete (মুছুন)", fontSize = 12.sp, color = Color(0xFFEF5350))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
