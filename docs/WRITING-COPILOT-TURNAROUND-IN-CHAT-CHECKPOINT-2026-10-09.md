# Checkpoint — Copilot: frontal aprobado → vistas independientes (2026-10-09)

## Contexto
- Rama aislada `feature/copilot-turnaround-in-chat-20261009`, basada exactamente en Android PR #54 (SHA `0b378bca`).
- El backend ya ofrece `visual/characters/complete-views`, `batch/status`, `batch/approve` y galería de Character Studio.
- No se crea un proveedor, no se configura API Key, no se altera GPT Image 2 Medium, no hay llamadas reales de generación durante este cambio.
- Este lote es un trabajo candidato pendiente de CI. No afirmar que funciona en teléfono ni que los cambios están en main.

## Nuevo flujo
1. Copilot recupera candidatos y masters frontales ya aprobados desde Visual Asset Registry después del reinicio, por identidad/proyecto/modelo/procedencia.
2. El autor aprueba explícitamente una sola imagen frontal (la aprobación exacta y el vínculo Wiki son del PR #53/#54).
3. Aparece `Completar vistas desde el master aprobado` en el mismo chat; requiere pulsación expresa. Las vistas se piden por separado: left_three_quarter, left_profile, right_three_quarter, right_profile, back, full_body. No collage.
4. Preflight de lectura: consultar `visualCharacterDetail`, verificar que el master primario APPROVED de Wiki tenga el mismo asset ID y SHA que el master seleccionado; consultar batch/status. La consulta fallida no autoriza una nueva generación.
5. Si no hay lote previo, llama una sola vez a `complete-views` (potencialmente cobrable). Si existe lote, solo se recupera; un READY pendiente puede continuar usando su batch_id exacto. RUNNING/READY_FOR_REVIEW/BLOCKED/FAILED/COMPLETED no crea un batch nuevo ni reintenta pagos ambiguos.
6. Mostrar estado, conteos y miniaturas de los ángulos en Copilot, verificando desde descarga autenticada `asset_id`, SHA256 y bytes en almacenamiento local.
7. El autor aprueba el lote con botón independiente **solo si** todas las vistas candidatas están disponibles y verificadas, no hay pendientes/bloqueadas y el estado es READY_FOR_REVIEW. El backend todavía valida la pertenencia al proyecto, propietario y lote. Se conserva el canon narrativo.

## Validaciones de código agregadas
- `CopilotTurnaroundPolicyTest`: seis perspectivas únicas, bloqueo de aprobación sin previews, lote pendiente/bloqueado, hash inválido, no repetir nuevos lotes al conocer uno previo.
- `CopilotPortraitRecoveryPolicyTest`: candidatos + retratos APPROVED desde Copilot (modelo fijado), pero no galería ajena.
- Integración de la UI y ViewModel, con cancelación de polling y eliminación de previews al cambiar de proyecto.

## Límites y revisión humana
- No reconstituye el historial textual completo del Copilot. Las imágenes del registro se recuperan, el chat anterior no.
- Solo se muestra un lote activo por vez en la vista de Copilot; al pasar a otro personaje, los previews previos se limpian.
- El backend puede tener políticas adicionales de presupuesto/corrección; no evadir estados BLOCKED ni `OUTCOME_UNKNOWN`.
- No ejecutar generaciones pagadas sin la acción explícita del autor. No aprobar perspectivas automáticamente ni promover referencias al canon narrativo.
- Confirmar tras CI: tests Kotlin y parser, casos de master reemplazado y cambio de proyecto, comportamiento sin conectividad, reanudar READY, presentación de 6 miniaturas y aprobación por lote.
- **No APK** durante CI ordinario; compilar APK firmada/publicar solamente en una solicitud manual posterior.
- Mantener el control humano: al lanzar un workflow, detener el trabajo y dejar el enlace para monitoreo.

## Estado de handoff
Se preparó el código en rama sin PR para evitar disparar otro workflow antes de integrar #54. Falta CI y pruebas físicas. No integrar esta rama a main hasta revisión de pruebas.
