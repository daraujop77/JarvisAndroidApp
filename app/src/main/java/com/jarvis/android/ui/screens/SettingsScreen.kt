package com.jarvis.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.BuildConfig
import com.jarvis.android.transport.fake.FakeScenario
import com.jarvis.android.update.AppUpdateState
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.i18n.LocalAppStrings
import com.jarvis.android.ui.shared.OwnerAvatar
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.jarvisTextFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: JarvisViewModel,
    onOpenUsage: () -> Unit = {},
) {
    val strings = LocalAppStrings.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val health by vm.healthStatus.collectAsStateWithLifecycle()
    val avatarEpoch by vm.avatarEpoch.collectAsStateWithLifecycle()
    val appUpdate by vm.appUpdateState.collectAsStateWithLifecycle()
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { vm.setAvatar(it) } }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle) },
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
            SettingsCard(strings.languageSection) {
                Text(
                    strings.languageSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                val languageOptions = listOf(
                    "system" to strings.langSystem,
                    "en" to strings.langEnglish,
                    "es" to strings.langSpanish,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    languageOptions.forEach { (code, label) ->
                        val isSelected = settings.appLanguage.equals(code, ignoreCase = true)
                        Surface(
                            onClick = { vm.setAppLanguage(code) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0x3322D3EE) else Color(0x18101B2E),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) LocalJarvisAccents.current.orbGlow.copy(alpha = 0.65f)
                                else Color(0x2222D3EE),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { vm.setAppLanguage(code) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = LocalJarvisAccents.current.orbGlow,
                                        unselectedColor = Color(0xFF8BA2BE),
                                    ),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                    color = if (isSelected) Color(0xFFE8EEF8) else Color(0xFFB7C7DC),
                                )
                            }
                        }
                    }
                }
            }

            SettingsCard(strings.identitySection) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    OwnerAvatar(store = vm.attachmentStore, epoch = avatarEpoch, size = 64.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (settings.hasAvatar) strings.profilePhotoSet else strings.noProfilePhoto,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            strings.photoDescription,
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
                    }) { Text(strings.choosePhoto) }
                    if (settings.hasAvatar) {
                        OutlinedButton(onClick = vm::clearAvatar) { Text(strings.removePhoto) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                SwitchRow(
                    title = strings.deviceIsOwner,
                    subtitle = strings.deviceIsOwnerSubtitle,
                    checked = settings.isOwner,
                    onChange = vm::setIsOwner,
                )
            }

            SettingsCard(strings.securitySection) {
                SwitchRow(
                    title = strings.requireUnlock,
                    subtitle = strings.requireUnlockSubtitle,
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
                    strings.deviceKeyNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = vm::unpair) { Text(strings.revokeAndRepair) }
            }

            SettingsCard(strings.floatingBrainSection) {
                val bubbleGranted = com.jarvis.android.overlay.FloatingBubbleService.canDraw(
                    androidx.compose.ui.platform.LocalContext.current,
                )
                SwitchRow(
                    title = strings.floatingBubble,
                    subtitle = if (bubbleGranted) strings.floatingBubbleGrantedSubtitle else strings.floatingBubbleNeedsPermissionSubtitle,
                    checked = settings.floatingBubbleEnabled && bubbleGranted,
                    onChange = { want ->
                        if (want && !bubbleGranted) vm.requestOverlayPermission()
                        else vm.setFloatingBubble(want)
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (com.jarvis.android.overlay.ScreenVisionService.isEnabled) strings.screenVisionOn else strings.screenVisionOff,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard(strings.appearanceSection) {
                SwitchRow(
                    title = strings.reduceMotion,
                    subtitle = strings.reduceMotionSubtitle,
                    checked = settings.reducedMotion,
                    onChange = vm::setReducedMotion,
                )
                Spacer(Modifier.height(10.dp))
                var name by remember(settings.ownerName) { mutableStateOf(settings.ownerName) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.greetingName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = jarvisTextFieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.setOwnerName(name) }) { Text(strings.saveName) }
            }


            SettingsCard(strings.usageSection) {
                Text(
                    strings.usageSettingsDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onOpenUsage) { Text(strings.openUsage) }
            }

            SettingsCard(strings.appUpdateSection) {
                Text(
                    strings.installedVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFE8EEF8),
                )
                Spacer(Modifier.height(8.dp))
                when (val update = appUpdate) {
                    AppUpdateState.Idle -> {
                        Text(
                            strings.updatesDeliveredNotice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = vm::checkForAppUpdate) { Text(strings.checkForUpdate) }
                    }
                    AppUpdateState.Checking -> {
                        Text(strings.checkingForUpdate, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {}, enabled = false) { Text(strings.checking) }
                    }
                    is AppUpdateState.UpToDate -> {
                        Text(strings.upToDate(update.versionName), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = vm::checkForAppUpdate) { Text(strings.checkAgain) }
                    }
                    is AppUpdateState.Available -> {
                        Text(
                            strings.updateAvailable(update.manifest.version_name, update.manifest.version_code),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = vm::downloadAppUpdate) { Text(strings.downloadUpdate) }
                    }
                    is AppUpdateState.Downloading -> {
                        Text(
                            strings.downloading(update.manifest.version_name, update.percent),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {}, enabled = false) { Text(strings.checking) }
                    }
                    is AppUpdateState.ReadyToInstall -> {
                        Text(
                            strings.downloadVerified,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = vm::installDownloadedAppUpdate) { Text(strings.installUpdate) }
                    }
                    is AppUpdateState.PermissionRequired -> {
                        Text(
                            strings.permissionRequiredNotice,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = vm::requestAppUpdateInstallPermission) { Text(strings.allowInstalls) }
                            OutlinedButton(onClick = vm::installDownloadedAppUpdate) { Text(strings.continueAction) }
                        }
                    }
                    is AppUpdateState.Installing -> {
                        Text(
                            strings.installerOpenedNotice(update.versionName),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    is AppUpdateState.Error -> {
                        Text(update.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = vm::checkForAppUpdate) { Text(strings.tryAgain) }
                    }
                }
            }

            SettingsCard(strings.connectionSection) {
                if (vm.developerOptionsEnabled) {
                    SwitchRow(
                        title = strings.useFakeGateway,
                        subtitle = strings.useFakeGatewaySubtitle,
                        checked = settings.useFakeGateway,
                        onChange = vm::setUseFake,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        strings.restartAppNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                var url by remember(settings.gatewayBaseUrl) { mutableStateOf(settings.gatewayBaseUrl) }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(strings.gatewayBaseUrl) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = jarvisTextFieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { vm.setBaseUrl(url) },
                    ) { Text(strings.saveUrl) }
                    Button(onClick = vm::checkHealth) { Text(strings.checkHealth) }
                }
                health?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (vm.developerOptionsEnabled) {
                SettingsCard(strings.simulationSection) {
                    Text(
                        strings.simulationSubtitle,
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
                            label = { Text(strings.scenario) },
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
                        Text(strings.runScenarioNow)
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
