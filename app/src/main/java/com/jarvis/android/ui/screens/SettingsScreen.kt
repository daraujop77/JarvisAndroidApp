package com.jarvis.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.BuildConfig
import com.jarvis.android.transport.fake.FakeScenario
import com.jarvis.android.update.AppUpdateState
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.components.JarvisOrb
import com.jarvis.android.ui.components.OrbActivity
import com.jarvis.android.ui.i18n.LocalAppStrings
import com.jarvis.android.ui.shared.OwnerAvatar
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisAmber
import com.jarvis.android.ui.theme.JarvisCyan
import com.jarvis.android.ui.theme.JarvisCyanBright
import com.jarvis.android.ui.theme.JarvisGreen
import com.jarvis.android.ui.theme.JarvisRed
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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        JarvisOrb(size = 28.dp, activity = OrbActivity.IDLE)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(strings.settingsTitle, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "SYSTEM CONFIG & TELEMETRY",
                                style = HudTextStyle,
                                color = LocalJarvisAccents.current.orbGlow,
                            )
                        }
                    }
                },
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
            SettingsCard(
                title = strings.languageSection,
                icon = Icons.Filled.Translate,
                badgeText = settings.appLanguage.uppercase(),
                badgeColor = JarvisCyan,
            ) {
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
                                if (isSelected) JarvisCyanBright.copy(alpha = 0.75f)
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
                                        selectedColor = JarvisCyan,
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
                                    modifier = Modifier.weight(1f),
                                )
                                if (isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = JarvisCyan.copy(alpha = 0.16f),
                                        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f)),
                                    ) {
                                        Text(
                                            "ACTIVE",
                                            style = HudTextStyle,
                                            color = JarvisCyan,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SettingsCard(
                title = strings.identitySection,
                icon = Icons.Filled.Person,
                badgeText = if (settings.isOwner) "OWNER" else "GUEST",
                badgeColor = if (settings.isOwner) JarvisGreen else JarvisAmber,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(1.dp, JarvisCyan.copy(alpha = 0.5f), CircleShape)
                            .padding(2.dp),
                    ) {
                        OwnerAvatar(store = vm.attachmentStore, epoch = avatarEpoch, size = 64.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (settings.hasAvatar) strings.profilePhotoSet else strings.noProfilePhoto,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFE8EEF8),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            strings.photoDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisCyan,
                            contentColor = Color(0xFF041018),
                        ),
                    ) {
                        Text(strings.choosePhoto, fontWeight = FontWeight.SemiBold)
                    }
                    if (settings.hasAvatar) {
                        OutlinedButton(
                            onClick = vm::clearAvatar,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, JarvisRed.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = JarvisRed,
                            ),
                        ) {
                            Text(strings.removePhoto, fontWeight = FontWeight.SemiBold)
                        }
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

            SettingsCard(
                title = strings.securitySection,
                icon = Icons.Filled.Security,
                badgeText = if (settings.appLockEnabled) "PROTECTED" else "UNLOCKED",
                badgeColor = if (settings.appLockEnabled) JarvisGreen else JarvisAmber,
            ) {
                SwitchRow(
                    title = strings.requireUnlock,
                    subtitle = strings.requireUnlockSubtitle,
                    checked = settings.appLockEnabled,
                    onChange = vm::setAppLock,
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF08101E),
                    border = BorderStroke(1.dp, Color(0x3322D3EE)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.VpnKey,
                            contentDescription = null,
                            tint = JarvisCyan,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "DEVICE: ${settings.deviceId?.uppercase() ?: "NOT PROVISIONED"}",
                            style = HudTextStyle,
                            color = JarvisCyan,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    strings.deviceKeyNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = vm::unpair,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, JarvisRed.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = JarvisRed,
                    ),
                ) {
                    Text(strings.revokeAndRepair, fontWeight = FontWeight.SemiBold)
                }
            }

            SettingsCard(
                title = strings.floatingBrainSection,
                icon = Icons.Filled.OpenInNew,
                badgeText = if (settings.floatingBubbleEnabled) "ACTIVE" else "OFF",
                badgeColor = if (settings.floatingBubbleEnabled) JarvisCyan else Color(0xFF8BA2BE),
            ) {
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
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF08101E),
                    border = BorderStroke(
                        1.dp,
                        if (com.jarvis.android.overlay.ScreenVisionService.isEnabled) JarvisGreen.copy(alpha = 0.35f)
                        else Color(0x3322D3EE),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (com.jarvis.android.overlay.ScreenVisionService.isEnabled) Icons.Filled.CheckCircle else Icons.Filled.Dns,
                            contentDescription = null,
                            tint = if (com.jarvis.android.overlay.ScreenVisionService.isEnabled) JarvisGreen else Color(0xFF8BA2BE),
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (com.jarvis.android.overlay.ScreenVisionService.isEnabled) strings.screenVisionOn else strings.screenVisionOff,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (com.jarvis.android.overlay.ScreenVisionService.isEnabled) JarvisGreen else Color(0xFF8BA2BE),
                        )
                    }
                }
            }

            SettingsCard(
                title = strings.appearanceSection,
                icon = Icons.Filled.Palette,
            ) {
                SwitchRow(
                    title = strings.reduceMotion,
                    subtitle = strings.reduceMotionSubtitle,
                    checked = settings.reducedMotion,
                    onChange = vm::setReducedMotion,
                )
                Spacer(Modifier.height(12.dp))
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
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.setOwnerName(name) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCyan,
                        contentColor = Color(0xFF041018),
                    ),
                ) {
                    Text(strings.saveName, fontWeight = FontWeight.SemiBold)
                }
            }

            SettingsCard(
                title = strings.usageSection,
                icon = Icons.Filled.Speed,
            ) {
                Text(
                    strings.usageSettingsDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenUsage,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10233D),
                        contentColor = JarvisCyan,
                    ),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f)),
                ) {
                    Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.openUsage, fontWeight = FontWeight.SemiBold)
                }
            }

            SettingsCard(
                title = strings.appUpdateSection,
                icon = Icons.Filled.SystemUpdate,
                badgeText = "v${BuildConfig.VERSION_CODE}",
                badgeColor = JarvisCyan,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF08101E),
                    border = BorderStroke(1.dp, Color(0x3322D3EE)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        strings.installedVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFE8EEF8),
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                when (val update = appUpdate) {
                    AppUpdateState.Idle -> {
                        Text(
                            strings.updatesDeliveredNotice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = vm::checkForAppUpdate,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color(0xFF041018)),
                        ) {
                            Text(strings.checkForUpdate, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    AppUpdateState.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = JarvisCyan)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.checkingForUpdate, style = MaterialTheme.typography.bodySmall, color = JarvisCyan)
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {}, enabled = false, shape = RoundedCornerShape(12.dp)) { Text(strings.checking) }
                    }
                    is AppUpdateState.UpToDate -> {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = JarvisGreen.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, JarvisGreen.copy(alpha = 0.5f)),
                        ) {
                            Text(
                                strings.upToDate(update.versionName),
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = vm::checkForAppUpdate,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan),
                        ) {
                            Text(strings.checkAgain, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    is AppUpdateState.Available -> {
                        Text(
                            strings.updateAvailable(update.manifest.version_name, update.manifest.version_code),
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisAmber,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = vm::downloadAppUpdate,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisGreen, contentColor = Color(0xFF041018)),
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.downloadUpdate, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    is AppUpdateState.Downloading -> {
                        Text(
                            strings.downloading(update.manifest.version_name, update.percent),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {}, enabled = false, shape = RoundedCornerShape(12.dp)) { Text(strings.checking) }
                    }
                    is AppUpdateState.ReadyToInstall -> {
                        Text(
                            strings.downloadVerified,
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisGreen,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = vm::installDownloadedAppUpdate,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisGreen, contentColor = Color(0xFF041018)),
                        ) {
                            Text(strings.installUpdate, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    is AppUpdateState.PermissionRequired -> {
                        Text(
                            strings.permissionRequiredNotice,
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisAmber,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = vm::requestAppUpdateInstallPermission,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color(0xFF041018)),
                            ) {
                                Text(strings.allowInstalls, fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = vm::installDownloadedAppUpdate,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan),
                            ) {
                                Text(strings.continueAction, fontWeight = FontWeight.SemiBold)
                            }
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
                        OutlinedButton(
                            onClick = vm::checkForAppUpdate,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(strings.tryAgain, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            SettingsCard(
                title = strings.connectionSection,
                icon = Icons.Filled.Wifi,
            ) {
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
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { vm.setBaseUrl(url) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan),
                    ) {
                        Text(strings.saveUrl, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = vm::checkHealth,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color(0xFF041018)),
                    ) {
                        Text(strings.checkHealth, fontWeight = FontWeight.SemiBold)
                    }
                }
                health?.let {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF08101E),
                        border = BorderStroke(1.dp, Color(0x3322D3EE)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            it,
                            style = HudTextStyle,
                            color = JarvisCyan,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }

            if (vm.developerOptionsEnabled) {
                SettingsCard(
                    title = strings.simulationSection,
                    icon = Icons.Filled.Science,
                ) {
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
                    Button(
                        onClick = vm::runFakeScenarioNow,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color(0xFF041018)),
                    ) {
                        Text(strings.runScenarioNow, fontWeight = FontWeight.SemiBold)
                    }
                }

                DiagnosticsCard(vm, snapshot.session.diagnostics)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector? = null,
    badgeText: String? = null,
    badgeColor: Color = JarvisCyan,
    content: @Composable () -> Unit,
) {
    val accents = LocalJarvisAccents.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xCC0E182A),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    accents.orbGlow.copy(alpha = 0.28f),
                    Color(0x228B5CF6),
                    accents.orbGlow.copy(alpha = 0.18f),
                ),
            ),
        ),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                accents.orbGlow.copy(alpha = 0.8f),
                                Color(0x338B5CF6),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(accents.orbGlow.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = accents.orbGlow,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            title.uppercase(),
                            style = HudTextStyle.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                            color = accents.orbGlow,
                        )
                    }

                    if (badgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
                        ) {
                            Text(
                                badgeText.uppercase(),
                                style = HudTextStyle,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                content()
            }
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
    val accents = LocalJarvisAccents.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color(0xFFE8EEF8))
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8BA2BE),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF041018),
                checkedTrackColor = accents.orbGlow,
                checkedBorderColor = Color(0xFF67E8F9),
                uncheckedThumbColor = Color(0xFF8BA2BE),
                uncheckedTrackColor = Color(0xFF141F33),
                uncheckedBorderColor = Color(0xFF2A3B57),
            ),
        )
    }
}
