# Writing Room Copilot A5.3 — escenas completas en el chat (2026-10-09)

## Contrato y alcance
Continúa la misma arquitectura de Copilot A5/A5.1/A5.2. Esta rama Android no agrega coordinadores, proveedores, rutas VPS ni generación oculta. Reutiliza los trabajos durables del servidor A5.2, el registro de Visual Studio, las rutas `visual-assets/list`/`fetch`/`approve-exact` y la edición con parent aprobado ya implementada en Image Studio.

**Flujo del autor desde la pestaña de chat:**
1. En `Agente con herramientas`, pedir preparación de escena basada en evidencia del canon. Autor revisa y confirma la preparación. Si hay ambigüedad, escoger el pasaje exacto.
2. Solicitar generación con `context_id`, `context_hash`, motor local/cloud; autor confirma costo. A5.2 crea un trabajo durable en el VPS y conserva el mismo `vsj_...`.
3. A5.3 lee `tasks/status` (cada 4 segundos mientras haya trabajo, con tope de 8 minutos) y **no reenvía ninguna generación**. Si el estado llega a `READY_FOR_REVIEW`, consulta la lista de assets del proyecto y descarga **solo el candidato vinculado al trabajo**, con comprobación SHA256, MIME permitido, storage y permisos. Lo muestra como imagen dentro del mismo chat, sin trasladarse a Scene Builder.
4. **Aprobar esta imagen** verifica el SHA y estado exacto del candidato y llama `visualAssetApproveExact` (owner-only). Cambia estatus del asset en Visual Studio; **no modifica hechos narrativos, capítulos ni canon automáticamente**.
5. Después de aprobar, el autor puede escribir una corrección en el chat. Pulsar **Generar corrección · puede tener costo** valida que el parent `SCENE_ART` esté aprobado, recarga metadatos y bytes SHA-verified, y usa `editSceneVisualAssetImage` (API existente, gpt-image-2-medium) para crear un nuevo hijo CANDIDATE que se previsualiza en el mismo chat. La imagen anterior permanece aprobada hasta una nueva aprobación. Si hay timeout o resultado dudoso, bloqueo fail-closed `copilotSceneCorrectionUnknown`: no reintento automático.
6. Tras reiniciar Android, `tasks/latest` recupera el job; `selectCopilotSceneReviewAsset` reconstruye revisiones hijas usando `parent_asset_id` y `parent_sha256`, solo en el mismo proyecto y con imágenes almacenadas y verificadas. Se selecciona la más reciente válida, no un retrato ajeno.

## Privacidad, seguridad y alcance
- Se usa `settings.isOwner`, tarea activa `task_id`, `job_id` y `rootAssetId` del VPS. No se permite cambiar arbitrariamente el asset objetivo desde una cadena generada por el modelo.
- Cada bytes de imagen procede de fetch autenticado; antes de mostrar se compara el hash con el registro y `stageVisualAssetBase64` vuelve a verificar SHA256. Android descarta thumbs al cambiar de proyecto o recuperar otra tarjeta.
- No se aprueba automáticamente por recibir `READY_FOR_REVIEW`. La aprobación requiere un gesto humano y verificación del server.
- Las modificaciones siempre salen de un parent `APPROVED`, no de un candidato inestable. Esto es un requisito del Image Studio existente; si el autor rechaza el primer candidato, puede conservarlo como candidato y generar otra escena explícitamente.
- La edición visual puede consumir créditos y es una operación sin garantía de replay. No se hacen llamadas pagadas en CI ni se crea APK por ciclo.
- Límite actual: la revisión de imagen se hace mediante un cuadro de instrucción dentro de Copilot, **no todavía mediante otra llamada automática al planner**; las instrucciones en lenguaje natural siguen sirviendo para preparar y arrancar jobs. La edición mantiene el mismo `SCENE_ART` y relaciones de escena/capítulo del parent, pero no concede aprobaciones de canon narrativo.
- La redacción inicial de capítulos ya está integrada en el A4 auto-review existente (`ensure_initial_draft`), invocable desde la herramienta `start_chapter_auto_review` de A5.2. A5.3 no añade un segundo escritor y conserva aprobación humana.

## Pruebas
- `CopilotSceneCandidatesTest`: reconstrucción exacta de parent→child, filtros de proyecto, visual kind, checksum y storage pendiente.
- `CopilotAgentWiringTest`: uso de HTTPS autenticado de VPS, polling no re-dispatch, thumbnail en chat, owner-approve-exact y edición explícita.
- CI Kotlin + pruebas unitarias **sin APK**. Prueba real en teléfono con generación/edición pagadas queda pendiente y requiere consentimiento del autor.

**Estado inicial:** rama aislada, pendiente de CI; no se afirma que la interfaz ya esté instalada en Android.
