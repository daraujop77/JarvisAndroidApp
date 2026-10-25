package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.CopilotApprovedVisualAsset
import com.jarvis.android.transport.live.CopilotTaskResult
import com.jarvis.android.transport.live.CopilotTaskState
import com.jarvis.android.transport.live.WritingRoomAutoChat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotTaskVisualRecoveryTest {
    private val approved = CopilotApprovedVisualAsset(
        asset_id = "va_0123456789abcdef01234567",
        sha256 = "a".repeat(64),
        status = "APPROVED",
        storage_state = "stored",
        kind = "PRIMARY_REFERENCE",
        character_ids = listOf("character:alexander"),
    )
    private fun task(
        project: String = "prj_story",
        taskId: String = "ar_123",
        assets: List<CopilotApprovedVisualAsset> = emptyList(),
    ) = CopilotTaskState(
        project_id = project,
        task_id = taskId,
        results = if (assets.isEmpty()) emptyList()
                  else listOf(CopilotTaskResult(
                      tool = "get_approved_visual_references",
                      visual_assets = assets,
                  )),
    )

    @Test fun editActionSupportsVerifiedApprovedAndCandidateSources() {
        val id = approved.asset_id
        val hash = approved.sha256
        fun target(
            status: String = "APPROVED",
            storage: String = "stored",
            shown: String? = "verified-image",
            verified: String? = "verified-image",
            assetId: String = id,
            sha: String = hash,
        ) = editableCopilotVisualSource(
            assetId, sha, status, storage, shown, verified,
        )
        assertEquals(id to hash, target())
        assertEquals(id to hash, target(status = "CANDIDATE"))
        assertEquals(null, target(storage = "pending"))
        assertEquals(null, target(status = "REJECTED"))
        assertEquals(null, target(shown = "other-image"))
        assertEquals(null, target(verified = null))
        assertEquals(null, target(assetId = "../unsafe"))
        assertEquals(null, target(sha = "broken"))
    }

    @Test fun approvedHistorySurvivesMoreRecentCandidateOnlyTurn() {
        val earlier = approved
        val newestCandidate = approved.copy(
            asset_id = "va_abcdefabcdefabcdefabcdef", status = "CANDIDATE",
        )
        val reused = earlier.copy(alt = "Same reference in a different turn")
        assertEquals(listOf(earlier.asset_id),
            approvedVisualIdsForCopilotContext(listOf(
                listOf(newestCandidate), listOf(earlier, reused),
            )))
        assertTrue(approvedVisualIdsForCopilotContext(listOf(
            listOf(newestCandidate, approved.copy(storage_state = "pending")),
            listOf(approved.copy(asset_id = "../outside")),
            listOf(approved.copy(sha256 = "invalid")),
        )).isEmpty())
    }

    @Test fun approvedHistoryKeepsAtMostSixUniqueRecentImages() {
        val seven = (1..7).map { n ->
            approved.copy(asset_id = "va_" + n.toString().padStart(24, 'a'))
        }
        val ids = approvedVisualIdsForCopilotContext(
            listOf(seven.take(3), seven.drop(3)),
        )
        assertEquals(seven.take(6).map { it.asset_id }, ids)
    }

    @Test fun attachedPhotoBecomesExactCandidateThenCanBeEditedInSameTask() {
        val uploaded = CopilotTaskResult(tool = "import_attached_image_reference",
            result = buildJsonObject {
                put("asset_id", "va_ref_" + "a".repeat(32))
                put("sha256", "b".repeat(64))
                put("kind", "REFERENCE")
                put("perspective", "custom")
                put("character_id", "")
                put("status", "CANDIDATE")
                put("storage_state", "stored")
            })
        val edited = CopilotTaskResult(tool = "edit_visual_candidate",
            result = buildJsonObject {
                put("asset_id", "va_gen_" + "c".repeat(32))
                put("sha256", "d".repeat(64))
                put("kind", "REFERENCE")
                put("perspective", "custom")
                put("character_id", "")
                put("status", "CANDIDATE")
                put("storage_state", "stored")
            })
        val original = task().copy(results = listOf(uploaded))
        assertEquals("va_ref_" + "a".repeat(32),
            completedCopilotGeneratedMaster("prj_story", original)?.assetId)
        assertEquals("va_gen_" + "c".repeat(32),
            completedCopilotGeneratedMaster("prj_story",
                original.copy(results = listOf(uploaded, edited)))?.assetId)
        assertEquals(null, completedCopilotGeneratedMaster("wrong-project", original))
        assertEquals("REFERENCE",
            completedCopilotGeneratedMaster("prj_story", original)?.kind)
    }

    @Test fun invalidLastVisualReceiptDoesNotHidePreviouslyCompletedCandidate() {
        val valid = CopilotTaskResult(tool = "edit_visual_candidate",
            result = buildJsonObject {
                put("asset_id", "va_gen_" + "a".repeat(32))
                put("sha256", "b".repeat(64))
                put("character_id", "character:doom")
                put("status", "CANDIDATE")
                put("storage_state", "pending")
            })
        val malformed = valid.copy(result = buildJsonObject {
            put("asset_id", "not-a-valid-asset-id")
            put("sha256", "b".repeat(64))
            put("character_id", "character:doom")
            put("status", "CANDIDATE")
            put("storage_state", "pending")
        })
        assertEquals("va_gen_" + "a".repeat(32),
            completedCopilotGeneratedMaster("prj_story",
                task().copy(results = listOf(valid, malformed)))?.assetId)
    }

    @Test fun newApprovedAssetArrivingDuringPollingIsDownloadedOnce() {
        val initial = task()
        val completed = task(assets = listOf(approved))
        assertEquals(listOf(approved),
            newApprovedCopilotTaskVisuals("prj_story", initial, completed))
        assertTrue(newApprovedCopilotTaskVisuals(
            "prj_story", completed, completed,
        ).isEmpty())
    }

    @Test fun candidateBecomingApprovedTriggersOnlyAfterApproval() {
        val candidate = approved.copy(status = "CANDIDATE")
        val before = task(assets = listOf(candidate))
        val after = task(assets = listOf(approved))
        assertTrue(newApprovedCopilotTaskVisuals(
            "prj_story", task(), before,
        ).isEmpty())
        assertEquals(listOf(approved),
            newApprovedCopilotTaskVisuals("prj_story", before, after))
    }

    @Test fun mismatchedProjectOrMissingTaskNeverLeaksVisuals() {
        val crossProject = task(project = "prj_private", assets = listOf(approved))
        assertTrue(newApprovedCopilotTaskVisuals(
            "prj_story", task(), crossProject,
        ).isEmpty())
        assertTrue(newApprovedCopilotTaskVisuals(
            "prj_story", null, task(taskId = "", assets = listOf(approved)),
        ).isEmpty())
    }

    @Test fun invalidHashAndStorageStateAreNeverDownloaded() {
        val badDigest = approved.copy(sha256 = "xyz")
        val pending = approved.copy(storage_state = "pending")
        val otherId = approved.copy(asset_id = "not-a-visual")
        val next = task(assets = listOf(badDigest, pending, otherId))
        assertTrue(newApprovedCopilotTaskVisuals(
            "prj_story", null, next,
        ).isEmpty())
    }

    @Test fun duplicateAndChangedHashForSeenIdAreNotReFetched() {
        val before = task(assets = listOf(approved))
        val later = task(assets = listOf(
            approved.copy(sha256 = "b".repeat(64)),
            approved,
        ))
        assertTrue(newApprovedCopilotTaskVisuals(
            "prj_story", before, later,
        ).isEmpty())
        assertEquals(1, newApprovedCopilotTaskVisuals(
            "prj_story", task(), later,
        ).size)
    }

    @Test fun laterTaskDoesNotInheritEarlierTasksSeenAssets() {
        val previous = task(taskId = "ar_previous", assets = listOf(approved))
        val fresh = task(taskId = "ar_new", assets = listOf(approved))
        assertEquals(listOf(approved),
            newApprovedCopilotTaskVisuals("prj_story", previous, fresh))
    }

    @Test fun adaptiveAgentWithoutLegacyWorkerRowsMustStillPoll() {
        assertTrue(copilotTaskHasActiveRemoteWork(
            task().copy(agent_run_state = "RUNNING", state = "RUNNING"),
        ))
        assertTrue(copilotTaskHasActiveRemoteWork(
            task().copy(agent_run_state = "DISPATCHING", state = "DISPATCHING"),
        ))
    }

    @Test fun waitingApprovalOrTerminalOutcomeCannotTriggerAutomaticPolling() {
        assertTrue(!copilotTaskHasActiveRemoteWork(
            task().copy(agent_run_state = "WAITING_CONFIRMATION", state = "READY"),
        ))
        assertTrue(!copilotTaskHasActiveRemoteWork(
            task().copy(agent_run_state = "OUTCOME_UNKNOWN", state = "OUTCOME_UNKNOWN"),
        ))
        assertTrue(!copilotTaskHasActiveRemoteWork(
            task().copy(agent_run_state = "SUCCEEDED", state = "DONE"),
        ))
    }

    @Test fun oldStyleBackgroundWorkerStillRequiresPollingUntilFinished() {
        val progress = com.jarvis.android.transport.live.CopilotTaskLiveProgress(
            tool = "generate_story_scene",
            status = "RUNNING",
        )
        assertTrue(copilotTaskHasActiveRemoteWork(
            task().copy(live_progress = listOf(progress)),
        ))
        assertTrue(!copilotTaskHasActiveRemoteWork(
            task().copy(live_progress = listOf(progress.copy(status = "READY_FOR_REVIEW"))),
        ))
    }

    @Test fun terminalTaskWithQueuedLegacyVisualJobStillPolls() {
        val queued = com.jarvis.android.transport.live.CopilotTaskLiveProgress(
            tool = "complete_character_views", status = "QUEUED",
        )
        assertTrue(copilotTaskHasActiveRemoteWork(
            task().copy(state = "DONE", live_progress = listOf(queued)),
        ))
        assertTrue(copilotTaskHasActiveRemoteWork(
            task().copy(agent_run_state = "SUCCEEDED", state = "DONE",
                live_progress = listOf(queued)),
        ))
    }

    @Test fun backgroundWorkerOutlivesFailedOrPartialAgentText() {
        val queued = com.jarvis.android.transport.live.CopilotTaskLiveProgress(
            tool = "start_scene_generation", job_id = "scene_123",
            status = "QUEUED",
        )
        val result = task().copy(
            state = "BLOCKED", agent_run_state = "PARTIAL",
            live_progress = listOf(queued),
        )
        assertTrue(copilotTaskHasActiveRemoteWork(result))
        assertTrue(copilotTaskHasActiveRemoteWork(
            result.copy(agent_run_state = "OUTCOME_UNKNOWN")))
        assertTrue(!copilotTaskHasActiveRemoteWork(
            result.copy(live_progress = listOf(queued.copy(
                status = "READY_FOR_REVIEW",
            )))))
        assertTrue(!copilotTaskHasActiveRemoteWork(
            result.copy(state = "READY", agent_run_state = "WAITING_CONFIRMATION",
                requires_confirmation = true)))
    }

    @Test fun completedAdaptivePollMakesJustFinishedApprovedReferenceAvailable() {
        val active = task().copy(agent_run_state = "RUNNING", state = "RUNNING")
        val done = task(assets = listOf(approved))
            .copy(agent_run_state = "SUCCEEDED", state = "DONE")
        assertTrue(copilotTaskHasActiveRemoteWork(active))
        assertEquals(listOf(approved),
            newApprovedCopilotTaskVisuals("prj_story", active, done))
        assertTrue(!copilotTaskHasActiveRemoteWork(done))
    }

    @Test fun interruptedFinalTextStillRebuildsExactVisualCandidate() {
        val pending = CopilotTaskResult(
            tool = "generate_character_master",
            result = buildJsonObject {
                put("asset_id", "va_gen_" + "a".repeat(32))
                put("sha256", "b".repeat(64))
                put("character_id", "character:doom")
                put("status", "CANDIDATE")
                put("storage_state", "pending")
            },
        )
        val original = task(taskId = "ar_saved_image").copy(
            request_id = "req-original", conversation_id = "chat-original",
            request_text = "Genera retrato de Doom",
        )
        val noText = original.copy(
            state = "BLOCKED", agent_run_state = "PARTIAL",
            reply_text = "", results = listOf(pending),
        )
        assertTrue(hasRecoverableCopilotVisualReceipt("prj_story", noText))
        assertTrue(canRebuildCopilotTurn("prj_story", original, noText))
        assertEquals("va_gen_" + "a".repeat(32),
            completedCopilotGeneratedMaster("prj_story", noText)?.assetId)

        // The recovery predicate NEVER accepts a bare target ID, another
        // project, an invalid digest or an unrelated final text response.
        assertTrue(!hasRecoverableCopilotVisualReceipt(
            "another_project", noText))
        assertTrue(!canRebuildCopilotTurn(
            "prj_story", original, noText.copy(request_id = "other-request")))
        assertTrue(!canRebuildCopilotTurn(
            "prj_story", original, noText.copy(conversation_id = "other-chat")))
        assertTrue(!canRebuildCopilotTurn(
            "prj_story", original, noText.copy(agent_run_state = "RUNNING")))
        val bad = pending.copy(result = buildJsonObject {
            put("asset_id", "va_gen_" + "a".repeat(32))
            put("sha256", "bad")
            put("character_id", "character:doom")
            put("status", "CANDIDATE")
            put("storage_state", "stored")
        })
        assertTrue(!canRebuildCopilotTurn(
            "prj_story", original, noText.copy(results = listOf(bad))))
        assertTrue(!canRebuildCopilotTurn(
            "prj_story", original, noText.copy(results = emptyList())))
    }

    @Test fun approvedImageMetadataCanRebuildTurnWithMissingModelText() {
        val original = task(taskId = "ar_approved").copy(
            request_id = "req-view", conversation_id = "chat-original",
        )
        val snapshot = original.copy(
            request_text = "Enséñame la referencia aprobada",
            agent_run_state = "PARTIAL", reply_text = "",
            results = listOf(CopilotTaskResult(
                tool = "get_approved_visual_references",
                visual_assets = listOf(approved),
            )),
        )
        assertTrue(canRebuildCopilotTurn("prj_story", original, snapshot))
        assertTrue(!hasRecoverableCopilotVisualReceipt(
            "prj_story", snapshot.copy(results = listOf(CopilotTaskResult(
                tool = "get_approved_visual_references",
                visual_assets = listOf(approved.copy(storage_state = "pending")),
            )))))
    }

    @Test fun missingChatBubbleCanBeRebuiltOnlyForItsOriginalScopedTurn() {
        val original = task(taskId = "ar_original").copy(
            conversation_id = "conversation-1", request_id = "request-1",
        )
        val recovered = original.copy(
            request_text = "Genera Doom", reply_text = "La imagen está lista",
            agent_run_state = "SUCCEEDED",
        )
        assertTrue(canRebuildCopilotTurn("prj_story", original, recovered))
        assertTrue(!canRebuildCopilotTurn("wrong-project", original, recovered))
        assertTrue(!canRebuildCopilotTurn(
            "prj_story", original, recovered.copy(task_id = "ar_other")))
        assertTrue(!canRebuildCopilotTurn(
            "prj_story", original, recovered.copy(request_id = "another")))
        assertTrue(!canRebuildCopilotTurn(
            "prj_story", original, recovered.copy(conversation_id = "private")))
        assertTrue(!canRebuildCopilotTurn(
            "prj_story", original, recovered.copy(request_text = "")))
        assertTrue(!canRebuildCopilotTurn(
            "prj_story", original, recovered.copy(agent_run_state = "RUNNING")))
        assertTrue(!canRebuildCopilotTurn(
            "prj_story", original.copy(request_id = ""), recovered))
    }

    @Test fun writingRoomAcceptsTypedCandidateContentWithoutApprovingIt() {
        val wire = """{
            "schema": "jarvis.writing-room.chat.v1",
            "content": {
                "schema": "jarvis.chat.content.v1",
                "blocks": [
                    {"type":"text","text":"Aquí está el draft"},
                    {"type":"image","asset_id":"va_gen_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                     "sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                     "status":"CANDIDATE","storage_state":"pending"}
                ], "sources": []
            }
        }"""
        val parsed = Json { ignoreUnknownKeys = true }
            .decodeFromString<WritingRoomAutoChat>(wire)
        assertEquals("jarvis.chat.content.v1", parsed.content?.schema)
        assertEquals("CANDIDATE", parsed.content?.blocks?.get(1)?.status)
        assertEquals("pending", parsed.content?.blocks?.get(1)?.storage_state)
        assertTrue(parsed.visual_assets.isEmpty())
    }

    @Test fun existingAndEditedVisualAppearInsideOriginalCopilotTurn() {
        val source = CopilotTaskResult(tool = "get_visual_asset_for_chat",
            result = buildJsonObject {
                put("asset_id", "va_gen_" + "a".repeat(32))
                put("sha256", "b".repeat(64))
                put("character_id", "character:doom")
                put("status", "CANDIDATE")
                put("storage_state", "pending")
            })
        val updated = source.copy(tool = "edit_visual_candidate",
            result = buildJsonObject {
                put("asset_id", "va_gen_" + "c".repeat(32))
                put("sha256", "d".repeat(64))
                put("character_id", "character:doom")
                put("status", "CANDIDATE")
                put("storage_state", "pending")
            })
        val linked = task().copy(results = listOf(source, updated),
            state = "BLOCKED", agent_run_state = "PARTIAL")
        val candidate = completedCopilotGeneratedMaster("prj_story", linked)
        assertEquals("va_gen_" + "c".repeat(32), candidate?.assetId)
        assertEquals("pending", candidate?.storageState)
        assertEquals("character:doom", candidate?.characterId)
        assertEquals("va_gen_" + "a".repeat(32),
            completedCopilotGeneratedMaster(
                "prj_story", linked.copy(results = listOf(source)))?.assetId)
        assertTrue(completedCopilotGeneratedMaster(
            "other-project", linked) == null)
    }

    @Test fun sceneEditPreviewNeverMasqueradesAsCharacterMaster() {
        val scene = CopilotTaskResult(tool = "edit_visual_candidate",
            result = buildJsonObject {
                put("asset_id", "va_gen_" + "e".repeat(32))
                put("sha256", "f".repeat(64))
                put("character_id", "")
                put("kind", "SCENE_ART")
                put("perspective", "scene")
                put("status", "CANDIDATE")
                put("storage_state", "pending")
            })
        val task = task().copy(results = listOf(scene))
        val shown = completedCopilotGeneratedMaster("prj_story", task)
        assertEquals("SCENE_ART", shown?.kind)
        assertEquals("scene", shown?.perspective)
        assertEquals("", shown?.characterId)
        val fake = scene.copy(result = buildJsonObject {
            put("asset_id", "va_gen_" + "e".repeat(32))
            put("sha256", "f".repeat(64))
            put("status", "CANDIDATE")
            put("storage_state", "pending")
            put("kind", "PRIMARY_REFERENCE")
            put("character_id", "")
        })
        assertTrue(completedCopilotGeneratedMaster(
            "prj_story", task.copy(results = listOf(fake))) == null)
    }

    @Test fun projectReferenceApprovalNeverConfusesCanonMasterOrScene() {
        val id = "va_ref_" + "a".repeat(32)
        val sha = "b".repeat(64)
        assertTrue(isApprovableProjectReference(
            id, sha, "REFERENCE", "CANDIDATE", "stored", "", true))
        for (kind in listOf("PRIMARY_REFERENCE", "SCENE_ART", ""))
            assertTrue(!isApprovableProjectReference(
                id, sha, kind, "CANDIDATE", "stored", "", true))
        assertTrue(!isApprovableProjectReference(
            id, sha, "REFERENCE", "APPROVED", "stored", "", true))
        assertTrue(!isApprovableProjectReference(
            id, sha, "REFERENCE", "CANDIDATE", "pending", "", true))
        assertTrue(!isApprovableProjectReference(
            id, sha, "REFERENCE", "CANDIDATE", "stored", "character:doom", true))
        assertTrue(!isApprovableProjectReference(
            id, sha, "REFERENCE", "CANDIDATE", "stored", "", false))
        assertTrue(!isApprovableProjectReference(
            "../wrong", sha, "REFERENCE", "CANDIDATE", "stored", "", true))
        assertTrue(!isApprovableProjectReference(
            id, "wrong", "REFERENCE", "CANDIDATE", "stored", "", true))
    }

    @Test fun completedCharacterMasterIsRecoveredWithoutRepeatGeneration() {
        val result = CopilotTaskResult(tool = "generate_character_master",
            result = buildJsonObject {
                put("asset_id", "va_gen_" + "a".repeat(32))
                put("sha256", "b".repeat(64))
                put("character_id", "character:doom")
                put("status", "CANDIDATE")
                put("storage_state", "stored")
            })
        val completed = task().copy(
            agent_run_state = "SUCCEEDED", state = "DONE", results = listOf(result),
        )
        val candidate = completedCopilotGeneratedMaster("prj_story", completed)
        assertEquals("character:doom", candidate?.characterId)
        assertEquals("CANDIDATE", candidate?.status)
        assertTrue(completedCopilotGeneratedMaster("another_project", completed) == null)
        val interrupted = completed.copy(
            state = "OUTCOME_UNKNOWN", agent_run_state = "OUTCOME_UNKNOWN",
        )
        assertEquals(candidate?.assetId,
            completedCopilotGeneratedMaster("prj_story", interrupted)?.assetId)
        val spoolPending = result.copy(result = buildJsonObject {
            put("asset_id", "va_gen_" + "a".repeat(32))
            put("sha256", "b".repeat(64))
            put("character_id", "character:doom")
            put("status", "CANDIDATE")
            put("storage_state", "pending")
        })
        assertEquals("pending", completedCopilotGeneratedMaster(
            "prj_story", interrupted.copy(results = listOf(spoolPending)))?.storageState)
        assertTrue(completedCopilotGeneratedMaster(
            "prj_story", interrupted.copy(results = emptyList())) == null)
        assertTrue(completedCopilotGeneratedMaster("prj_story",
            completed.copy(results = listOf(result.copy(result =
                buildJsonObject { put("asset_id", "not-valid") })))) == null)
    }

    /**
     * The actual pre-APK FLUX handoff used this immutable original SHA and
     * candidate ID. Only metadata enters this wire fixture: image bytes were
     * independently read from the real stage with SHA/PNG validation.
     *
     * An Android candidate must be recoverable in the source chat bubble,
     * but NEVER appear as an APPROVED reference or bypass the image decode
     * gate. No real Drive request, LLM call, regeneration or APK here.
     */
    @Test fun actualFluxCopilotCandidateWireParsesWithoutPromotingCanon() {
        val project = "prj_preapk_flux_visual_fixture"
        val sha = "bcbb45314e757aa2ada3364c5b8ece481b428784b94e437565baa4046005186f"
        val asset = "va_ref_" + sha.take(32)
        val wire = """
            {
              "schema":"jarvis.writing-room.chat.v1",
              "selected_participant":"copilot",
              "turn":{
                "project_id":"$project",
                "response":{"text":"Recuperé la imagen candidata existente. No la aprobé ni la convertí en canon."}
              },
              "copilot_task":{
                "schema":"jarvis.writing-room.copilot.task.v1",
                "task_id":"ar_flux_existing_readonly",
                "project_id":"$project",
                "conversation_id":"preapk-flux-copilot-preview",
                "request_id":"preapk-flux-existing-image-readonce",
                "agent_run_state":"SUCCEEDED",
                "state":"DONE",
                "requires_confirmation":false,
                "results":[{
                  "tool":"get_visual_asset_for_chat",
                  "status":"SUCCEEDED",
                  "effect":"READ_ONLY",
                  "result":{
                    "asset_id":"$asset","sha256":"$sha",
                    "status":"CANDIDATE","storage_state":"stored",
                    "kind":"SCENE_ART","perspective":"scene",
                    "character_id":"","mime_type":"image/png",
                    "human_approval_required":true,"auto_canon":false
                  },
                  "visual_assets":[{
                    "asset_id":"$asset","sha256":"$sha",
                    "status":"CANDIDATE","storage_state":"stored",
                    "kind":"SCENE_ART","mime_type":"image/png"
                  }]
                }]
              },
              "visual_assets":[],
              "content":{
                "schema":"jarvis.chat.content.v1",
                "blocks":[
                  {"type":"text","text":"Recuperé la imagen candidata existente."},
                  {"type":"image","asset_id":"$asset","sha256":"$sha",
                   "mime_type":"image/png","status":"CANDIDATE",
                   "storage_state":"stored"}
                ],
                "sources":[]
              }
            }
        """.trimIndent()

        val parsed = Json { ignoreUnknownKeys = true }
            .decodeFromString<WritingRoomAutoChat>(wire)
        assertEquals("jarvis.writing-room.chat.v1", parsed.schema)
        assertEquals("copilot", parsed.selected_participant)
        assertEquals("CANDIDATE", parsed.content?.blocks?.get(1)?.status)
        assertEquals(asset, parsed.content?.blocks?.get(1)?.asset_id)
        assertEquals(sha, parsed.content?.blocks?.get(1)?.sha256)
        assertTrue(parsed.visual_assets.isEmpty())
        val task = requireNotNull(parsed.copilot_task)
        assertEquals("DONE", task.state)
        assertEquals("READ_ONLY", task.results.single().effect)
        val recovered = completedCopilotGeneratedMaster(project, task)
        assertEquals(asset, recovered?.assetId)
        assertEquals(sha, recovered?.sha256)
        assertEquals("CANDIDATE", recovered?.status)
        assertEquals("stored", recovered?.storageState)
        assertEquals("SCENE_ART", recovered?.kind)
        assertEquals("", recovered?.characterId)
        assertEquals(null, completedCopilotGeneratedMaster("prj_other", task))
        assertTrue(!isApprovableProjectReference(
            asset, sha, "SCENE_ART", "CANDIDATE", "stored", "",
            verifiedPreviewPresent = true,
        ))
        assertEquals("CANDIDATE", task.results.single().visual_assets.single().status)
    }

}
