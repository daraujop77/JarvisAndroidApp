# Copilot A5.5 Android — chapter draft + review from chat

Branch candidate; NOT a released APK.

Explicit full-chapter writing intents route to the same Copilot tool planner even when the technical Agent chip is off. Other writing conversations, scene prose and portraits retain their existing routes.

The new tool `draft_and_review_current_chapter` uses one owner-reviewed action and the existing A4 coordinator that creates a W2 chapter draft when needed and then reviews it. Its server-side resolver chooses only a uniquely eligible **unfinished** chapter with frozen approved Brief. Ambiguous/no ready chapter produces a bounded response and does not call the writing model. No technical chapter ID is required for the unambiguous case.

The confirmation card explicitly warns about cloud token costs and never claims that the resulting draft is approved or published. Polling/recovery reuses existing durable Copilot task and chapter A4 live_progress state. Do not generate an APK at this CI checkpoint.

Acceptance: check natural requests, no accidental routing of discussion/scene/image requests, exact owner permission, no duplicate writer, error/ambiguous recovery, no auto-canon. Backend PR must be merged/deployed and externally verified before merging Android.
