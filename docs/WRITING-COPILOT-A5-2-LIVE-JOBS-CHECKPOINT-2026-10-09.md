# A5.2 Copilot en Android — trabajos durables de escenas y capítulos (2026-10-09)

## Ruta única
El cliente mantiene `CopilotTaskState` del bloque A5; no existen nuevos gestores Android para el motor visual o la coordinación de revisores. El backend A5.2 añade cinco herramientas al catálogo: generar candidato visual, consultar trabajo visual, estado del borrador, estado de revisión y comenzar revisión automática.

## Aprobación y costos explícitos
La tarjeta muestra argumentos exactos de cada acción de efecto `PROPOSED_ONLY`. Para `start_scene_generation`, resalta la selección local o nube. **Nube puede generar costos**; al confirmar una generación se limita a un intento con `max_corrections=0` (el VPS valida contexto, hashes y referencias aprobadas). El botón indica “Confirmar generación de imagen”, no “guardar idea”. Para `start_chapter_auto_review`, muestra que el trabajo consumirá tokens y que el canon no se aprobará automáticamente.

## Resultados recuperables
`CopilotTaskLiveProgress` se obtiene desde `/copilot/tasks/status` y `/copilot/tasks/latest`; contiene `job_id`, estado y `final_asset_id`. El botón “Actualizar estado desde el VPS” no reenvía la generación ni la revisión. Si hay candidato `READY_FOR_REVIEW`, muestra su identificador y recuerda que requiere revisión humana en **Visual Studio**. No se intentan leer bytes de imagen desde status; el preview en chat será un paso separado sobre las rutas seguras ya existentes.

## Aceptación
1. Pedir preparación desde pasaje del canon, aprobar la preparación. No debe consumir imagen.
2. Pedir generación explícita usando `context_id`, `context_hash` y engine exactos. Observar advertencia/consentimiento y generación única.
3. Recuperar estado tras cerrar/reabrir el teléfono; el mismo job debe persistir.
4. Iniciar auto-review de capítulo real y consultar progreso; no autorizar canon sin intervención humana.
5. Denegar cuentas no owner, proyectos cruzados, hashes falsos, reintentos automáticos.
6. Backend desplegado primero; Android Kotlin/unit tests sin APK, después smoke test funcional con APK final cuando se solicite.

**No se ha realizado prueba funcional de esta rama en el teléfono; no confundir CI con release.**
