package com.jarvis.android.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

interface AppStrings {
    // Navigation / Dock
    val navChat: String
    val navProjects: String
    val navApprovals: String
    val navTasks: String
    val navSettings: String

    // Connection HUD & Banner
    val statusOnline: String
    val statusDegraded: String
    val statusConnecting: String
    val statusReconnecting: String
    val statusOffline: String
    val statusDisconnected: String
    val statusAuthRequired: String
    val statusDeviceRevoked: String
    val statusUpdateRequired: String
    val retry: String
    val hintPcOffline: String
    val hintControlPlane: String
    fun activeCount(count: Int): String

    // Chat & Conversations
    val conversationsTitle: String
    val newChat: String
    val loadingConversations: String
    val readyWhenYouAre: String
    val startConversationSubtitle: String
    val savedPreferencesHint: String
    val quickPromptDraft: String
    val quickPromptBrainstorm: String
    val quickPromptStatus: String
    val messagePlaceholder: String
    val messagePlaceholderOffline: String
    val send: String
    val stop: String
    val generateImage: String
    val attachmentOptions: String
    val attachPhoto: String
    val attachDocument: String
    val copyMessage: String
    val messageCopied: String
    val messageStopping: String
    val messageCancelled: String
    val messageFailed: String
    val projectAll: String
    val projectWriting: String
    val projectActive: String
    val projectArchived: String
    val projectsEmptyBody: String
    val projectStudio: String
    val activeWorkspace: String
    val archivedWorkspace: String
    val noProjectConversations: String
    val renameProject: String
    val deleteProject: String
    val refreshProjects: String
    val newProject: String
    fun projectCount(count: Int): String
    fun noProjectsForFilter(filter: String): String
    val imageGenerationUnavailable: String
    val conversationOptions: String
    val deleteConversation: String
    val deleteConversationTitle: String
    val deleteConversationBody: String
    val deleteConversationFailed: String
    val cancel: String
    val imageAttachmentFailed: String
    val documentAttachmentFailed: String
    val someAttachmentsFailed: String
    val today: String
    val yesterday: String
    val thinking: String
    val back: String

    // Welcome & secure access
    val bootInitializing: String
    val bootLoading: String
    val bootRestoring: String
    val bootConnecting: String
    val appDescriptor: String
    fun welcomeBack(ownerName: String?): String
    val allSystemsReady: String
    fun secureClientVersion(version: Number): String
    val loginAgain: String
    val signInTitle: String
    val signInDescription: String
    val username: String
    val password: String
    val signIn: String
    val signingIn: String
    val privateConnection: String
    val diagnosticsTitle: String
    val hideDetails: String
    fun showDetails(count: Int): String
    val lockedTitle: String
    val lockedDescription: String
    val unlockAction: String
    val authenticationFailed: String
    val sessionLocked: String
    val sessionExpired: String
    val reauthDescription: String
    val renewingSession: String
    val pairAgain: String
    val authenticationUnavailable: String
    val revokedReason: String
    val expiredSessionReason: String
    val protocolMismatchReason: String

    // Approvals & Tasks
    val approvalsTitle: String
    val tasksTitle: String
    val taskProgressLabel: String
    val ownerOnlyTitle: String
    val ownerOnlyBody: String
    val nothingToApproveTitle: String
    val nothingToApproveBody: String
    val approve: String
    val deny: String
    val biometricRequired: String
    val biometricPromptTitle: String
    val biometricPromptCancel: String
    val resolving: String
    val resolved: String
    val expired: String
    fun expiresIn(secs: Long): String
    fun riskLevel(risk: String): String
    val noTasksTitle: String
    val noTasksBody: String

    // Settings
    val settingsTitle: String
    val languageSection: String
    val languageSubtitle: String
    val langSystem: String
    val langEnglish: String
    val langSpanish: String

    val usageSection: String
    val usageSettingsDescription: String
    val openUsage: String
    val usageTitle: String
    val usageObservedNotice: String
    val usageToday: String
    val usage7Days: String
    val usage30Days: String
    val refreshUsage: String
    val requestsLabel: String
    val successLabel: String
    val failedLabel: String
    val tokensLabel: String
    val tokensNotReported: String
    val providerQuotaNotReported: String
    val providerQuotaLive: String
    fun quotaPlan(plan: String): String
    fun quotaRemaining(percent: Int): String
    fun quotaResets(value: String): String
    val noUsageYet: String

