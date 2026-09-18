package com.jarvis.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.android.data.learning.LearningCatalog
import com.jarvis.android.ui.JarvisViewModel
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.LocalJarvisAccents
import com.jarvis.android.ui.theme.jarvisTextFieldColors

/**
 * On-device lesson room. Answers are graded locally and rewards unlock from the
 * number of lessons finished on this phone. Nothing here is sent to the gateway.
 */
@Composable
fun LearningRoomScreen(vm: JarvisViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val completed = settings.completedLessons
    val accents = LocalJarvisAccents.current
    val total = LearningCatalog.lessons.size
    val done = completed.count { id -> LearningCatalog.lesson(id) != null }

    ProjectsScaffold(title = "LEARNING ROOM", onBack = onBack) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "$done OF $total LESSONS",
                            style = HudTextStyle,
                            color = accents.orbGlow,
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (total == 0) 0f else done.toFloat() / total },
                            color = accents.orbGlow,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Finish lessons to unlock rewards. Answers stay on this phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { SectionLabel("REWARDS") }
            items(LearningCatalog.rewards.size, key = { LearningCatalog.rewards[it].id }) { index ->
                val reward = LearningCatalog.rewards[index]
                val unlocked = done >= reward.lessonsRequired
                RewardRow(reward.title, reward.description, unlocked, reward.lessonsRequired)
            }

            item { SectionLabel("LESSONS") }
            items(LearningCatalog.lessons.size, key = { LearningCatalog.lessons[it].id }) { index ->
                val lesson = LearningCatalog.lessons[index]
                LessonCard(
                    title = lesson.title,
                    prompt = lesson.prompt,
                    solved = lesson.id in completed,
                    onSubmit = { vm.submitLesson(lesson.id, it) },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val accents = LocalJarvisAccents.current
    Text(text, style = HudTextStyle, color = accents.orbGlow, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun RewardRow(title: String, description: String, unlocked: Boolean, required: Int) {
    val accents = LocalJarvisAccents.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (unlocked) 0.8f else 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (unlocked) description else "Locked — finish $required lessons",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (unlocked) "UNLOCKED" else "LOCKED",
                style = HudTextStyle,
                color = if (unlocked) accents.online else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LessonCard(
    title: String,
    prompt: String,
    solved: Boolean,
    onSubmit: (String) -> Boolean,
) {
    val accents = LocalJarvisAccents.current
    var answer by rememberSaveable(title) { mutableStateOf("") }
    var wrong by rememberSaveable(title) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (solved) Text("DONE", style = HudTextStyle, color = accents.online)
            }
            Spacer(Modifier.height(6.dp))
            Text(prompt, style = MaterialTheme.typography.bodyMedium)
            if (!solved) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it; wrong = false },
                    singleLine = true,
                    label = { Text("Your answer") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = jarvisTextFieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { wrong = !onSubmit(answer) },
                    enabled = answer.isNotBlank(),
                ) { Text("Check") }
                if (wrong) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Not quite — try again.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
