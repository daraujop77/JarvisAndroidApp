# Android Writing Room: A5 Copilot tools + durable task UI (2026-10-09)

## Objetivo
Copilot debe ser el chat operativo del Writing Room sin que cada pantalla implemente coordinación por su cuenta. La implementación Android **solo muestra el plan y el estado** y pide aprobación humana de una escritura. La selección, validación, ejecución, seguridad, idempotencia y almacenamiento viven en el VPS (ver checkpoint A5 backend).

## Implementación
- Envelope tipado `CopilotTaskState` agregado a la respuesta existente `WritingRoomAutoChat` vía SSE, compatible hacia atrás.
- Selector persistente por proyecto `Agente con herramientas`. Un mensaje enviado con ese modo llama `/api/app/writing-room/chat/stream` con `tool_mode=plan`; el VPS hace **una** inferencia para planificar y devuelve la tarea, sin segunda respuesta LLM.
- Herramientas read-only se ejecutan automáticamente tras la validación servidor. Escritos `PROPOSED_ONLY` requieren **Confirmar y guardar propuesta** desde el chat, mostrando argumentos exactos antes.
- `/copilot/tasks/latest` recupera el último trabajo autorizado después de reiniciar la app; no depende de que carguen todas las vistas del Wiki o Drive.
- `/copilot/tasks/status` solo lee; `/copilot/tasks/run` solo ejecuta un plan preexistente en el VPS y con `confirmed=true`. Timeout y estados dudosos nunca reintentan automáticamente los writes.
- Se preserva el chat normal sin inferencia extra para preguntas y los comandos visuales existentes permanecen disponibles cuando el modo Agente está apagado.
- No se cambió la infraestructura de conexión: gateway HTTPS actual, **sin Tailscale**.

## Prueba de aceptación posterior al CI (sin APK por bloque)
1. Ingresar al Writing Room y activar Agente con herramientas.
2. Escribir «Revisa el Wiki de Soren, lista personajes y dime cuál es el estilo visual del proyecto».
3. Comprobar que el plan muestra herramientas ejecutadas y resultados, sin alterar Wiki ni imágenes.
4. Enviar «Guarda esta idea: ...» en modo Agente; observar el contenido de la propuesta, NO guardarla hasta pulsar confirmar y verificar el estado `DONE`.
5. Cerrar la app, volver a entrar; el estado se recupera desde el VPS sin repetir ejecución.
6. Desde otra cuenta/proyecto, no debe verse la tarea anterior; desde lector no debe poder escribir ni conocer borradores privados.
7. Solicitar «Publica todo y genera imágenes»: el sistema debe decir que esa herramienta no está conectada, sin simular éxito ni incurrir en costos.

## Límites reales
Esto es la primera ruta **modelo → plan de herramientas → ejecución protegida**, con diez herramientas disponibles (lectura Wiki/personajes, estilo visual, capítulos, biblioteca y propuestas de ideas), no aún el acceso completo del agente a Scene Builder, producción de capítulos ni generación automática de imágenes. Su integración seguirá este MISMO contrato A5; no se volverá a programar una segunda lógica de agent en Android. Tampoco hubo prueba real en el teléfono ni llamadas pagadas durante este cambio.

## Validación
`CopilotAgentWiringTest` más CI estándar de compilación Kotlin/unit tests. El PR de Android depende de que la parte backend A5 esté integrada y desplegada antes de hacer una prueba en teléfono. No generar APK hasta el punto de prueba integrada.