    val identitySection: String
    val profilePhotoSet: String
    val noProfilePhoto: String
    val photoDescription: String
    val choosePhoto: String
    val removePhoto: String
    val deviceIsOwner: String
    val deviceIsOwnerSubtitle: String

    val securitySection: String
    val requireUnlock: String
    val requireUnlockSubtitle: String
    val deviceKeyNotice: String
    val revokeAndRepair: String

    val floatingBrainSection: String
    val floatingBubble: String
    val floatingBubbleGrantedSubtitle: String
    val floatingBubbleNeedsPermissionSubtitle: String
    val screenVisionOn: String
    val screenVisionOff: String

    val appearanceSection: String
    val reduceMotion: String
    val reduceMotionSubtitle: String
    val greetingName: String
    val saveName: String

    val appUpdateSection: String
    fun installedVersion(name: String, code: Number): String
    val updatesDeliveredNotice: String
    val checkForUpdate: String
    val checkingForUpdate: String
    val checking: String
    fun upToDate(version: String): String
    val checkAgain: String
    fun updateAvailable(name: String, code: Number): String
    val downloadUpdate: String
    fun downloading(name: String, percent: Int): String
    val downloadVerified: String
    val installUpdate: String
    val permissionRequiredNotice: String
    val allowInstalls: String
    val continueAction: String
    fun installerOpenedNotice(version: String): String
    val tryAgain: String

    val connectionSection: String
    val useFakeGateway: String
    val useFakeGatewaySubtitle: String
    val restartAppNotice: String
    val gatewayBaseUrl: String
    val saveUrl: String
    val checkHealth: String

    val simulationSection: String
    val simulationSubtitle: String
    val scenario: String
    val runScenarioNow: String

    // Writing Room & Story Wiki
    val workspaceOverview: String
    val workspaceWrite: String
    val workspaceChat: String
    val workspaceCanon: String
    val workspaceVisual: String
    val visualStudioTitle: String
    val visualCharacters: String
    val visualLocations: String
    val visualScenes: String
    val visualMap: String
    val writingRoomAnalyzing: String
    val writingRoomResponding: String
    val workspacePlan: String
    val workspaceWiki: String
    val workspaceLibrary: String
    val roomLore: String
    val roomCouncil: String
    val roomCanon: String
    val roomStudio: String
    fun canonConnected(docs: Int): String
    val liveCouncil: String
    val exploreByCategory: String
    val backToExplore: String
    val officialCanon: String
    val officialCanonSub: String
    val reference: String
    val referenceSub: String
    val approvedPlans: String
    val approvedPlansSub: String
    val proposals: String
    val proposalsSub: String
    val dismiss: String
    val searchLorePlaceholder: String
}

object EnAppStrings : AppStrings {
    override val navChat = "Chat"
    override val navProjects = "Projects"
    override val navApprovals = "Approvals"
    override val navTasks = "Tasks"
    override val navSettings = "Settings"

    override val statusOnline = "ONLINE"
    override val statusDegraded = "DEGRADED"
    override val statusConnecting = "CONNECTING"
    override val statusReconnecting = "RECONNECTING"
    override val statusOffline = "OFFLINE"
    override val statusDisconnected = "DISCONNECTED"
    override val statusAuthRequired = "AUTH REQUIRED"
    override val statusDeviceRevoked = "DEVICE REVOKED"
    override val statusUpdateRequired = "UPDATE REQUIRED"
    override val retry = "Retry"
    override val hintPcOffline = "PC offline — cloud still available"
    override val hintControlPlane = "Control plane unreachable"
    override fun activeCount(count: Int) = "$count ACTIVE"

