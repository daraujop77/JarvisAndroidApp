# Writing Room final review: late image responses

Date: 2026-10-10 (America/Chicago)
Android main base: d667896612a2c798c39c65b7b1da6cb82e1278f8; main CI #38104749798 SUCCESS.

## Reproduced risk in source

Character Studio already isolated late paid bulk-master completions,
but manual single-character generation, multi-view approval, and Reference
Pack approval could still mutate the UI after switching project or
character. Copilot portrait generation/approval could similarly mutate a
newly opened project's chat with old-project failure/success details.

## Correction

Capture the authenticated project/character and scope version or Copilot
recovery epoch before issuing an operation. Verify scope after network
completion and before updating UI state. Also check owner client-side
before costly portrait dispatch/approval, while retaining authoritative
VPS permission checks. Discard a staged Copilot thumbnail when the
project is switched during decoding.

The VPS-approved asset and paid generation remain durable; there are no
hidden retries, cross-project UI updates or automatic approvals.
Contract tests cover the scoped visual request handlers.

## Still requires user acceptance

Code-level tests do not prove phone UI usability, actual image quality,
matching face/silhouette/style across generated images, chapter/scene
canon accuracy, or child account permissions. Keep the final APK gate
closed until human acceptance of these areas. No paid provider called
in this patch or its CI.
