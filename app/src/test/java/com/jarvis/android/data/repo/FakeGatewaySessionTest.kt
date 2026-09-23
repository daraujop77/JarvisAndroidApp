package com.jarvis.android.data.repo

import com.jarvis.android.contract.ApprovalOutcome
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.data.state.RequestStatus
import com.jarvis.android.transport.LinkState
import com.jarvis.android.transport.fake.FakeConfig
import com.jarvis.android.transport.fake.FakeFailMode
import com.jarvis.android.transport.fake.FakeGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end client pipeline over the deterministic Fake Gateway (plan AND-0002
 * gate): deterministic streaming from accepted -> deltas -> completed, duplicate
 * events don't duplicate UI, cancel is idempotent, protocol mismatch visible,
 * reconnect resumes, replay from cursor, approvals/tasks.
 */
class FakeGatewaySessionTest {

    private lateinit var job: Job
    private lateinit var scope: CoroutineScope
    private lateinit var fake: FakeGateway
    private lateinit var repo: JarvisSessionRepository

    @Before
    fun setUp() {
        job = SupervisorJob()
        scope = CoroutineScope(job + Dispatchers.Default)
        fake = FakeGateway(scope)
        repo = JarvisSessionRepository(fake, scope)
        repo.start()
        checkLink(LinkState.CONNECTED)
    }

    @After
    fun tearDown() {
        repo.stop()
        scope.cancel()
    }

    private fun checkLink(expected: LinkState) {
        runBlockingShort {
            withTimeout(2_000) {
                while (fake.linkState.value != expected) delay(5)
            }
        }
    }

    private fun awaitConnection(expected: ConnectionState) {
        runBlockingShort {
            withTimeout(3_000) {
                while (repo.snapshot.value.session.connection != expected) delay(10)
            }
        }
    }

    private fun <T> runBlockingShort(block: suspend kotlinx.coroutines.CoroutineScope.() -> T): T =
        kotlinx.coroutines.runBlocking(Dispatchers.Default, block = block)

    private fun awaitRequest(clientRequestId: String, predicate: (RequestStatus) -> Boolean): RequestStatus {
        var last: RequestStatus? = null
        runBlockingShort {
            withTimeout(5_000) {
                while (true) {
                    last = repo.snapshot.value.session.requests[clientRequestId]?.status
                    if (last != null && predicate(last!!)) break
                    delay(10)
                }
            }
        }
        return last!!
    }

    @Test
    fun happyPathStreamsToCompletion() {
        awaitConnection(ConnectionState.ONLINE)
        val cid = repo.send("c1", "hello")
        awaitRequest(cid) { it == RequestStatus.Completed }
        val req = repo.snapshot.value.session.requests[cid]!!
        assertEquals("word0 word1 word2 word3 word4 word5 word6 word7 ", req.text)
        val frame = fake.emittedFrames.first { it.contains("message.delta") }
        assertTrue(frame.contains("\"event_id\""))
        assertTrue(frame.contains("\"protocol_version\":\"1.0\""))
        assertTrue(frame.contains("\"cursor\":\"tok_"))
        assertTrue(!frame.contains("\"event\":{"))
    }

    @Test
    fun cancelIsIdempotentEndToEnd() {
        awaitConnection(ConnectionState.ONLINE)
        fake.config = FakeConfig(wordDelayMs = 60, replyWords = List(50) { "w$it " })
        val cid = repo.send("c1", "hello")
        awaitRequest(cid) { it == RequestStatus.Streaming }
        repeat(5) { repo.cancel(cid) } // repeated Stop presses
        awaitRequest(cid) { it == RequestStatus.Cancelled }
        val text1 = repo.snapshot.value.session.requests[cid]!!.text
        repo.cancel(cid) // cancel after terminal: no-op
        assertEquals(text1, repo.snapshot.value.session.requests[cid]!!.text)
        // exactly one cancel command hit the transport
        assertEquals(1, fake.receivedRequests.count { it is com.jarvis.android.contract.MobileRequest.CancelRequest })
    }