    override val conversationsTitle = "Chat"
    override val newChat = "New chat"
    override val loadingConversations = "Loading conversations"
    override val readyWhenYouAre = "Ready when you are"
    override val startConversationSubtitle = "Start a conversation and I'll stream the reply here."
    override val savedPreferencesHint = "Saved preferences: /memory list · /memory set key = text · /memory delete key"
    override val quickPromptDraft = "Draft an update"
    override val quickPromptBrainstorm = "Brainstorm ideas"
    override val quickPromptStatus = "Check status"
    override val messagePlaceholder = "Message JARVIS…"
    override val messagePlaceholderOffline = "Offline — will retry"
    override val send = "Send"
    override val stop = "Stop"
    override val generateImage = "Generate image"
    override val attachmentOptions = "Attachment options"
    override val attachPhoto = "Photo"
    override val attachDocument = "Document"
    override val copyMessage = "Copy"
    override val messageCopied = "Copied"
    override val messageStopping = "Stopping…"
    override val messageCancelled = "Cancelled"
    override val messageFailed = "Failed"
    override val projectAll = "All"
    override val projectWriting = "Writing rooms"
    override val projectActive = "Active"
    override val projectArchived = "Archived"
    override val projectsEmptyBody = "Projects appear here when they are available to this account."
    override val projectStudio = "STUDIO"
    override val activeWorkspace = "ACTIVE WORKSPACE"
    override val archivedWorkspace = "ARCHIVED"
    override val noProjectConversations = "No conversations are linked to this project yet."
    override val renameProject = "Rename project"
    override val deleteProject = "Delete project"
    override val refreshProjects = "Refresh projects"
    override val newProject = "New project"
    override fun projectCount(count: Int) = if (count == 1) "1 project" else "$count projects"
    override fun noProjectsForFilter(filter: String) = "No projects match $filter."
    override val imageGenerationUnavailable = "Image generation unavailable"
    override val conversationOptions = "Conversation options"
    override val deleteConversation = "Delete"
    override val deleteConversationTitle = "Delete this conversation?"
    override val deleteConversationBody = "This removes its messages and attachments from Jarvis and this device. This action can't be undone."
    override val deleteConversationFailed = "Couldn't delete this conversation. Check your connection and try again."
    override val cancel = "Cancel"
    override val imageAttachmentFailed = "Couldn't read this image. Choose a supported photo and try again."
    override val documentAttachmentFailed = "Couldn't attach the file. Choose a TXT, MD, PDF, or DOCX file up to 2 MB."
    override val someAttachmentsFailed = "Some files couldn't be attached. Check their format and size."
    override val today = "Today"
    override val yesterday = "Yesterday"
    override val thinking = "Thinking…"
    override val back = "Back"

    override val bootInitializing = "Starting the core"
    override val bootLoading = "Loading capabilities"
    override val bootRestoring = "Restoring your workspace"
    override val bootConnecting = "Connecting to JARVIS"
    override val appDescriptor = "Your personal assistant"
    override fun welcomeBack(ownerName: String?) =
        if (ownerName.isNullOrBlank()) "Welcome back" else "Welcome back, $ownerName"
    override val allSystemsReady = "JARVIS is ready to help."
    override fun secureClientVersion(version: Number) = "Secure JARVIS app · version $version"
    override val loginAgain = "Sign in again"
    override val signInTitle = "Sign in to JARVIS"
    override val signInDescription = "Your private JARVIS connection is configured automatically."
    override val username = "Username"
    override val password = "Password"
    override val signIn = "Sign in"
    override val signingIn = "Signing in…"
    override val privateConnection = "Private connection"
    override val diagnosticsTitle = "Diagnostics"
    override val hideDetails = "Hide details"
    override fun showDetails(count: Int) = "Show details ($count)"
    override val lockedTitle = "JARVIS is locked"
    override val lockedDescription = "Authenticate to continue."
    override val unlockAction = "Unlock JARVIS"
    override val authenticationFailed = "Authentication failed or was cancelled."
    override val sessionLocked = "Session locked"
    override val sessionExpired = "Your JARVIS session expired"
    override val reauthDescription = "Use your fingerprint, face, or device unlock. No JARVIS password is needed."
    override val renewingSession = "Renewing secure session…"
    override val pairAgain = "Sign in again"
    override val authenticationUnavailable = "Authentication was cancelled or is unavailable on this device."
    override val revokedReason = "This device was revoked on the Jarvis PC. Sign in again to restore access."
    override val expiredSessionReason = "This device session expired and cannot be renewed. Sign in again to restore access."
    override val protocolMismatchReason = "This app is out of date for the Jarvis PC protocol. Update it, then sign in again."

    override val approvalsTitle = "Approvals"
    override val tasksTitle = "Tasks"
    override val taskProgressLabel = "Progress"
    override val ownerOnlyTitle = "Owner only"
    override val ownerOnlyBody = "PC-action approvals are visible only to the OWNER of this Jarvis installation. This device is signed in as a guest."
    override val nothingToApproveTitle = "Nothing to approve"
    override val nothingToApproveBody = "When JARVIS wants to act on your PC, the request appears here for you to allow or deny."
    override val approve = "Approve"
    override val deny = "Deny"
    override val biometricRequired = "Biometric confirmation required — not approved."
    override val biometricPromptTitle = "Confirm approval"
    override val biometricPromptCancel = "Cancel"
    override val resolving = "RESOLVING"
    override val resolved = "RESOLVED"
    override val expired = "EXPIRED"
    override fun expiresIn(secs: Long) = "EXPIRES IN ${secs}S"
    override fun riskLevel(risk: String) = "RISK ${risk.uppercase()}"
    override val noTasksTitle = "No active tasks"
    override val noTasksBody = "Background work dispatched to your PC appears here."

