# A5.7 — Cambiar estilo y apariencia conversando con Copilot

Se reutilizan las herramientas versionadas de dirección visual del backend. Pedidos explícitos como «Cambia el estilo visual a ilustración shonen» o «Actualiza la apariencia de Soren: cabello más largo» se envían a un plan tipado, no directamente a un endpoint de escritura. El owner revisa el campo y valor exactos y debe tocar **Guardar cambio visual**.

La ruta de un retrato, una escena, una pregunta informativa o el editor de apariencia determinístico previo permanece igual. A5.7 no agrega formularios, cambia canon ni regenera imágenes.

La tarjeta explica que el cambio afecta candidatos posteriores, no imágenes o masters ya aprobados. Revisión de perfil y autorización residen en el VPS. Resultado `UNCHANGED` no crea revisiones innecesarias; errores de revisión se informan, sin reintento automático de escritura.

CI tests-only: clasificador natural, estados excluidos y wiring. Aún no hay validación en dispositivo ni APK.
