package com.jarvis.android.ui.writing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarvis.android.transport.live.WritingApprovalStateResult
import com.jarvis.android.transport.live.WritingChapterAutoReviewResult
import com.jarvis.android.transport.live.WritingDraftExecutionResult
import com.jarvis.android.transport.live.WritingDraftReviewRecord
import com.jarvis.android.ui.theme.HudTextStyle
import com.jarvis.android.ui.theme.JarvisAmber
import com.jarvis.android.ui.theme.JarvisCyan
import com.jarvis.android.ui.theme.JarvisGreen
import com.jarvis.android.ui.theme.JarvisRed
import com.jarvis.android.ui.theme.JarvisViolet

/** A4 chapter-to-final-review presentation. Durable authority stays server-side. */
@Composable
fun DraftReviewScreen(
    draft: WritingDraftExecutionResult?,
    approval: WritingApprovalStateResult?,
    autoReview: WritingChapterAutoReviewResult?,
    busy: Boolean,
    onRunDraft: () -> Unit,
    onRunReview: () -> Unit,
    onPrepareApproval: () -> Unit,
    onApprove: () -> Unit,
    onVisualizeScene: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (draft == null && approval == null) return

    val aggregate = approval?.aggregate ?: draft?.aggregate
    val stage = aggregate?.chapter_stage.orEmpty()
    val publication = aggregate?.publication_state.orEmpty()
    val reviews = approval?.reviews ?: draft?.reviews.orEmpty()
    val reviewPass = reviews.groupBy { it.review_kind }.let { grouped ->
        listOf("reviewer", "canon_keeper").all { kind ->
            grouped[kind]?.maxByOrNull { it.review_version }?.validation_status == "PASS"
        }
    }
    val draftJob = draft?.job
    val reviewJob = draft?.review_job
    val canonDiff = approval?.canon_diff
    val autoStatus = autoReview?.status.orEmpty()
    var advancedExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xEE0E182A),
        border = BorderStroke(1.dp, stageColor(stage).copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("JARVIS · CAPÍTULO HASTA REVISIÓN FINAL", style = HudTextStyle, color = JarvisCyan)
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StagePill(stage.ifBlank { "BRIEF_APPROVED" }, stageColor(stage))
                if (autoReview?.job != null) {
                    StagePill(
                        "AUTO · ${autoStatus.ifBlank { autoReview.job.status.ifBlank { "DESCONOCIDO" } }}",
                        jobColor(autoReview.job.status),
                    )
                }
                if (draftJob != null) StagePill("WRITER · ${draftJob.status.ifBlank { "DESCONOCIDO" }}", jobColor(draftJob.status))
                if (reviewJob != null) StagePill("REVIEW · ${reviewJob.status.ifBlank { "DESCONOCIDO" }}", jobColor(reviewJob.status))
                if (publication.isNotBlank()) StagePill("PUBLICACIÓN · $publication", if (publication == "PUBLISHED") JarvisGreen else JarvisAmber)
            }

            if (autoReview != null) {
                Spacer(Modifier.height(9.dp))
                Text(
                    "JARVIS coordina Writer → Reviewer → Canon Keeper → corrección acotada → CanonDiff. " +
                        "Correcciones automáticas: ${autoReview.corrections_used}/${autoReview.max_corrections}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                )
                autoReview.assessment.pending_findings.take(5).forEach { finding ->
                    if (finding.message.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "• ${finding.review_kind.replace('_', ' ')} · ${finding.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (finding.severity == "blocking") JarvisRed else JarvisAmber,
                        )
                    }
                }
            }

            if (
                autoStatus == "OUTCOME_UNKNOWN" ||
                draftJob?.status == "OUTCOME_UNKNOWN" ||
                reviewJob?.status == "OUTCOME_UNKNOWN"
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Un proveedor dejó un resultado ambiguo. JARVIS no repetirá automáticamente esa llamada para evitar duplicados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisAmber,
                )
            }

            if (reviews.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("REVISIONES DEL BORRADOR ACTUAL", style = HudTextStyle, color = JarvisViolet)
                Spacer(Modifier.height(6.dp))
                reviews.sortedWith(compareBy<WritingDraftReviewRecord>({ it.review_kind }, { it.review_version })).forEach { review ->
                    ReviewCard(review)
                    Spacer(Modifier.height(7.dp))
                }
            }

            if (canonDiff != null) {
                Spacer(Modifier.height(10.dp))
                Text("CANONDIFF", style = HudTextStyle, color = JarvisAmber)
                if (canonDiff.payload.summary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(canonDiff.payload.summary, color = Color(0xFFE2E8F0))
                }
                if (canonDiff.payload.changes.isEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    Text("Sin cambios de canon detectados para esta revisión.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFCBD5E1))
                } else {
                    Spacer(Modifier.height(6.dp))
                    canonDiff.payload.changes.forEach { change ->
                        Text("• ${change.kind.ifBlank { "cambio" }} · ${change.statement}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE2E8F0))
                    }
                }
                canonDiff.payload.blocking_issues.forEach { issue ->
                    Spacer(Modifier.height(4.dp))
                    Text("Bloqueo: ${issue.message}", style = MaterialTheme.typography.bodySmall, color = JarvisRed)
                }
            }

            Spacer(Modifier.height(12.dp))
            when {
                stage == "APPROVED" -> Text(
                    if (publication == "SOURCE_PENDING") "Texto y CanonDiff aprobados. La publicación canónica sigue pendiente de W3." else "Capítulo aprobado.",
                    color = JarvisGreen,
                    fontWeight = FontWeight.SemiBold,
                )
                approval?.approval_ready == true && stage == "READY_FOR_APPROVAL" -> {
                    Text(
                        "JARVIS terminó el trabajo automático. Revisa el texto y el CanonDiff; esta es la única aprobación que cambia el estado del capítulo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisGreen,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onApprove,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisGreen, contentColor = Color(0xFF02101F)),
                    ) { Text("Aprobar texto + CanonDiff", fontWeight = FontWeight.Bold) }
                }
                autoStatus == "NEEDS_HUMAN_REVIEW" -> Text(
                    "JARVIS llegó al límite de correcciones o encontró un bloqueo real. El borrador actual se conserva para que puedas editarlo; al guardar, la revisión automática continúa sola.",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisAmber,
                )
                canonDiff != null && canonDiff.payload.blocking_issues.isNotEmpty() -> Text(
                    "La aprobación está bloqueada hasta resolver los conflictos de canon mostrados arriba.",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisRed,
                )
                autoStatus == "OUTCOME_UNKNOWN" -> Text(
                    "Se requiere intervención antes de volver a usar el proveedor; no hay redispatch automático.",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisAmber,
                )
                autoStatus in setOf("WORKING", "RETRYABLE", "PENDING", "PENDING_CANON_DIFF") ||
                    draftJob?.status in setOf("QUEUED", "FAILED_RETRYABLE") ||
                    reviewJob?.status in setOf("QUEUED", "FAILED_RETRYABLE") -> Button(
                    onClick = onRunDraft,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Procesando…" else "Continuar trabajo automático")
                }
                else -> OutlinedButton(
                    onClick = onRunDraft,
                    enabled = !busy && stage == "BRIEF_APPROVED",
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Procesando…" else "Iniciar trabajo automático") }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onVisualizeScene,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Visualizar escena")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { advancedExpanded = !advancedExpanded },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (advancedExpanded) "Ocultar opciones avanzadas" else "Opciones avanzadas")
            }
            if (advancedExpanded) {
                Spacer(Modifier.height(7.dp))
                Text(
                    "Respaldo manual. El flujo normal no necesita estos pasos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                )
                if (reviewJob != null && reviewJob.status in setOf("QUEUED", "FAILED_RETRYABLE")) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onRunReview,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Ejecutar revisión manual") }
                }
                if (reviewPass && stage in setOf("DRAFT_READY", "REVISING")) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onPrepareApproval,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Preparar CanonDiff manualmente") }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(review: WritingDraftReviewRecord) {
    val color = when (review.validation_status) {
        "PASS" -> JarvisGreen
        "BLOCKED" -> JarvisRed
        else -> JarvisAmber
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0x66111C30),
        border = BorderStroke(1.dp, color.copy(alpha = 0.38f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text("${review.review_kind.replace('_', ' ').uppercase()} · ${review.validation_status}", style = HudTextStyle, color = color)
            if (review.payload.summary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(review.payload.summary, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE2E8F0))
            }
            review.payload.findings.take(6).forEach { finding ->
                Spacer(Modifier.height(3.dp))
                Text("• ${finding.message}", style = MaterialTheme.typography.bodySmall, color = if (finding.severity == "blocking") JarvisRed else Color(0xFFCBD5E1))
            }
        }
    }
}

@Composable
private fun StagePill(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.42f)),
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = HudTextStyle, color = color)
    }
}

private fun stageColor(stage: String): Color = when (stage) {
    "APPROVED" -> JarvisGreen
    "READY_FOR_APPROVAL" -> JarvisAmber
    "REVISING" -> JarvisViolet
    "DRAFT_READY" -> JarvisCyan
    "DRAFTING" -> JarvisAmber
    else -> JarvisCyan
}

private fun jobColor(status: String): Color = when (status) {
    "SUCCEEDED" -> JarvisGreen
    "OUTCOME_UNKNOWN", "WAITING_PROVIDER" -> JarvisAmber
    "FAILED_FINAL", "CANCELED" -> JarvisRed
    else -> JarvisCyan
}