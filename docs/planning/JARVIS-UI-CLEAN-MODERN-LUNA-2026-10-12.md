# JARVIS: diseño más limpio y moderno — plan para Luna

Fecha: 2026-10-12. Estado: **implementación, CI y APK debug completados; validación visual en dispositivo pendiente**.
Implementador previsto: Luna.

## 1. Objetivo y alcance

Conservar la identidad que le gusta al usuario: fondo azul oscuro, acentos cian y el orbe de JARVIS. Mejorar legibilidad, jerarquía, espacio y consistencia en las pantallas activas. Completar la implementación, las verificaciones y la entrega a GitHub en una rama independiente. Avanzar todo lo posible localmente antes de ejecutar workflows; generar el APK únicamente cuando el candidato esté listo.

Esta revisión se basa en el código Compose. No se ha realizado una inspección visual de la app ejecutándose ni se aportan capturas de referencia todavía. Las propuestas deben contrastarse con previews y, al final, con la app en dispositivo/emulador.

### Aislamiento y base

- Repositorio: `daraujop77/JarvisAndroidApp`.
- Rama reservada: `codex/ui-clean-modern-luna`.
- Base inicial: `daa17a499de8df9e76ebf93cd4ba5483b853f5ea` de `main`.
- Esta base ya incluye Chat Global y Copilot (#75), referencias visuales aprobadas (#73) y vinculación de visión a la revisión aprobada (#74).
- Worktree del plan: `/workspace/JarvisAndroidApp-ui-clean-modern-luna`.
- El checkout original `/workspace/JarvisAndroidApp`, en `codex/chat-assistant-implementation`, permanece separado. No reutilizarlo ni volver a aplicar sus commits del chat.
- El backend `jarvis` queda fuera de este cambio visual.

Al comenzar, leer las instrucciones del repositorio, comprobar cambios locales y actualizar las referencias remotas. Si `main` avanzó, revisar los commits nuevos e integrar los necesarios en esta rama antes de editar archivos compartidos. Registrar la nueva base. No cambiar de rama en el checkout de otro trabajo, sobrescribir cambios ajenos ni hacer force-push.

## Estado de ejecución — 2026-10-12

- Rama aislada: `codex/ui-clean-modern-luna`. El checkout original sigue en `codex/chat-assistant-implementation`; `origin/main` continúa en `daa17a499de8df9e76ebf93cd4ba5483b853f5ea`.
- P0 confirmó los destinos activos en `JarvisRoot.kt`; no existe una ruta activa llamada `WritingRoomScreen`. `LinkedAtlasSection` sí se usa desde `WritingWorkspaceV1Screen` y quedó incluida.
- P1–P5 están implementados: tema y movimiento, navegación/conexión, lista y chat, proyectos/Workspace/estudios y pantallas de ajustes/seguridad/uso/acceso.
- Se mantuvieron los límites de permisos, API, datos, proveedores, autenticación y firma. Se corrigió el manejo explícito de una carrera de `POST_NOTIFICATIONS` ya cubierta por el código, sin cambiar permisos declarados; el atributo de navegación API 27 se separó en `values-v27` para conservar soporte de API 26.
- El CI del PR ejecutó compilación Kotlin, unit tests y `lintDebug` correctamente en `76dd66521d09e7fb5afd802fe992c429dc28952e` (run `38197991510`). Lint terminó con 47 advertencias y 4 sugerencias, sin errores.
- El CI del PR también pasó en el SHA del workflow final y del artefacto APK, `4d98cd506087ffff1a885d7e11764915894935c5` (run `38198367234`).
- El workflow manual con `build_apk=true` y `publish_release=false` terminó correctamente en el commit `4d98cd506087ffff1a885d7e11764915894935c5` (run `38198368684`). Subió `JARVIS-debug`, tamaño 22,321,925 bytes; digest SHA-256 del artefacto ZIP: `fb3c1f3a0f768f0663989d704a4344b97885bc821f9af88d463501ea5089d93c`. No publicó artefactos firmados ni release.
- En el workspace local `bash gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --no-daemon` no pudo iniciar: el wrapper no pudo descargar Gradle 8.13 por conexión rechazada. La validación equivalente se completó en GitHub Actions. No hay Android SDK/emulador local.
- No se han renderizado previews ni capturas reales. La matriz visual en dispositivo/emulador queda pendiente y bloquea declarar el rediseño totalmente verificado.
- PR #76 está abierto como borrador, sin merge. El APK debug está disponible como artefacto del run; su descarga directa desde este entorno respondió `Forbidden`, así que el artefacto se verifica por el resultado de build/upload y sus metadatos de GitHub.

## 2. Diagnóstico del código

Las rutas de esta sección son relativas a `app/src/main/java/com/jarvis/android/ui/`.

| Área | Evidencia en código | Cambio propuesto |
| --- | --- | --- |
| Tema | `theme/Theme.kt`: colores coherentes, pero estilos HUD pequeños, monoespaciados y espaciados; tipografía con color incorporado | Conservar paleta, centralizar tamaños/espaciado/formas y permitir colores semánticos por componente |
| Fondo | `components/AmbientBackdrop.kt`: tres luces radiales móviles; transición infinita creada incluso con movimiento reducido | Fondo más tranquilo en contenido; no crear animaciones decorativas cuando se reduce movimiento |
| Shell | `JarvisRoot.kt`: banner permanente, borde degradado de navegación y transiciones de 420 ms | Reducir decoración y hacer clara la selección, preservando navegación, teclado e insets |
| Conexión | `shared/ConnectionBanner.kt`: cápsula, brillo y texto técnico de 10 sp | Estado conectado discreto; errores legibles, accionables y fieles al estado real |
| Conversaciones | `screens/ConversationsScreen.kt`: borde degradado y orbe por fila, LIVE y papelera repetida | Lista sencilla, buen título/fecha y acciones secundarias en menú |
| Chat | Mismo archivo: controles circulares separados para adjuntos, documentos y generación | Compositor compacto con menú de adjuntos y acción enviar/detener claramente visible |
| Ajustes | `screens/SettingsScreen.kt`: tarjetas anidadas, etiquetas HUD y mezcla de idiomas | Filas y grupos de ajustes claros; diagnósticos accesibles bajo detalle |
| Proyectos | `screens/ProjectsScreen.kt`: resumen decorativo, LIVE SYNC y explicaciones internas de contratos | Lista centrada en proyectos y estados reales, con mensajes comprensibles |
| Writing Workspace | `screens/WritingWorkspaceV1Screen.kt`: superficies repetidas, cabeceras diferentes según pestaña y metadatos pequeños | Cabecera y componentes consistentes, conservando Copilot y todos sus controles |

## 3. Dirección visual y criterios medibles

- Mantener como punto de partida fondo `#060B16`, superficies `#0E1626` / `#16223A`, cian `#22D3EE`, texto `#E8EEF8` y secundario `#B7C7DC`. Son valores iniciales; ajustar si falla contraste real.
- Reservar cian para acción principal, selección y foco. Violeta y brillo sólo como acentos puntuales. Error, advertencia y éxito con icono/texto además de color.
- Escala de espacios: 4, 8, 12, 16, 24 y 32 dp. Márgenes de pantalla de 16 dp en compacto y 24 dp cuando haya espacio. Limitar ancho de lectura en pantallas amplias.
- Propuesta tipográfica: cuerpo 16 sp / 24 sp de interlínea; texto secundario 14 sp; metadatos 12 sp; títulos de sección 20–24 sp. Evitar mayúsculas y monoespaciado en navegación o instrucciones; reservarlos para código/datos técnicos.
- Formas consistentes: controles 12 dp, tarjetas 16 dp y diálogos 24 dp, adaptadas al componente Material. No envolver cada párrafo o fila en otra tarjeta.
- Objetivos táctiles de al menos 48 × 48 dp aunque el icono sea menor. Descripciones accesibles para botones sin texto y estado de selección anunciado.
- Contraste mínimo 4.5:1 en texto normal y 3:1 en texto grande y elementos de interfaz pertinentes. Medir el color compuesto sobre transparencias/degradados, no sólo los tokens.
- Animación funcional breve, aproximadamente 150–220 ms, sin rebote durante lectura. Movimiento reducido elimina pulsos, desplazamientos y escalas decorativas, conservando información de progreso.
- Mantener la identidad oscura actual. No agregar temas, dependencias visuales o configuraciones nuevas sin necesidad del alcance.

## 4. Invariantes funcionales

El cambio no requiere contratos de API, migraciones de Room, borrado de datos, cambios de proveedor, autenticación, firma, applicationId o versión de publicación.

Preservar:

- Historial, borradores, selección de conversación/proyecto, restauración de estado y navegación atrás.
- Enviar, detener, reintentar, streaming, autoseguimiento y botón para volver al último mensaje; no forzar scroll si el usuario lee mensajes anteriores.
- Markdown, bloques de código/copia, fuentes, adjuntos, previsualizaciones, límites vigentes de imágenes/documentos y comandos de memoria.
- Confirmación de borrado y restricciones al borrar conversaciones activas.
- Disponibilidad real de modelos/perfiles, errores, reconexión y distinción entre PC desconectado y servicio no disponible.
- Bloqueo, biometría, revocación, reautenticación, roles y acceso de propietario.
- Confirmaciones de acciones, costes de generación/visión, revisiones aprobadas, referencias verificadas, descargas/exportaciones oficiales y selección de proyecto. Una simplificación visual nunca confirma una acción automáticamente.
- Comportamiento de Copilot, estados READY/PROPOSED_ONLY y controles de las herramientas recientes. No ocultar advertencias de coste dentro de un menú secundario.
- Idiomas existentes mediante `i18n/AppStrings.kt` y el mecanismo actual de localización; no traducir identificadores ni nombres canónicos como Alexander.

## 5. Paquetes de implementación, en orden

Cada paquete termina con revisión del diff y un commit pequeño y descriptivo. Continuar hasta cerrar todos los paquetes; un cambio de tema global no completa el plan.

### P0 — Inventario y línea base

1. Confirmar base y archivos en conflicto potencial con trabajo paralelo, especialmente `JarvisRoot.kt`, `ConversationsScreen.kt`, `WritingWorkspaceV1Screen.kt` y `AppStrings.kt`.
2. Recorrer rutas reales desde `JarvisRoot.kt` y sus destinos. Marcar pantallas activas y código antiguo; no rediseñar pantallas inalcanzables sólo por su nombre.
3. Registrar estado inicial de compilación/pruebas en un checkout limpio de la misma base. Reutilizar evidencia fiable si coincide exactamente con el commit y comandos; no atribuir fallos previos al rediseño.
4. Crear fixtures sin datos privados para previews: vacío, contenido corto/largo, cargando, streaming, error, sin conexión, confirmación y adjuntos. Obtener referencia del aspecto actual con el mismo método que se usará para comparar; etiquetar previews como tales.

**Salida:** inventario de rutas, baseline y lista de evidencia pendiente. Si falta SDK/red, avanzar con el código y registrar el bloqueo; no declarar verificación completa.

### P1 — Sistema visual compartido

Archivos: `theme/Theme.kt`, `theme/Motion.kt`, `components/AmbientBackdrop.kt` y componentes HUD/orbe que realmente se usan.

Centralizar tokens de espacios/formas y estilos comunes; eliminar valores repetidos sólo donde se toque UI. Ajustar tipografía y colores semánticos, superficies y botones. Atenuar fondos y permitir que el contenido sea el foco. Evitar crear transiciones infinitas con movimiento reducido. Añadir previews de componentes representativos, con datos de prueba.

**Aceptación:** texto de botones/inputs con contraste correcto, estados habilitado/deshabilitado claros, targets de 48 dp y fondo reducido sin animación decorativa. Ninguna dependencia de negocio nueva.

### P2 — Shell, navegación y conexión

Archivos: `JarvisRoot.kt`, `shared/ConnectionBanner.kt` y controles compartidos necesarios.

Simplificar barra inferior sin cambiar destinos Chat, Proyectos, Aprobaciones, Tareas y Ajustes ni restricciones por rol. Selección visible con icono/etiqueta consistentes. Mantener estados guardados y ocultación de navegación con teclado. Reducir banner online y dar espacio suficiente a errores/reintento. Respetar status bar, gesture navigation, teclado y movimiento reducido también en transiciones del shell.

**Aceptación:** todos los destinos siguen alcanzables; contenido y acciones no quedan bajo barras del sistema; conexión y acceso bloqueado siguen siendo inequívocos.

### P3 — Lista de conversaciones y chat

Archivo principal: `screens/ConversationsScreen.kt`; componentes asociados `ChatAtoms.kt`, `shared/AttachmentThumb.kt` y `ProfileAvatar.kt` según necesidad.

1. Lista: reducir orbes/bordes por fila, alinear título, resumen y fecha; mantener selección y estados reales. Mover borrado al menú de fila con etiqueta accesible, conservando confirmación e impedimentos actuales.
2. Cabecera: título legible y selector compacto de perfil/modelo; detalles secundarios consultables. Sin quitar controles existentes.
3. Mensajes: relleno de usuario sobrio, asistente con superficie discreta, ancho de lectura razonable, párrafos/código/listas bien espaciados. No truncar contenido útil.
4. Compositor: un menú «+» con fotos, documentos y generación; input expansible y acción enviar/detener visible. Mantener límites, permisos del sistema, costes y consentimiento de generación. Adjuntos pendientes visibles y eliminables antes del envío.
5. Vacío: bienvenida breve y pocas sugerencias útiles, conservando acceso a funciones de memoria. Streaming/error/reintento y salto al último mensaje claros y accesibles.

**Aceptación:** conversación larga, bloques de código, respuesta en curso, reintento y adjuntos funcionan en ancho de 360 dp con teclado; no hay pérdida de borrador al abrir/cerrar menús o navegar.

### P4 — Proyectos, Writing Workspace y estudios

Archivos: `screens/ProjectsScreen.kt`, `screens/WritingWorkspaceV1Screen.kt`, `screens/WritingRoomScreen.kt` si es ruta activa, `screens/ImageStudioScreen.kt`, `screens/LinkedAtlasSection.kt`, y pantallas activas de `ui/writing/`: CharacterStudio, LocationStudio, WorldMap, VisualStudio, SceneBuilder y DraftReview.

Simplificar filas de proyectos, filtros y cabeceras. Sustituir texto de implementación expuesto por mensajes útiles sobre disponibilidad; no mostrar sincronización activa si el estado no la respalda. Unificar cabecera/tabs/acciones del workspace y tarjetas de contenido. Mantener Copilot como entrada actual y todas las herramientas accesibles. Preservar imágenes aprobadas, revisión, avisos de disponibilidad y confirmación de análisis visual de #73/#74.

El archivo del workspace es grande: extraer piezas visuales pequeñas conservando parámetros/callbacks; no hacer sustituciones masivas ni refactorizar ViewModel/transport/repositorios dentro de este paquete.

**Aceptación:** cambiar proyecto/pestaña conserva contexto; acciones y confirmaciones mantienen su alcance; imágenes se ajustan sin distorsión y errores de descarga no parecen éxitos; herramientas completas siguen alcanzables.

### P5 — Ajustes y pantallas restantes

Archivos: `screens/SettingsScreen.kt`, `DiagnosticsCard.kt`, `ApprovalsTasksScreens.kt`, `UsageScreen.kt`, `WelcomeScreen.kt`, `LockScreen.kt`, `PairingScreen.kt`, `SessionReauthScreen.kt`; localización en `i18n/AppStrings.kt`.

Agrupar ajustes en secciones comprensibles de apariencia/accesibilidad/idioma, seguridad, conexión/diagnóstico y aplicación, adaptándose a las opciones existentes. Usar filas simples y detalle desplegable para datos avanzados. Homogeneizar vacíos, carga, errores, diálogos y acciones en tareas/aprobaciones/uso/acceso. Localizar textos nuevos y modificados en los idiomas soportados.

**Aceptación:** opciones existentes siguen disponibles; aprobar/rechazar, emparejar, desbloquear y reautenticar conservan comportamiento; no quedan botones críticos con sólo color o mensajes cortados.

### P6 — Revisión integral y verificaciones locales

Revisar el diff contra la base y confirmar que no incluye cambios de backend, datos, permisos, versión o workflow ajenos al objetivo. Revisar novedades de `origin/main`; integrar cambios necesarios sin perder trabajo concurrente y repetir sólo verificaciones afectadas.

Con JDK compatible (CI usa 21), Android SDK 36 y dependencias disponibles:

```bash
git diff --check
bash gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --no-daemon
```

Estos comandos no ensamblan el APK distribuible. No modificar licencias/entorno para fingir una ejecución correcta; si hay un bloqueo, registrar comando, causa y qué falta verificar. Una revisión estática no sustituye compilación ni lint.

Revisar resultados existentes de navegación, AppStrings, conversación/autoseguimiento, Markdown y tests de Copilot/revisión visual. Añadir pruebas sólo si ayudan a verificar cambios de interacción/semántica reales. No escribir tests que repitan valores de color o padding. El repositorio no dispone actualmente de `app/src/androidTest` ni de un sistema de screenshots establecido: no presentar esa cobertura como existente. Si se añaden pruebas Compose instrumentadas, su ejecución pertenece a la fase final porque construye APK de aplicación y pruebas.

Matriz de revisión (previews ahora; ejecución real al final):

| Dimensión | Casos mínimos |
| --- | --- |
| Tamaño | 360 dp de ancho, teléfono mayor y pantalla amplia; revisar al menos una rotación |
| Texto | Escala 1.0, 1.3 y 2.0; español e inglés; nombres y mensajes largos |
| Sistema | Teclado abierto/cerrado, navegación por gestos, volver atrás, restauración de estado |
| Accesibilidad | Orden de foco/TalkBack, labels, selección, objetivos táctiles, contraste y movimiento reducido |
| Datos | Vacío, cargando, disponible, desconexión, error, streaming y adjuntos |
| Acciones | Enviar/detener/reintentar, copiar código, borrar con confirmación, aprobar/rechazar y cancelar diálogos |
| Copilot | Proyecto correcto, referencia/revisión aprobada, avisos de coste y confirmaciones intactas |

**Aceptación:** sin regresiones atribuibles al cambio, sin solapamientos/cortes de acciones ni desplazamiento horizontal accidental. Fallos previos y bloqueos se documentan por separado con evidencia de baseline.

### P7 — GitHub, verificación final y APK

1. Completar P0–P6 hasta el límite real del entorno. Preparar resumen, capturas disponibles y reporte de verificaciones. No parar para pedir permiso por cada commit o paquete.
2. Volver a leer los triggers de workflows antes de subir. En la base revisada, `writing-room-android-ci.yml` sólo corre por push a `main`, por PR con rutas relevantes o manualmente. Subir la rama aislada no activa ese workflow; un PR con cambios de app sí lo activa. No deshabilitar CI ni usar trucos para omitir controles.
3. Subir todos los commits de diseño a `codex/ui-clean-modern-luna`, verificar que el SHA remoto coincide y abrir PR hacia `main` cuando el candidato esté preparado para revisión. Si faltan verificaciones por entorno, marcarlo como borrador y especificarlas.
4. Ejecutar/observar CI al llegar a esta fase. El CI del PR ejecuta compilación Kotlin y unit tests, más `lintDebug`; los pasos de APK y publicación siguen siendo manuales. Para una ejecución manual de validación sin artefacto, seleccionar esta rama y dejar `build_apk=false`, `publish_release=false`. CI no sustituye las verificaciones visuales.
5. Corregir los fallos del candidato y revisar el SHA comprobado. Repetir sólo checks afectados. No considerar terminado un candidato que ni siquiera compila.
6. Sólo con el código completo y gates previos aprobados, generar el APK debug final para dispositivo/emulador: workflow manual en esta rama con `build_apk=true` y `publish_release=false`, o compilación local equivalente si el entorno ya está disponible. Verificar el artefacto y su commit. Las pruebas instrumentadas, si existen, se ejecutan aquí con sus APK de test.
7. Realizar la matriz funcional/visual en ese candidato y adjuntar evidencia antes/después comparable. Si aparece un defecto, corregir y volver a generar únicamente lo necesario; documentar la nueva revisión. Si no hay dispositivo/emulador, entregar el candidato claramente pendiente de validación visual, sin declarar el diseño totalmente verificado.
8. Entregar enlaces a rama, PR, checks y artefacto, con limitaciones reales. Dejar el PR sin fusionar para que el usuario revise el diseño. No publicar release ni enviar a Google Play automáticamente.

## 6. Entregables y definición de terminado

- [x] Inventario cubre las rutas activas; `WritingRoomScreen` no está conectada y se registra como inactiva.
- [x] P1–P5 implementados; estilo actualizado en chat, proyectos, workspace, estudios y pantallas restantes.
- [x] Funciones y datos existentes preservados; sin migraciones, permisos nuevos ni cambios de contratos.
- [x] Compilación, unit tests y lint con resultado/commit registrados en el reporte y CI.
- [x] Validación visual en dispositivo/emulador pendiente por falta de entorno; impide declarar cierre visual total.
- [ ] Capturas antes/después con misma pantalla, fixture, tamaño, escala de fuente y método; indicar si son previews o ejecución real. No exponer conversaciones, tokens o información privada.
- [x] Informe `docs/reviews/JARVIS-UI-CLEAN-MODERN-VERIFICATION.md` con base/candidato, comandos, resultados, dispositivo disponible y pendientes.
- [x] Commits subidos y PR #76 en borrador, con pruebas, limitaciones y estrategia de reversión.
- [x] APK debug generado en la fase final, ligado al SHA del workflow y sin publicación ni merge automático.

## 7. Volver al diseño anterior sin perder trabajo

Antes de fusionar, `main` conserva el diseño anterior: basta cerrar el PR o cambiar al checkout de `main`. Conservar la rama permite retomar la propuesta posteriormente. No borrar datos de la app para comparar diseños; instalaciones debug/release pueden tener IDs/firma distintos y deben documentarse.

Si posteriormente se fusiona y se quiere deshacer, revertir mediante un nuevo PR únicamente el commit squash/merge o los commits del rediseño, según cómo se haya integrado. Resolver conflictos manteniendo cambios posteriores del chat/Copilot. **No resetear `main` a esta base histórica**, porque eso descartaría trabajo ajeno. La reversión por Git es suficiente para este alcance; no se necesita mantener dos interfaces completas en producción.

## 8. Instrucción lista para Luna

> Implementa este plan completo en `codex/ui-clean-modern-luna`, partiendo de su base integrada y revisando cualquier avance posterior de main. Conserva identidad oscura/cian y las funciones del chat, Copilot y la app. Trabaja en el worktree aislado; no alteres el checkout del chat ni el backend. Completa P0–P6 con commits por paquete y verificaciones locales antes de workflows. Después entrega todos los cambios en GitHub y ejecuta P7: CI final, APK sólo cuando el candidato esté listo y validación real. Conserva confirmaciones, costes, revisiones aprobadas y datos. No te detengas tras cambiar colores o una sola pantalla. Documenta evidencia y bloqueos sin afirmar pruebas que no ejecutaste. Deja el PR disponible para revisión, sin fusionar ni publicar automáticamente.