    @Test
    fun duplicateEventsDoNotDuplicateUiText() {
        awaitConnection(ConnectionState.ONLINE)
        fake.config = FakeConfig(injectDuplicates = true, replyWords = List(3) { "w$it " })
        val cid = repo.send("c1", "hello")
        awaitRequest(cid) { it == RequestStatus.Completed }
        val req = repo.snapshot.value.session.requests[cid]!!
        // duplicates dropped -> exactly the three words once
        assertEquals("w0 w1 w2 ", req.text)
        assertTrue(req.text == "w0 w1 w2 ")
    }

    @Test
    fun outOfOrderDeltasStillRenderInOrder() {
        awaitConnection(ConnectionState.ONLINE)
        fake.config = FakeConfig(injectOutOfOrder = true, outOfOrderFirstSeq = 1, replyWords = List(6) { "w$it " })
        val cid = repo.send("c1", "hello")
        awaitRequest(cid) { it == RequestStatus.Completed }
        val req = repo.snapshot.value.session.requests[cid]!!
        // full text, no scrambled order, no duplication:
        assertEquals("w0 w1 w2 w3 w4 w5 ", req.text)
    }

    @Test
    fun optionalUnknownEventIsSafeAndStreamCompletes() {
        awaitConnection(ConnectionState.ONLINE)
        fake.config = FakeConfig(emitOptionalUnknown = true, replyWords = List(2) { "w$it " })
        val cid = repo.send("c1", "hello")
        awaitRequest(cid) { it == RequestStatus.Completed }
        assertEquals("w0 w1 ", repo.snapshot.value.session.requests[cid]!!.text)
        assertTrue(
            repo.snapshot.value.session.diagnostics.any {
                it.kind == com.jarvis.android.data.state.DiagnosticEntry.Kind.UNKNOWN_EVENT &&
                    it.detail.contains("hologram.started")
            },
        )
        assertEquals(ConnectionState.ONLINE, repo.snapshot.value.session.connection)
    }

    @Test
    fun protocolMismatchFailsClosed() {
        awaitConnection(ConnectionState.ONLINE)
        fake.config = FakeConfig(emitProtocolMismatchFirst = true)
        val cid = repo.send("c1", "hello")
        // mismatch arrives as first frame
        runBlockingShort {
            withTimeout(3_000) {
                while (repo.snapshot.value.phase != SessionPhase.MISMATCH) delay(10)
            }
        }
        assertEquals(ConnectionState.PROTOCOL_MISMATCH, repo.snapshot.value.session.connection)
        // request still optimistic-pending but session refuses to advance
        val st = repo.snapshot.value.session.requests[cid]!!.status
        assertTrue(st == RequestStatus.Pending || st.isTerminal)
    }

    @Test
    fun retryableFailureEndToEnd() {
        awaitConnection(ConnectionState.ONLINE)
        fake.config = FakeConfig(failMode = FakeFailMode.RETRYABLE, wordDelayMs = 30)
        val cid = repo.send("c1", "hello")
        val st = awaitRequest(cid) { it is RequestStatus.Failed } as RequestStatus.Failed
        assertTrue(st.retryable)
    }

    @Test
    fun approvalFlowEndToEnd() {
        awaitConnection(ConnectionState.ONLINE)
        fake.config = FakeConfig(requireApprovalOnRequest = true)
        repo.send("c1", "hello")
        // approval card appears
        runBlockingShort {
            withTimeout(3_000) {
                while (repo.snapshot.value.session.approvals.isEmpty()) delay(10)
            }
        }
        val approval = repo.snapshot.value.session.approvals.values.first()
        // double-resolve is idempotent client-side
        repo.resolveApproval(approval.approvalId, ApprovalOutcome.APPROVED)
        repo.resolveApproval(approval.approvalId, ApprovalOutcome.DENIED)
        // server resolves APPROVED
        runBlockingShort {
            withTimeout(3_000) {
                while (repo.snapshot.value.session.approvals.values.none { !it.resolutionInFlight }) delay(10)
            }
        }
        assertEquals(ApprovalOutcome.APPROVED, repo.snapshot.value.session.approvals.values.first().outcome)
        assertEquals(1, fake.receivedRequests.count { it is com.jarvis.android.contract.MobileRequest.ResolveApproval })
    }

