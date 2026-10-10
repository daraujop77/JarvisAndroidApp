# Writing Room integrado — punto de revisión ANTES del APK

**Estado:** código en rama, tests CI pendientes, ninguna APK solicitada.

## Relación con backend
Backend: `daraujop77/jarvis`, branch `feature/copilot-writing-room-integrated-final-review-20261010`, informe `docs/planning/checkpoints/WRITING-ROOM-INTEGRATED-PRE-APK-BIG-MODEL-REVIEW-2026-10-10.md`.

Android: esta rama parte del PR anterior #64 (edición visual A5.7) y agrega:
- Intent natural de exportar `capítulo 37 a PDF`, o consultar cronología/lore/estado del mapa, sin obligar a activar manualmente Agent;
- Tarjeta Copilot que reconoce exclusivamente un resultado oficial con `READY_TO_DOWNLOAD`, `OFFICIAL_CANON`, `document_id=chapter:N`, formato permitido y SHA-256;
- Botón `Exportar capítulo` que llama al endpoint export existente con `expected_source_sha256`;
- Selector SAF en ChatSection, reutilizando la validación previa de tamaño/hash en ViewModel;
- Editor de apariencia por chat conserva la ruta de un solo rasgo, pero una petición con cabello+ojos puede usar `changes` y una sola confirmación;
- Tráfico con VPS HTTPS existente, sin Tailscale ni provider/modelo adicional.

## Controles
Ningún archivo se guarda hasta la selección de destino por el usuario. El botón no aparece para contenido de referencia, resumen o un resultado de otro proyecto/tarea. La propuesta visual muestra los campos exactos y tiene gate owner. No se autoaprueba canon, mapa, capítulo ni master. Las imágenes aprobadas anteriormente no se reescriben.

## Qué validar en CI y teléfono
1. Kotlin compila con los imports de `JsonPrimitive`, Compose launchers y API de export.
2. Tests de intent/desvío seguro, casos negativos y wiring en `CopilotAgentWiringTest`.
3. Exportar PDF/DOCX desde Copilot; verificar bytes, nombre, SHA y actividad del selector SAF.
4. Verificar cancelación del picker y fuente editada (409), sin falsos mensajes de éxito.
5. Confirmar que estilo/cabello/ojos se guardan en una sola revisión y aparecen en Character Studio.
6. Abrir/cerrar chat, reentrar, cambiar proyecto y usar cuenta familiar; sin fuga de estado.
7. Verificar front master, vistas, escena inline, capítulo revisado y permisos de aprobación.

**STOP** después de integrar el código y verificar el CI. El usuario solicita revisión completa con un modelo grande antes de construir o instalar APK. Este documento no acredita aceptación en dispositivo.