    override val settingsTitle = "Settings"
    override val languageSection = "Language"
    override val languageSubtitle = "Select interface language / Seleccionar idioma de la interfaz"
    override val langSystem = "System default / Predeterminado del sistema"
    override val langEnglish = "English (United States)"
    override val langSpanish = "Español (Latinoamérica)"

    override val usageSection = "Usage & limits"
    override val usageSettingsDescription = "View JARVIS-observed use for ChatGPT/Codex, Grok, Gemini, Nous and FreeLLMAPI."
    override val openUsage = "View usage"
    override val usageTitle = "Usage & limits"
    override val usageObservedNotice = "Traffic counters are observed by JARVIS. Live quota and renewal data appear only when the provider exposes them."
    override val usageToday = "Today"
    override val usage7Days = "7 days"
    override val usage30Days = "30 days"
    override val refreshUsage = "Refresh"
    override val requestsLabel = "Requests"
    override val successLabel = "Success"
    override val failedLabel = "Failed"
    override val tokensLabel = "Tokens"
    override val tokensNotReported = "Not reported"
    override val providerQuotaNotReported = "Provider quota not reported"
    override val providerQuotaLive = "LIVE PROVIDER QUOTA"
    override fun quotaPlan(plan: String) = "Plan: $plan"
    override fun quotaRemaining(percent: Int) = "$percent% remaining"
    override fun quotaResets(value: String) = "Resets $value"
    override val noUsageYet = "No observed usage in this period"

    override val identitySection = "Identity"
    override val profilePhotoSet = "Profile photo set"
    override val noProfilePhoto = "No profile photo"
    override val photoDescription = "Shown next to your messages. Stored only on this phone."
    override val choosePhoto = "Choose photo"
    override val removePhoto = "Remove"
    override val deviceIsOwner = "This device is the OWNER"
    override val deviceIsOwnerSubtitle = "Only the owner can see and resolve PC-action approvals"

    override val securitySection = "Security"
    override val requireUnlock = "Require unlock"
    override val requireUnlockSubtitle = "Ask for biometrics or device PIN each time the app opens"
    override val deviceKeyNotice = "The private key stays in the Android Keystore and is never exported."
    override val revokeAndRepair = "Revoke & re-pair"

    override val floatingBrainSection = "Floating brain"
    override val floatingBubble = "Floating bubble"
    override val floatingBubbleGrantedSubtitle = "Keep the brain on screen over other apps, and open the conversation from it"
    override val floatingBubbleNeedsPermissionSubtitle = "Needs permission to draw over other apps. You grant it once in system Settings."
    override val screenVisionOn = "Screen vision is on. It only reads or taps when you approve an action."
    override val screenVisionOff = "Screen vision is off. Enable it under Settings, Accessibility, if you want JARVIS to see and act on this screen — always with your approval first."

    override val appearanceSection = "Appearance"
    override val reduceMotion = "Reduce motion"
    override val reduceMotionSubtitle = "Turn off ambient glow, pulses and the boot animation"
    override val greetingName = "Greeting name"
    override val saveName = "Save name"

    override val appUpdateSection = "App update"
    override fun installedVersion(name: String, code: Number) = "Installed $name ($code)"
    override val updatesDeliveredNotice = "Updates are delivered directly from the official JARVIS GitHub release."
    override val checkForUpdate = "Check for update"
    override val checkingForUpdate = "Checking for update…"
    override val checking = "Checking…"
    override fun upToDate(version: String) = "JARVIS is up to date ($version)."
    override val checkAgain = "Check again"
    override fun updateAvailable(name: String, code: Number) = "Update available: $name ($code)"
    override val downloadUpdate = "Download update"
    override fun downloading(name: String, percent: Int) = "Downloading $name: $percent%"
    override val downloadVerified = "Download verified. Android will ask you to confirm the update."
    override val installUpdate = "Install update"
    override val permissionRequiredNotice = "Android needs permission for JARVIS to hand verified APKs to the installer."
    override val allowInstalls = "Allow installs"
    override val continueAction = "Continue"
    override fun installerOpenedNotice(version: String) = "Android installer opened for $version. Confirm Update to keep your data."
    override val tryAgain = "Try again"