    @Test
    fun taskProgressEndToEnd() {
        awaitConnection(ConnectionState.ONLINE)
        fake.config = FakeConfig(emitTaskOnRequest = true)
        repo.send("c1", "hello")
        runBlockingShort {
            withTimeout(3_000) {
                while (repo.snapshot.value.session.tasks.values.none { it.status == com.jarvis.android.contract.TaskStatus.COMPLETED }) delay(10)
            }
        }
        val task = repo.snapshot.value.session.tasks.values.first()
        assertEquals(com.jarvis.android.contract.TaskStatus.COMPLETED, task.status)
    }

    @Test
    fun reconnectDuringStreamingResumesAndCompletes() {
        awaitConnection(ConnectionState.ONLINE)
        fake.config = FakeConfig(wordDelayMs = 120, dropAfterMs = 350, dropReconnectMs = 150, replyWords = List(12) { "w$it " })
        val cid = repo.send("c1", "hello")
        // after drop+reconnect, replay from cursor re-delivers; then the stream continues
        awaitRequest(cid) { it == RequestStatus.Completed }
        val req = repo.snapshot.value.session.requests[cid]!!
        assertEquals(
            (0 until 12).joinToString(" ") { "w$it" } + " ",
            req.text,
        )
    }

    @Test
    fun replayFromCursorDoesNotDuplicateAlreadyDeliveredText() {
        awaitConnection(ConnectionState.ONLINE)
        fake.config = FakeConfig(replyWords = List(4) { "w$it " })
        val cid = repo.send("c1", "hello")
        awaitRequest(cid) { it == RequestStatus.Completed }
        val before = repo.snapshot.value.session.requests[cid]!!.text

        // Ask for a full replay from cursor 0: everything redelivered with same
        // eventIds -> reducer dedupes all of it.
        val token = repo.snapshot.value.session.lastCursorToken
        runBlockingShort { fake.send(com.jarvis.android.contract.MobileRequest.Replay("")) }
        check(token.isNotBlank())
        // give frames time to flow
        runBlockingShort { delay(200) }
        assertEquals(before, repo.snapshot.value.session.requests[cid]!!.text)
    }

    /**
     * Regression: snapshot writes used to be read-modify-write, so a command
     * landing between a frame's read and write silently dropped one of them.
     */
    @Test
    fun concurrentSendsAndFramesDoNotLoseState() {
        awaitConnection(ConnectionState.ONLINE)
        fake.config = FakeConfig(wordDelayMs = 1, replyWords = List(3) { "w$it " })
        val ids = mutableListOf<String>()
        runBlockingShort {
            val jobs = (0 until 20).map { i ->
                scope.launch { synchronized(ids) { ids += repo.send("c$i", "msg$i") } }
            }
            jobs.forEach { it.join() }
        }
        runBlockingShort {
            withTimeout(10_000) {
                while (repo.snapshot.value.session.requests.size < 20) delay(10)
            }
        }
        // every optimistic send survived concurrent frame processing
        assertEquals(20, repo.snapshot.value.session.requests.size)
        ids.forEach { assertNotNull(repo.snapshot.value.session.requests[it]) }
    }

    @Test
    fun repeatedApprovalResolvesSendExactlyOneCommand() {
        awaitConnection(ConnectionState.ONLINE)
        fake.config = FakeConfig(requireApprovalOnRequest = true, approveResolveDelayMs = 200)
        repo.send("c1", "hello")
        runBlockingShort {
            withTimeout(3_000) { while (repo.snapshot.value.session.approvals.isEmpty()) delay(10) }
        }
        val id = repo.snapshot.value.session.approvals.values.first().approvalId
        // hammer it from several coroutines at once
        runBlockingShort {
            (0 until 10).map { scope.launch { repo.resolveApproval(id, ApprovalOutcome.APPROVED) } }
                .forEach { it.join() }
            // commands reach the transport asynchronously; wait for the first,
            // then confirm no further duplicates arrive.
            withTimeout(3_000) {
                while (fake.receivedRequests.none { it is com.jarvis.android.contract.MobileRequest.ResolveApproval }) delay(10)
            }
            delay(300)
        }
        assertEquals(
            1,
            fake.receivedRequests.count { it is com.jarvis.android.contract.MobileRequest.ResolveApproval },
        )
    }

