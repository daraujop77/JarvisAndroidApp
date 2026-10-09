# Writing Room Copilot — Adjuntar referencia visual desde Android

**Estado:** trabajo aislado en `feature/copilot-visual-attachment-20261009`. Sin APK publicada, sin merge ni despliegue.

## Flujo incorporado

1. El usuario abre Writing Room > Copilot y toca el icono de imagen.
2. El selector nativo de Android admite una foto. El cliente crea una copia JPEG en almacenamiento privado y elimina los metadatos EXIF, usando el `AttachmentStore` existente.
3. El compositor enseña una vista previa y permite acompañarla de una nota; se puede quitar antes de subir.
4. Al enviar, Android llama **solo** `/api/app/writing-room/visual-assets/ingest` con `project_id`, `image_base64`, `kind=REFERENCE`, `source=MANUAL_UPLOAD`, `perspective=custom` y `provenance.surface=copilot`.
5. El backend exige rol owner y guarda la imagen **como CANDIDATE** en el Visual Asset Registry del mismo proyecto. Una nota puede señalar que es una referencia de estilo, pero no fija los rasgos de un personaje.
6. Copilot enseña la foto enviada en el historial local y responde el ID y estado del candidato. Si falta subirla a Drive, indica que está pendiente, sin afirmar aprobación ni disponibilidad permanente.
7. El límite cliente es 5 MiB de JPEG procesado (menos de 8 MiB de base64), sin ningún modelo de imágenes ni aprobación automática.

## Seguridad y política

- Mantener GPT Image 2 Medium como ruta actual; esta operación **no** genera imágenes ni llama a proveedor.
- Sin importar que el texto diga «esta es la cara de Doom», la importación no convierte referencia en canon ni asigna un personaje sin resolver su identidad. Las referencias externas solo influyen en generaciones posteriores si se seleccionan explícitamente.
- Los archivos locales nunca se pasan por nombre o URI a la autoridad del servidor. Solo viaja una copia JPEG saneada por el canal autenticado.
- El usuario puede reintentar si falla el envío. No se fuerza reintento automático que duplique cargas.
- El Chat normal sigue usando su ruta existente; las fotos se importan como acción tipada separada.

## Evidencia y criterios del próximo checkpoint

- Tests transport con MockWebServer: ruta exacta, auth bearer, project_id, `CANDIDATE`, `REFERENCE`, superficie `copilot`, ausencia de campos `status` y `character_ids`, rechazo de payload demasiado grande.
- CI Android debe ejecutar `app:testDebugUnitTest` y compilar `app:assembleDebug`.
- Prueba manual pendiente: seleccionar foto en un teléfono real, verificar preview, tamaño, almacenamiento/candidato, restauración por `asset_id` y control de permisos.
- Pendiente en fase siguiente: desde chat vincular foto importada con ficha de personaje propuesta, elegir GPT Image 2 Medium explícitamente para generar un retrato, presentar el resultado como candidato en línea y exigir aprobación exacta por hash antes de registrar un master en Wiki.

**La ruta requiere el backend Visual Registry que ya existía**, y no depende de fusionar el PR #521 de Copilot para importar una foto. Ese PR sí agrega las herramientas de ideas y Wiki desde el chat.