    override val connectionSection = "Connection"
    override val useFakeGateway = "Use Fake Gateway"
    override val useFakeGatewaySubtitle = "Deterministic local scenarios — no network. Off = HTTP /api/v1"
    override val restartAppNotice = "Restart the app to apply a transport change."
    override val gatewayBaseUrl = "Gateway base URL"
    override val saveUrl = "Save URL"
    override val checkHealth = "Check health"

    override val simulationSection = "Simulation"
    override val simulationSubtitle = "Drives the deterministic scenario matrix without a live backend."
    override val scenario = "Scenario"
    override val runScenarioNow = "Run scenario now"

    override val workspaceOverview = "Overview"
    override val workspaceWrite = "Write"
    override val workspaceChat = "Copilot"
    override val workspaceCanon = "Canon"
    override val workspaceVisual = "Visual"
    override val visualStudioTitle = "Visual studio"
    override val visualCharacters = "Characters"
    override val visualLocations = "Locations"
    override val visualScenes = "Scenes"
    override val visualMap = "Map"
    override val writingRoomAnalyzing = "ANALYZING CANON"
    override val writingRoomResponding = "WRITING RESPONSE"
    override val workspacePlan = "Plans"
    override val workspaceWiki = "Wiki"
    override val workspaceLibrary = "Library"
    override val roomLore = "Lore"
    override val roomCouncil = "Council"
    override val roomCanon = "Canon"
    override val roomStudio = "Studio"
    override fun canonConnected(docs: Int) = "CANON CONNECTED · $docs DOCS"
    override val liveCouncil = "LIVE COUNCIL"
    override val exploreByCategory = "Explore by Category"
    override val backToExplore = "Back to Explore"
    override val officialCanon = "Official Canon"
    override val officialCanonSub = "Immutable events occurred"
    override val reference = "Reference"
    override val referenceSub = "Context, sources, and era"
    override val approvedPlans = "Approved Plans"
    override val approvedPlansSub = "Authorized future arcs"
    override val proposals = "Proposals"
    override val proposalsSub = "Draft ideas and analysis"
    override val dismiss = "Dismiss"
    override val searchLorePlaceholder = "Search characters, events, lore…"
}

object EsAppStrings : AppStrings {
    override val navChat = "Chat"
    override val navProjects = "Proyectos"
    override val navApprovals = "Aprobar"
    override val navTasks = "Tareas"
    override val navSettings = "Ajustes"

    override val statusOnline = "EN LÍNEA"
    override val statusDegraded = "DEGRADADO"
    override val statusConnecting = "CONECTANDO"
    override val statusReconnecting = "RECONECTANDO"
    override val statusOffline = "DESCONECTADO"
    override val statusDisconnected = "DESCONECTADO"
    override val statusAuthRequired = "AUTENTICACIÓN REQUERIDA"
    override val statusDeviceRevoked = "DISPOSITIVO REVOCADO"
    override val statusUpdateRequired = "ACTUALIZACIÓN REQUERIDA"
    override val retry = "Reintentar"
    override val hintPcOffline = "PC fuera de línea — nube disponible"
    override val hintControlPlane = "Plano de control inaccesible"
    override fun activeCount(count: Int) = "$count ACTIVAS"