    @Test
    fun approvalResolutionNetworkDropThenReconnectRetriesExactlyOnce() {
        awaitConnection(ConnectionState.ONLINE)
        fake.config = FakeConfig(requireApprovalOnRequest = true, approveNeverResolves = true)
        repo.send("c1", "hello")
        runBlockingShort {
            withTimeout(3_000) { while (repo.snapshot.value.session.approvals.isEmpty()) delay(10) }
        }
        val id = repo.snapshot.value.session.approvals.values.first().approvalId

        // Drop the link, then tap Approve: the command cannot be delivered, so
        // the reducer must roll the approval back to unresolved (not stuck
        // in-flight), leaving room for a single retry.
        runBlockingShort { fake.disconnect() }
        repo.resolveApproval(id, ApprovalOutcome.APPROVED)
        runBlockingShort { delay(150) }
        val afterDrop = repo.snapshot.value.session.approvals[id]!!
        assertFalse("not stuck in flight after failed delivery", afterDrop.resolutionInFlight)
        assertEquals(0, fake.receivedRequests.count { it is com.jarvis.android.contract.MobileRequest.ResolveApproval })

        // Restore the link and retry once; it now reaches the transport exactly once.
        runBlockingShort { fake.connect() }
        awaitConnection(ConnectionState.ONLINE)
        repo.resolveApproval(id, ApprovalOutcome.APPROVED)
        runBlockingShort {
            withTimeout(3_000) {
                while (fake.receivedRequests.none { it is com.jarvis.android.contract.MobileRequest.ResolveApproval }) delay(10)
            }
        }
        assertEquals(
            1,
            fake.receivedRequests.count { it is com.jarvis.android.contract.MobileRequest.ResolveApproval },
        )
    }

    @Test
    fun attachmentUploadBecomesReady() {
        awaitConnection(ConnectionState.ONLINE)
        fake.config = FakeConfig(uploadDelayMs = 30)
        repo.uploadAttachment("att_x", "c1", "photo.jpg", "image/jpeg", 1234L)
        // uploading flag then ready
        runBlockingShort {
            withTimeout(3_000) {
                while (repo.snapshot.value.session.attachments["att_x"]?.ready != true) delay(10)
            }
        }
        val a = repo.snapshot.value.session.attachments["att_x"]!!
        assertTrue(a.ready)
        assertEquals(1234L, a.sizeBytes)
        assertEquals(1, fake.receivedRequests.count { it is com.jarvis.android.contract.MobileRequest.UploadAttachment })
    }

    @Test
    fun attachmentUploadFailureMarksFailedNotReady() {
        awaitConnection(ConnectionState.ONLINE)
        fake.config = FakeConfig(failUploads = true, uploadDelayMs = 20)
        repo.uploadAttachment("att_y", "c1", "photo.jpg", "image/jpeg", 99L)
        runBlockingShort {
            withTimeout(3_000) {
                while (repo.snapshot.value.session.attachments["att_y"]?.uploading == true) delay(10)
            }
        }
        val a = repo.snapshot.value.session.attachments["att_y"]!!
        assertTrue(!a.ready)
        assertNotNull(a.error)
    }

    @Test
    fun sendWhileDisconnectedFailsRetryableThenRetryWorks() {
        awaitConnection(ConnectionState.ONLINE)
        runBlockingShort { fake.disconnect() }
        val cid = repo.send("c1", "hello")
        val st = awaitRequest(cid) { it is RequestStatus.Failed } as RequestStatus.Failed
        assertTrue(st.retryable)

        runBlockingShort { fake.connect() }
        awaitConnection(ConnectionState.ONLINE)

        // Only retryable failures are offered for resend, and the resend is a new
        // request (ConversationRepository re-persists it; here we drive it directly).
        val retryable = repo.retryableRequest(cid)
        assertNotNull(retryable)
        repo.send(retryable!!.conversationId, retryable.userText)
        runBlockingShort {
            withTimeout(3_000) {
                while (repo.snapshot.value.session.requests.values.none { it.status == RequestStatus.Completed && it.userText == "hello" }) delay(20)
            }
        }
        assertNotNull(repo.snapshot.value.session.requests.values.first { it.status == RequestStatus.Completed })
    }
}
