```
TASK_ID: PCB-R2
ANDROID_REPO_SHA: working tree (uncommitted with R1/R3)
PC_A_BASELINE_SHA: 2a6ba24ab398356409f0448d6b13d63df8bb382a
SHARED_PROTOCOL: jarvis.web.v1 1.0
CHANGED_PATHS:
  app/src/main/java/com/jarvis/android/data/local/JarvisEntities.kt
  app/src/main/java/com/jarvis/android/data/local/JarvisDatabase.kt
  app/src/main/java/com/jarvis/android/data/repo/ConversationRepository.kt
  app/src/main/java/com/jarvis/android/data/repo/JarvisSessionRepository.kt
  app/schemas/com.jarvis.android.data.local.JarvisDatabase/3.json
  app/src/test/java/com/jarvis/android/data/local/CursorMigrationTest.kt
  app/src/test/java/com/jarvis/android/data/repo/ProcessDeathReconciliationTest.kt
TESTS:
  CursorMigrationTest 1
  ProcessDeathReconciliationTest 3
  (suite green with R1/R3)
RESULT: PASS
EVIDENCE:
  Room version 3. lastCursor INTEGER → lastCursorToken TEXT. 0 → empty string.
  Migration SQL shared with JVM sqlite-jdbc test; conversations/messages/pending preserved.
  Destructive fallback removed.
  Process death: opaque token restored, pending outbound resent with SAME clientRequestId,
  conversation A never appears in B, assistant text not duplicated.
KNOWN_LIMITATIONS:
  No explicit Migration(1,2); only 2→3 is required for this packet.
  Passwords / pairing keys still not stored (unchanged).
ROLLBACK: keep version 2 + lastCursor Long + fallbackToDestructiveMigration.
NEXT_TASK: PCB-R3
PC_A_DEPENDENCY: none
```