    override val conversationsTitle = "Conversaciones"
    override val newChat = "Nuevo chat"
    override val loadingConversations = "Cargando conversaciones"
    override val readyWhenYouAre = "Listo cuando tú lo estés"
    override val startConversationSubtitle = "Inicia una conversación y transmitiré la respuesta aquí."
    override val savedPreferencesHint = "Preferencias guardadas: /memory list · /memory set clave = texto · /memory delete clave"
    override val quickPromptDraft = "Redactar actualización"
    override val quickPromptBrainstorm = "Lluvia de ideas"
    override val quickPromptStatus = "Verificar estado"
    override val messagePlaceholder = "Mensaje a JARVIS…"
    override val messagePlaceholderOffline = "Desconectado — reintentará"
    override val send = "Enviar"
    override val stop = "Detener"
    override val generateImage = "Generar imagen"
    override val attachmentOptions = "Opciones de adjuntos"
    override val attachPhoto = "Foto"
    override val attachDocument = "Documento"
    override val copyMessage = "Copiar"
    override val messageCopied = "Copiado"
    override val messageStopping = "Deteniendo…"
    override val messageCancelled = "Cancelado"
    override val messageFailed = "Falló"
    override val projectAll = "Todos"
    override val projectWriting = "Espacios de escritura"
    override val projectActive = "Activos"
    override val projectArchived = "Archivados"
    override val projectsEmptyBody = "Los proyectos disponibles para esta cuenta aparecerán aquí."
    override val projectStudio = "ESTUDIO"
    override val activeWorkspace = "ESPACIO ACTIVO"
    override val archivedWorkspace = "ARCHIVADO"
    override val noProjectConversations = "Este proyecto aún no tiene conversaciones vinculadas."
    override val renameProject = "Cambiar nombre del proyecto"
    override val deleteProject = "Eliminar proyecto"
    override val refreshProjects = "Actualizar proyectos"
    override val newProject = "Nuevo proyecto"
    override fun projectCount(count: Int) = if (count == 1) "1 proyecto" else "$count proyectos"
    override fun noProjectsForFilter(filter: String) = "No hay proyectos en «$filter»."
    override val imageGenerationUnavailable = "Generación de imagen no disponible"
    override val conversationOptions = "Opciones de conversación"
    override val deleteConversation = "Eliminar"
    override val deleteConversationTitle = "¿Eliminar esta conversación?"
    override val deleteConversationBody = "Se eliminarán sus mensajes y archivos adjuntos de Jarvis y de este dispositivo. Esta acción no se puede deshacer."
    override val deleteConversationFailed = "No se pudo eliminar la conversación. Revisa tu conexión e inténtalo de nuevo."
    override val cancel = "Cancelar"
    override val imageAttachmentFailed = "No se pudo leer la imagen. Elige una foto compatible y vuelve a intentarlo."
    override val documentAttachmentFailed = "No se pudo adjuntar. Elige un TXT, MD, PDF o DOCX de hasta 2 MB."
    override val someAttachmentsFailed = "Algunos archivos no se pudieron adjuntar. Revisa el formato y el tamaño."
    override val today = "Hoy"
    override val yesterday = "Ayer"
    override val thinking = "Pensando…"
    override val back = "Atrás"

    override val bootInitializing = "Iniciando el núcleo"
    override val bootLoading = "Cargando capacidades"
    override val bootRestoring = "Restaurando tu espacio de trabajo"
    override val bootConnecting = "Conectando con JARVIS"
    override val appDescriptor = "Tu asistente personal"
    override fun welcomeBack(ownerName: String?) =
        if (ownerName.isNullOrBlank()) "Qué bueno tenerte de vuelta" else "Qué bueno tenerte de vuelta, $ownerName"
    override val allSystemsReady = "JARVIS está listo para ayudarte."
    override fun secureClientVersion(version: Number) = "App segura de JARVIS · versión $version"
    override val loginAgain = "Iniciar sesión de nuevo"
    override val signInTitle = "Inicia sesión en JARVIS"
    override val signInDescription = "Tu conexión privada con JARVIS se configura automáticamente."
    override val username = "Usuario"
    override val password = "Contraseña"
    override val signIn = "Iniciar sesión"
    override val signingIn = "Iniciando sesión…"
    override val privateConnection = "Conexión privada"
    override val diagnosticsTitle = "Diagnóstico"
    override val hideDetails = "Ocultar detalles"
    override fun showDetails(count: Int) = "Ver detalles ($count)"
    override val lockedTitle = "JARVIS está bloqueado"
    override val lockedDescription = "Autentícate para continuar."
    override val unlockAction = "Desbloquear JARVIS"
    override val authenticationFailed = "La autenticación falló o se canceló."
    override val sessionLocked = "Sesión bloqueada"
    override val sessionExpired = "Tu sesión de JARVIS venció"
    override val reauthDescription = "Usa tu huella, rostro o el desbloqueo del dispositivo. No necesitas la contraseña de JARVIS."
    override val renewingSession = "Renovando la sesión segura…"
    override val pairAgain = "Iniciar sesión de nuevo"
    override val authenticationUnavailable = "La autenticación se canceló o no está disponible en este dispositivo."
    override val revokedReason = "Este dispositivo fue revocado en la PC de Jarvis. Inicia sesión de nuevo para recuperar el acceso."
    override val expiredSessionReason = "La sesión de este dispositivo venció y no se puede renovar. Inicia sesión de nuevo para recuperar el acceso."
    override val protocolMismatchReason = "Esta app no es compatible con el protocolo de la PC de Jarvis. Actualízala y vuelve a iniciar sesión."

