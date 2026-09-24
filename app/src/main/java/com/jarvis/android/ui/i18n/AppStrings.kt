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
    val quickPromptDraft: String
    val quickPromptBrainstorm: String
    val quickPromptStatus: String
    val messagePlaceholder: String
    val messagePlaceholderOffline: String
    val send: String
    val stop: String
    val generateImage: String
    val imageGenerationUnavailable: String
    val deleteConversation: String
    val today: String
    val yesterday: String
    val thinking: String
    val back: String

    // Approvals & Tasks
    val approvalsTitle: String
    val tasksTitle: String
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
    override val quickPromptDraft = "Draft an update"
    override val quickPromptBrainstorm = "Brainstorm ideas"
    override val quickPromptStatus = "Check status"
    override val messagePlaceholder = "Message JARVIS…"
    override val messagePlaceholderOffline = "Offline — will retry"
    override val send = "Send"
    override val stop = "Stop"
    override val generateImage = "Generate image"
    override val imageGenerationUnavailable = "Image generation unavailable"
    override val deleteConversation = "Delete"
    override val today = "Today"
    override val yesterday = "Yesterday"
    override val thinking = "Thinking…"
    override val back = "Back"

    override val approvalsTitle = "Approvals"
    override val tasksTitle = "Tasks"
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
    override val languageSection = "LANGUAGE / IDIOMA"
    override val languageSubtitle = "Select interface language / Seleccionar idioma de la interfaz"
    override val langSystem = "System default / Predeterminado del sistema"
    override val langEnglish = "English (United States)"
    override val langSpanish = "Español (Latinoamérica)"

    override val usageSection = "AI USAGE & LIMITS"
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

    override val identitySection = "IDENTITY"
    override val profilePhotoSet = "Profile photo set"
    override val noProfilePhoto = "No profile photo"
    override val photoDescription = "Shown next to your messages. Stored only on this phone."
    override val choosePhoto = "Choose photo"
    override val removePhoto = "Remove"
    override val deviceIsOwner = "This device is the OWNER"
    override val deviceIsOwnerSubtitle = "Only the owner can see and resolve PC-action approvals"

    override val securitySection = "SECURITY"
    override val requireUnlock = "Require unlock"
    override val requireUnlockSubtitle = "Ask for biometrics or device PIN each time the app opens"
    override val deviceKeyNotice = "The private key stays in the Android Keystore and is never exported."
    override val revokeAndRepair = "Revoke & re-pair"

    override val floatingBrainSection = "FLOATING BRAIN"
    override val floatingBubble = "Floating bubble"
    override val floatingBubbleGrantedSubtitle = "Keep the brain on screen over other apps, and open the conversation from it"
    override val floatingBubbleNeedsPermissionSubtitle = "Needs permission to draw over other apps. You grant it once in system Settings."
    override val screenVisionOn = "Screen vision is on. It only reads or taps when you approve an action."
    override val screenVisionOff = "Screen vision is off. Enable it under Settings, Accessibility, if you want JARVIS to see and act on this screen — always with your approval first."

    override val appearanceSection = "APPEARANCE"
    override val reduceMotion = "Reduce motion"
    override val reduceMotionSubtitle = "Turn off ambient glow, pulses and the boot animation"
    override val greetingName = "Greeting name"
    override val saveName = "Save name"

    override val appUpdateSection = "APP UPDATE"
    override fun installedVersion(name: String, code: Number) = "Installed $name ($code)"
    override val updatesDeliveredNotice = "Updates are delivered through your private JARVIS VPS."
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

    override val connectionSection = "CONNECTION"
    override val useFakeGateway = "Use Fake Gateway"
    override val useFakeGatewaySubtitle = "Deterministic local scenarios — no network. Off = HTTP /api/v1"
    override val restartAppNotice = "Restart the app to apply a transport change."
    override val gatewayBaseUrl = "Gateway base URL"
    override val saveUrl = "Save URL"
    override val checkHealth = "Check health"

    override val simulationSection = "SIMULATION"
    override val simulationSubtitle = "Drives the deterministic scenario matrix without a live backend."
    override val scenario = "Scenario"
    override val runScenarioNow = "Run scenario now"

    override val workspaceOverview = "Overview"
    override val workspaceWrite = "Write"
    override val workspaceChat = "Copilot"
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
    override val quickPromptDraft = "Redactar actualización"
    override val quickPromptBrainstorm = "Lluvia de ideas"
    override val quickPromptStatus = "Verificar estado"
    override val messagePlaceholder = "Mensaje a JARVIS…"
    override val messagePlaceholderOffline = "Desconectado — reintentará"
    override val send = "Enviar"
    override val stop = "Detener"
    override val generateImage = "Generar imagen"
    override val imageGenerationUnavailable = "Generación de imagen no disponible"
    override val deleteConversation = "Eliminar"
    override val today = "Hoy"
    override val yesterday = "Ayer"
    override val thinking = "Pensando…"
    override val back = "Atrás"

    override val approvalsTitle = "Aprobaciones"
    override val tasksTitle = "Tareas"
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
    override val languageSection = "LANGUAGE / IDIOMA"
    override val languageSubtitle = "Seleccionar idioma de la interfaz / Select interface language"
    override val langSystem = "Predeterminado del sistema / System default"
    override val langEnglish = "English (United States)"
    override val langSpanish = "Español (Latinoamérica)"

    override val usageSection = "USO Y LÍMITES DE IA"
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

    override val identitySection = "IDENTIDAD"
    override val profilePhotoSet = "Foto de perfil configurada"
    override val noProfilePhoto = "Sin foto de perfil"
    override val photoDescription = "Se muestra junto a tus mensajes. Almacenada solo en este teléfono."
    override val choosePhoto = "Elegir foto"
    override val removePhoto = "Quitar"
    override val deviceIsOwner = "Este dispositivo es el PROPIETARIO"
    override val deviceIsOwnerSubtitle = "Solo el propietario puede ver y resolver aprobaciones de acciones de PC"

    override val securitySection = "SEGURIDAD"
    override val requireUnlock = "Requerir desbloqueo"
    override val requireUnlockSubtitle = "Solicitar biometría o PIN cada vez que se abre la app"
    override val deviceKeyNotice = "La clave privada permanece en Android Keystore y nunca se exporta."
    override val revokeAndRepair = "Revocar y revincular"

    override val floatingBrainSection = "CEREBRO FLOTANTE"
    override val floatingBubble = "Burbuja flotante"
    override val floatingBubbleGrantedSubtitle = "Mantén el cerebro en pantalla sobre otras apps y abre la conversación desde allí"
    override val floatingBubbleNeedsPermissionSubtitle = "Requiere permiso para mostrarse sobre otras apps. Se otorga en Ajustes del sistema."
    override val screenVisionOn = "Visión de pantalla activada. Solo lee o presiona cuando apruebas una acción."
    override val screenVisionOff = "Visión de pantalla desactivada. Actívala en Ajustes, Accesibilidad, si deseas que JARVIS vea y actúe en esta pantalla — siempre con tu previa aprobación."

    override val appearanceSection = "APARIENCIA"
    override val reduceMotion = "Reducir movimiento"
    override val reduceMotionSubtitle = "Desactiva el brillo ambiental, pulsos y animación de inicio"
    override val greetingName = "Nombre de saludo"
    override val saveName = "Guardar nombre"

    override val appUpdateSection = "ACTUALIZACIÓN DE LA APP"
    override fun installedVersion(name: String, code: Number) = "Instalado $name ($code)"
    override val updatesDeliveredNotice = "Las actualizaciones se distribuyen a través de tu VPS privado de JARVIS."
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

    override val connectionSection = "CONEXIÓN"
    override val useFakeGateway = "Usar pasarela simulada"
    override val useFakeGatewaySubtitle = "Escenarios locales deterministas — sin red. Desactivado = HTTP /api/v1"
    override val restartAppNotice = "Reinicia la aplicación para aplicar un cambio de transporte."
    override val gatewayBaseUrl = "URL base de la pasarela"
    override val saveUrl = "Guardar URL"
    override val checkHealth = "Verificar estado"

    override val simulationSection = "SIMULACIÓN"
    override val simulationSubtitle = "Controla la matriz de escenarios deterministas sin backend en vivo."
    override val scenario = "Escenario"
    override val runScenarioNow = "Ejecutar escenario ahora"

    override val workspaceOverview = "Resumen"
    override val workspaceWrite = "Escribir"
    override val workspaceChat = "Copiloto"
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
