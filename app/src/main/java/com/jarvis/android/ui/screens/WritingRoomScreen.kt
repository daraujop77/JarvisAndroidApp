package com.jarvis.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.android.data.projects.ProjectSummary
import com.jarvis.android.data.writing.DraftStats
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.jarvisTextFieldColors
import kotlinx.coroutines.delay

/**
 * Local draft editor. Text is saved on this phone a moment after typing stops
 * and is never sent anywhere. Each writing project keeps its own file.
 */
@Composable
fun WritingRoomScreen(vm: JarvisViewModel, project: ProjectSummary, onBack: () -> Unit) {
    var text by remember(project.id.value) { mutableStateOf(vm.readDraft(project.id.value)) }
    val accents = LocalJarvisAccents.current

    LaunchedEffect(text) {
        delay(600)
        vm.saveDraft(project.id.value, text)
    }

    ProjectsScaffold(title = project.title.uppercase(), onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SAVED ON THIS PHONE", style = HudTextStyle, color = accents.orbGlow)
                Text(
                    "${DraftStats.words(text)} WORDS",
                    style = HudTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                placeholder = { Text("Start writing…") },
                shape = RoundedCornerShape(16.dp),
                colors = jarvisTextFieldColors(),
            )
        }
    }
}
