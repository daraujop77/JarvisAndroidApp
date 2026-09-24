package com.jarvis.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.transport.live.JarvisAppSession
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.i18n.LocalAppStrings
import com.jarvis.android.ui.theme.LocalJarvisAccents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(vm: JarvisViewModel, onBack: () -> Unit) {
    val strings = LocalAppStrings.current
    val state by vm.providerUsage.collectAsStateWithLifecycle()
    var period by remember { mutableStateOf("7d") }

    LaunchedEffect(Unit) { vm.refreshProviderUsage() }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(strings.usageTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = vm::refreshProviderUsage) {
                        Icon(Icons.Outlined.Refresh, contentDescription = strings.refreshUsage)
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
            modifier = Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x18101B2E),
                border = BorderStroke(1.dp, Color(0x3322D3EE)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    strings.usageObservedNotice,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB7C7DC),
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "today" to strings.usageToday,
                    "7d" to strings.usage7Days,
                    "30d" to strings.usage30Days,
                ).forEach { (id, label) ->
                    FilterChip(selected = period == id, onClick = { period = id }, label = { Text(label) })
                }
            }

            when (val current = state) {
                JarvisViewModel.ProviderUsageState.Idle,
                JarvisViewModel.ProviderUsageState.Loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) { CircularProgressIndicator() }
                }
                is JarvisViewModel.ProviderUsageState.Error -> {
                    UsageMessageCard(current.message)
                    OutlinedButton(onClick = vm::refreshProviderUsage) { Text(strings.refreshUsage) }
                }
                is JarvisViewModel.ProviderUsageState.Ready -> {
                    current.snapshot.providers.forEach { item -> ProviderUsageCard(item, period) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProviderUsageCard(provider: JarvisAppSession.ProviderUsage, period: String) {
    val strings = LocalAppStrings.current
    val accents = LocalJarvisAccents.current
    val counter = provider.periods[period] ?: JarvisAppSession.UsageCounter()

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xCC0D1626),
        border = BorderStroke(1.dp, accents.orbGlow.copy(alpha = 0.38f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        provider.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFE8EEF8),
                    )
                    Text(provider.model, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8FA9C4))
                }
                Text(
                    "JARVIS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accents.orbGlow,
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UsageMetric(strings.requestsLabel, compact(counter.requests), Modifier.weight(1f))
                UsageMetric(strings.successLabel, compact(counter.successfulRequests), Modifier.weight(1f))
                UsageMetric(strings.failedLabel, compact(counter.failedRequests), Modifier.weight(1f))
            }

            UsageMetric(
                strings.tokensLabel,
                counter.totalTokens?.let(::compact) ?: strings.tokensNotReported,
                Modifier.fillMaxWidth(),
            )

            if (provider.quotaStatus == "reported") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accents.orbGlow.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, accents.orbGlow.copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            strings.providerQuotaLive,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accents.orbGlow,
                        )
                        provider.quotaPlan?.takeIf { it.isNotBlank() }?.let {
                            Text(strings.quotaPlan(it), style = MaterialTheme.typography.bodySmall, color = Color(0xFFD5E3F3))
                        }

                        if (provider.quotaWindows.isNotEmpty()) {
                            provider.quotaWindows.forEach { window ->
                                QuotaWindowRow(window)
                            }
                        } else {
                            provider.quotaRemainingPercent?.let {
                                Text(
                                    strings.quotaRemaining(it.coerceIn(0.0, 100.0).toInt()),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFFE8EEF8),
                                )
                            }
                            provider.quotaResetsAt?.let(::formatQuotaReset)?.let {
                                Text(strings.quotaResets(it), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9FB5CC))
                            }
                        }

                        provider.quotaDetails.take(3).forEach { detail ->
                            Text(detail, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9FB5CC))
                        }
                    }
                }
            } else {
                Text(
                    strings.providerQuotaNotReported,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9FB5CC),
                )
            }

            if (counter.requests == 0L) {
                Text(strings.noUsageYet, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8298B2))
            }
        }
    }
}

@Composable
private fun QuotaWindowRow(window: JarvisAppSession.QuotaWindow) {
    val strings = LocalAppStrings.current
    val remaining = window.remainingPercent
        ?: window.usedPercent?.let { 100.0 - it }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                window.label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFFD5E3F3),
            )
            remaining?.let {
                Text(
                    strings.quotaRemaining(it.coerceIn(0.0, 100.0).toInt()),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFE8EEF8),
                )
            }
        }
        window.resetsAt?.let(::formatQuotaReset)?.let {
            Text(strings.quotaResets(it), style = MaterialTheme.typography.labelSmall, color = Color(0xFF8298B2))
        }
        window.detail?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8298B2))
        }
    }
}

private fun formatQuotaReset(value: String): String? = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault()))
}.getOrNull()

@Composable
private fun UsageMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0x2217CFE3), modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(0xFF8FA9C4))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFFE8EEF8),
            )
        }
    }
}

@Composable
private fun UsageMessageCard(message: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0x22101B2E),
        border = BorderStroke(1.dp, Color(0x334B617A)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB7C7DC))
    }
}

private fun compact(value: Long): String = when {
    value >= 1_000_000L -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000L -> String.format(java.util.Locale.US, "%.1fK", value / 1_000.0)
    else -> value.toString()
}