    override val approvalsTitle = "Aprobaciones"
    override val tasksTitle = "Tareas"
    override val taskProgressLabel = "Progreso"
    override val ownerOnlyTitle = "Solo propietario"
    override val ownerOnlyBody = "Las aprobaciones de acciones de PC solo son visibles para el PROPIETARIO de esta instalación de Jarvis. Este dispositivo ha iniciado sesión como invitado."
    override val nothingToApproveTitle = "Nada que aprobar"
    override val nothingToApproveBody = "Cuando JARVIS desee actuar en tu PC, la solicitud aparecerá aquí para que la apruebes o rechaces."
    override val approve = "Aprobar"
    override val deny = "Rechazar"
    override val biometricRequired = "Confirmación biométrica requerida — no aprobada."
    override val biometricPromptTitle = "Confirmar aprobación"
    override val biometricPromptCancel = "Cancelar"
    override val resolving = "RESOLVIENDO"
    override val resolved = "RESUELTO"
    override val expired = "EXPIRADO"
    override fun expiresIn(secs: Long) = "EXPIRA EN ${secs}S"
    override fun riskLevel(risk: String) = "RIESGO ${risk.uppercase()}"
    override val noTasksTitle = "Sin tareas activas"
    override val noTasksBody = "El trabajo en segundo plano enviado a tu PC aparecerá aquí."

    override val settingsTitle = "Ajustes"
    override val languageSection = "Idioma"
    override val languageSubtitle = "Seleccionar idioma de la interfaz / Select interface language"
    override val langSystem = "Predeterminado del sistema / System default"
    override val langEnglish = "English (United States)"
    override val langSpanish = "Español (Latinoamérica)"

    override val usageSection = "Uso y límites"
    override val usageSettingsDescription = "Consulta el uso observado por JARVIS para ChatGPT/Codex, Grok, Gemini, Nous y FreeLLMAPI."
    override val openUsage = "Ver uso"
    override val usageTitle = "Uso y límites"
    override val usageObservedNotice = "Los contadores de tráfico son observados por JARVIS. La cuota y renovación reales aparecen solo cuando el proveedor las expone."
    override val usageToday = "Hoy"
    override val usage7Days = "7 días"
    override val usage30Days = "30 días"
    override val refreshUsage = "Actualizar"
    override val requestsLabel = "Solicitudes"
    override val successLabel = "Correctas"
    override val failedLabel = "Fallidas"
    override val tokensLabel = "Tokens"
    override val tokensNotReported = "No reportados"
    override val providerQuotaNotReported = "Cuota del proveedor no reportada"
    override val providerQuotaLive = "CUOTA REAL DEL PROVEEDOR"
    override fun quotaPlan(plan: String) = "Plan: $plan"
    override fun quotaRemaining(percent: Int) = "$percent% restante"
    override fun quotaResets(value: String) = "Se restablece $value"
    override val noUsageYet = "Sin uso observado en este periodo"

    override val identitySection = "Identidad"
    override val profilePhotoSet = "Foto de perfil configurada"
    override val noProfilePhoto = "Sin foto de perfil"
    override val photoDescription = "Se muestra junto a tus mensajes. Almacenada solo en este teléfono."
    override val choosePhoto = "Elegir foto"
    override val removePhoto = "Quitar"
    override val deviceIsOwner = "Este dispositivo es el PROPIETARIO"
    override val deviceIsOwnerSubtitle = "Solo el propietario puede ver y resolver aprobaciones de acciones de PC"

    override val securitySection = "Seguridad"
    override val requireUnlock = "Requerir desbloqueo"
    override val requireUnlockSubtitle = "Solicitar biometría o PIN cada vez que se abre la app"
    override val deviceKeyNotice = "La clave privada permanece en Android Keystore y nunca se exporta."
    override val revokeAndRepair = "Revocar y revincular"

    override val floatingBrainSection = "Cerebro flotante"
    override val floatingBubble = "Burbuja flotante"
    override val floatingBubbleGrantedSubtitle = "Mantén el cerebro en pantalla sobre otras apps y abre la conversación desde allí"
    override val floatingBubbleNeedsPermissionSubtitle = "Requiere permiso para mostrarse sobre otras apps. Se otorga en Ajustes del sistema."
    override val screenVisionOn = "Visión de pantalla activada. Solo lee o presiona cuando apruebas una acción."
    override val screenVisionOff = "Visión de pantalla desactivada. Actívala en Ajustes, Accesibilidad, si deseas que JARVIS vea y actúe en esta pantalla — siempre con tu previa aprobación."

