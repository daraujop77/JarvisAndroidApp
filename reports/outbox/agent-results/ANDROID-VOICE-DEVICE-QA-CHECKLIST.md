# Voice V0 device QA checklist

Do not install this over the frozen Human Check APK.

Install only: `dist/JARVIS-voice-v0-qa.apk`
SHA-256: `D084CD10C4AC99EBED363943276206853FB0F1956C7CB9B3206F03D329CFD830`
Size: 20738118 bytes
Built from: `e2bfba69b10e07a754c630cd3ee302ea3e915ec6`

Frozen baseline, do not replace:
`dist/JARVIS-debug.apk`
SHA-256: `BCD24B4D27686AFB7ECD9434E080DCC40C1307DFAF979520CE7F5AE8B180C961`

This checklist is preparation only. It is not a physical Human Check and it is not PC-A validation.

1. Microphone permission. First push-to-talk requests RECORD_AUDIO. Deny stays idle and does not loop. Grant starts listening once.
2. Push-to-talk. Idle to listening to transcript review. A second tap while listening cancels. Explicit send is the only path into chat.
3. Recognition. Partial text stays editable. Final text is reviewable. On-device unavailable does not fall back to a network recognizer.
4. TTS. A new assistant completion speaks once while chat is visible and the app is foreground. Secrets and disabled TTS stay silent.
5. Foreground and chat visibility. Leaving chat or backgrounding stops speech. A completion that arrives while hidden is not spoken and is not queued.
6. onStop silence. Background, Settings, Projects, Tasks, and the conversation list stop current speech and do not restart it.
7. No replay after return. Re-entering chat or recreating the screen does not speak history. A completion that happens after return may speak.
8. Reconnect. A network drop while voice is local does not send audio. Existing text-chat reconnect still applies after an explicit send. No second inference.
