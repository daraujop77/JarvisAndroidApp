package com.jarvis.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.transport.fake.FakeScenario
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.shared.OwnerAvatar
import com.jarvis.android.ui.theme.AnimationIntensity
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisVisualSystem
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.UiDensity
import com.jarvis.android.ui.theme.jarvisTextFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: JarvisViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val health by vm.healthStatus.collectAsStateWithLifecycle()
    val avatarEpoch by vm.avatarEpoch.collectAsStateWithLifecycle()
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { vm.setAvatar(it) } }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color(0xFFE8EEF8),
                    navigationIconContentColor = Color(0xFFE8EEF8),
                    actionIconContentColor = Color(0xFFE8EEF8),
                ),
                windowInsets = WindowInsets(0),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCard("IDENTITY") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    OwnerAvatar(store = vm.attachmentStore, epoch = avatarEpoch, size = 64.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (settings.hasAvatar) "Profile photo set" else "No profile photo",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "Shown next to your messages. Stored only on this phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }) { Text("Choose photo") }
                    if (settings.hasAvatar) {
                        OutlinedButton(onClick = vm::clearAvatar) { Text("Remove") }
                    }
                }
                Spacer(Modifier.height(14.dp))
                SwitchRow(
                    title = "This device is the OWNER",
                    subtitle = "Only the owner can see and resolve PC-action approvals",
                    checked = settings.isOwner,
                    onChange = vm::setIsOwner,
                )
            }

            SettingsCard("SECURITY") {
                SwitchRow(
                    title = "Require unlock",
                    subtitle = "Ask for biometrics or device PIN each time the app opens",
                    checked = settings.appLockEnabled,
                    onChange = vm::setAppLock,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "DEVICE  ${settings.deviceId?.uppercase() ?: "NOT PROVISIONED"}",
                    style = HudTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "The private key stays in the Android Keystore and is never exported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = vm::unpair) { Text("Revoke & re-pair") }
            }

            SettingsCard("APPEARANCE") {
                SwitchRow(
                    title = "Reduce motion",
                    subtitle = "Turn off ambient glow, pulses and the boot animation",
                    checked = settings.reducedMotion,
                    onChange = vm::setReducedMotion,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Animation intensity. Stored only on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                val intensity = JarvisVisualSystem.intensityOf(settings.animationIntensity)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = intensity == AnimationIntensity.SUBTLE,
                        onClick = { vm.setAnimationIntensity(JarvisVisualSystem.persist(AnimationIntensity.SUBTLE)) },
                        enabled = !settings.reducedMotion,
                        label = { Text("Subtle") },
                    )
                    FilterChip(
                        selected = intensity == AnimationIntensity.STANDARD,
                        onClick = { vm.setAnimationIntensity(JarvisVisualSystem.persist(AnimationIntensity.STANDARD)) },
                        enabled = !settings.reducedMotion,
                        label = { Text("Standard") },
                    )
                }
                if (settings.reducedMotion) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Reduced motion collapses every animation regardless of intensity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Density. Stored only on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                val density = JarvisVisualSystem.densityOf(settings.uiDensity)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = density == UiDensity.COMPACT,
                        onClick = { vm.setUiDensity(JarvisVisualSystem.persist(UiDensity.COMPACT)) },
                        label = { Text("Compact") },
                    )
                    FilterChip(
                        selected = density == UiDensity.COMFORTABLE,
                        onClick = { vm.setUiDensity(JarvisVisualSystem.persist(UiDensity.COMFORTABLE)) },
                        label = { Text("Comfortable") },
                    )
                }
                Spacer(Modifier.height(10.dp))
                var name by remember(settings.ownerName) { mutableStateOf(settings.ownerName) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Greeting name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = jarvisTextFieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.setOwnerName(name) }) { Text("Save name") }
            }

            SettingsCard("VOICE") {
                SwitchRow(
                    title = "Voice input",
                    subtitle = "Push-to-talk. Transcript is reviewed before send. No audio is stored.",
                    checked = settings.voiceInputEnabled,
                    onChange = vm::setVoiceInputEnabled,
                )
                SwitchRow(
                    title = "Prefer on-device recognition",
                    subtitle = "Network recognition stays off. If the device cannot recognize offline, voice input errors instead of uploading audio.",
                    checked = settings.preferOnDeviceRecognition,
                    onChange = vm::setPreferOnDeviceRecognition,
                )
                SwitchRow(
                    title = "Read Jarvis replies aloud",
                    subtitle = "Off by default. Speaks completed assistant replies only.",
                    checked = settings.readRepliesAloud,
                    onChange = vm::setReadRepliesAloud,
                )
                if (settings.readRepliesAloud) {
                    Text(
                        "TTS rate ${"%.1f".format(settings.ttsRate)}x",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    androidx.compose.material3.Slider(
                        value = settings.ttsRate,
                        onValueChange = vm::setTtsRate,
                        valueRange = 0.5f..2f,
                    )
                }
            }

            SettingsCard("CONNECTION") {
                if (vm.developerOptionsEnabled) {
                    SwitchRow(
                        title = "Use Fake Gateway",
                        subtitle = "Deterministic local scenarios — no network. Off = HTTP /api/v1",
                        checked = settings.useFakeGateway,
                        onChange = vm::setUseFake,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Restart the app to apply a transport change.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                var url by remember(settings.gatewayBaseUrl) { mutableStateOf(settings.gatewayBaseUrl) }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Gateway base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = jarvisTextFieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { vm.setBaseUrl(url) },
                    ) { Text("Save URL") }
                    Button(onClick = vm::checkHealth) { Text("Check health") }
                }
                health?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (vm.developerOptionsEnabled) {
                SettingsCard("SIMULATION") {
                    Text(
                        "Drives the deterministic scenario matrix without a live backend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = vm.fakeScenario.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Scenario") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            shape = RoundedCornerShape(14.dp),
                            colors = jarvisTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            containerColor = Color(0xFF0E1626),
                        ) {
                            FakeScenario.entries.forEach { sc ->
                                DropdownMenuItem(
                                    text = { Text(sc.label, color = Color(0xFFE8EEF8)) },
                                    onClick = { vm.applyFakeScenario(sc); expanded = false },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = vm::runFakeScenarioNow, modifier = Modifier.fillMaxWidth()) {
                        Text("Run scenario now")
                    }
                }

                DiagnosticsCard(vm, snapshot.session.diagnostics)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    val accents = LocalJarvisAccents.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = HudTextStyle, color = accents.orbGlow)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color(0xFFE8EEF8))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB7C7DC),
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