    override val appearanceSection = "Apariencia"
    override val reduceMotion = "Reducir movimiento"
    override val reduceMotionSubtitle = "Desactiva el brillo ambiental, pulsos y animación de inicio"
    override val greetingName = "Nombre de saludo"
    override val saveName = "Guardar nombre"

    override val appUpdateSection = "Actualización de la app"
    override fun installedVersion(name: String, code: Number) = "Instalado $name ($code)"
    override val updatesDeliveredNotice = "Las actualizaciones se descargan directamente desde la release oficial de JARVIS en GitHub."
    override val checkForUpdate = "Buscar actualizaciones"
    override val checkingForUpdate = "Buscando actualizaciones…"
    override val checking = "Buscando…"
    override fun upToDate(version: String) = "JARVIS está actualizado ($version)."
    override val checkAgain = "Buscar de nuevo"
    override fun updateAvailable(name: String, code: Number) = "Actualización disponible: $name ($code)"
    override val downloadUpdate = "Descargar actualización"
    override fun downloading(name: String, percent: Int) = "Descargando $name: $percent%"
    override val downloadVerified = "Descarga verificada. Android te pedirá confirmar la actualización."
    override val installUpdate = "Instalar actualización"
    override val permissionRequiredNotice = "Android necesita permiso para que JARVIS entregue APKs verificadas al instalador."
    override val allowInstalls = "Permitir instalaciones"
    override val continueAction = "Continuar"
    override fun installerOpenedNotice(version: String) = "Instalador de Android abierto para $version. Confirma Actualizar para mantener tus datos."
    override val tryAgain = "Reintentar"

    override val connectionSection = "Conexión"
    override val useFakeGateway = "Usar pasarela simulada"
    override val useFakeGatewaySubtitle = "Escenarios locales deterministas — sin red. Desactivado = HTTP /api/v1"
    override val restartAppNotice = "Reinicia la aplicación para aplicar un cambio de transporte."
    override val gatewayBaseUrl = "URL base de la pasarela"
    override val saveUrl = "Guardar URL"
    override val checkHealth = "Verificar estado"

    override val simulationSection = "Simulación"
    override val simulationSubtitle = "Controla la matriz de escenarios deterministas sin backend en vivo."
    override val scenario = "Escenario"
    override val runScenarioNow = "Ejecutar escenario ahora"

    override val workspaceOverview = "Resumen"
    override val workspaceWrite = "Escribir"
    override val workspaceChat = "Copiloto"
    override val workspaceCanon = "Canon"
    override val workspaceVisual = "Visual"
    override val visualStudioTitle = "Estudio visual"
    override val visualCharacters = "Personajes"
    override val visualLocations = "Lugares"
    override val visualScenes = "Escenas"
    override val visualMap = "Mapa"
    override val writingRoomAnalyzing = "ANALIZANDO CANON"
    override val writingRoomResponding = "REDACTANDO RESPUESTA"
    override val workspacePlan = "Planes"
    override val workspaceWiki = "Wiki"
    override val workspaceLibrary = "Biblioteca"
    override val roomLore = "Lore"
    override val roomCouncil = "Consejo"
    override val roomCanon = "Canon"
    override val roomStudio = "Estudio"
    override fun canonConnected(docs: Int) = "CANON CONECTADO · $docs DOCS"
    override val liveCouncil = "CONSEJO EN VIVO"
    override val exploreByCategory = "Explorar por categoría"
    override val backToExplore = "Volver a explorar"
    override val officialCanon = "Canon Oficial"
    override val officialCanonSub = "Hechos inmutables ocurridos"
    override val reference = "Referencia"
    override val referenceSub = "Contexto, fuentes y época"
    override val approvedPlans = "Planes Aprobados"
    override val approvedPlansSub = "Arcos futuros autorizados"
    override val proposals = "Propuestas"
    override val proposalsSub = "Ideas en borrador y análisis"
    override val dismiss = "Descartar"
    override val searchLorePlaceholder = "Buscar personajes, eventos, lore…"
}

fun resolveAppStrings(languageSetting: String): AppStrings {
    return when (languageSetting.lowercase(Locale.ROOT)) {
        "es" -> EsAppStrings
        "en" -> EnAppStrings
        else -> {
            val systemLanguage = Locale.getDefault().language
            if (systemLanguage.startsWith("es", ignoreCase = true)) EsAppStrings else EnAppStrings
        }
    }
}

val LocalAppStrings = staticCompositionLocalOf<AppStrings> { EnAppStrings }
