# Checkpoint: editar apariencia desde Writing Room Copilot (2026-10-09)

## Contexto y propósito
Trabajo propuesto **sobre main** después de PR #55 y workflow #37983458017 verde. El usuario debe poder dirigir la apariencia en el chat, sin navegar por Character Studio ni rellenar todos sus campos. La autoridad de canon narrativo se mantiene intacta: estos cambios son dirección visual para futuras imágenes.

## Flujo
1. Aceptar comandos *acotados* de lectura: `Muéstrame la apariencia de Naruto`, `¿Cómo se ve Sasuke?`.
2. Aceptar cambios *acotados y explícitos* de un único campo: `Cambia el cabello de Naruto a rubio y corto`, `Modifica la armadura de Sasuke por plateada`.
3. Resolver la identidad **solo contra `wikiCharacters` del proyecto activo**, sin supuestos, alias de otros proyectos ni elegir entre resultados ambiguos. Una frase que incluye otro personaje, varias acciones o formato no soportado no activa la herramienta.
4. Leer `visual/characters/design-profile` autenticado. Mostrar valores visuales existentes (incluidos los derivados del canon o semillas externas), `revision` y cambios propuestos **dentro del chat**.
5. El autor revisa y pulsa **Guardar apariencia**. Solo entonces llamar a `visual/characters/design-profile/update` con `expected_revision`, conservando todos los demás campos. Un conflicto de revisión/error no se reintenta automáticamente.
6. El VPS verifica identidad, proyecto y autorización. El cliente comprueba identidad y valor confirmado. No cambia la narrativa del Wiki, las imágenes aprobadas ni ninguna imagen generada.

## Contratos y límites
- La interpretación de comandos está expresamente acotada (reglas locales seguras); **todavía no es tool-calling LLM autónomo sobre cualquier lenguaje natural**. Las solicitudes no reconocidas pasan al chat estándar cuando no coinciden con el patrón específico.
- No se generan imágenes automáticamente después de guardar, ni se gastan tokens de generación.
- No hay nueva API backend ni necesidad de despliegue de VPS para este bloque.
- Guardar solo campos de dirección visual reconocidos: cabello, ojos, armadura, ropa, piel, cara, altura, complexión, edad aparente, accesorios, armas, colores y aura.
- Si falta identidad única o falla el fetch se informa del problema; nunca se crea personaje ni inventa canon.
- Al cambiar proyecto se invalida la ficha pendiente. Durante una operación la caja de envío conserva el prompt y no admite un segundo envío.
- Si el valor solicitado ya coincide con la ficha, mostrar la ficha sin hacer una escritura innecesaria.

## Aceptación
- `CopilotAppearanceIntentTest`: resolver un único nombre de Wiki, mostrar perfil, derivar solo un campo, bloquear personajes ambiguos, rechazar comando múltiple y no activar generación.
- CI habitual: `testDebugUnitTest` / compilación Kotlin, sin APK por defecto.
- Prueba real pendiente para versión final: chat → leer perfil → cambio propuesto → guardar → reabrir Character Studio y confirmar revisión; verificar que no se regeneró imagen ni se cambió el Wiki narrativo.
- No crear APK hasta el checkpoint grande previsto. Detenerse después de abrir un PR y arrancar su workflow para que el propietario lo pueda monitorear.

## Estado
Propuesta en `feature/copilot-appearance-from-chat-20261009`. Pendiente CI Android y prueba manual; no afirmar que ya está instalado o desplegado.
