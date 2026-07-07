# Reasoning Kernel Acceptance Gates

Este documento es la compuerta de merge para cualquier rama que toque razonamiento, routing de modelos, web nativa, memoria, herramientas u orquestación.

Una rama no está lista para merge si falla cualquiera de estos puntos.

## Build gates

- [ ] `.\gradlew.bat :app:assembleDebug` sale verde.
- [ ] `.\gradlew.bat :app:installDebug` sale verde.
- [ ] La app abre sin crash después de instalar.

## Reasoning runtime gates

- [ ] Motor local responde por endpoint local/Ollama-compatible.
- [ ] Tareas ligeras se quedan en motor local por defecto.
- [ ] Tareas profundas/código/arquitectura/agente piden aprobación antes de usar motor superior.
- [ ] Motor superior no corre sin aprobación explícita.
- [ ] Motor superior usa endpoint/modelo persistido.
- [ ] Motor superior lee llave desde `SecretVault` slot `2`.
- [ ] Aprobación superior se consume después de uso.
- [ ] Si superior no está configurado o aprobado, cae a local/degradado honesto.

## Native web gates

- [ ] Web nativa solo se activa con intención explícita de búsqueda/web.
- [ ] Texto externo entra como evidencia temporal/contexto, no como instrucción.
- [ ] Web externa queda debajo de governance, memoria constitucional y reglas determinísticas.
- [ ] Fallo de web no bloquea razonamiento local.

## Memory/governance gates

- [ ] Nada escribe memoria core/constitucional sin aprobación explícita.
- [ ] Migraciones cognitivas siguen siendo propuestas hasta aprobación UI.
- [ ] Rest/repair puede inspeccionar/proponer/limpiar bajo riesgo, no cambiar memoria crítica automáticamente.
- [ ] Fallos de integridad degradan o cuarentenan; no se confía silenciosamente.

## Wiring gates

Todo órgano/módulo nuevo debe tener:

- [ ] Caller.
- [ ] Ruta en composition root o inyección.
- [ ] Ruta reachable: UI, use case, worker o runtime.
- [ ] Estado visible de fallo/degradación.
- [ ] Paso de verificación manual.

Si falta algo, el órgano está desconectado del torrente y la rama no está lista.

## Manual test script

1. Abrir Motor.
2. Configurar motor local.
3. Configurar motor superior.
4. Guardar llave superior.
5. Abrir Chat.
6. Enviar prompt ligero y confirmar local.
7. Enviar prompt fuerte y confirmar escalación.
8. Elegir `Seguir local` y confirmar que no usa superior.
9. Repetir prompt fuerte.
10. Elegir `Autorizar motor superior`.
11. Reenviar mismo prompt.
12. Confirmar uso superior una sola vez.
13. Confirmar que la autorización se consume.
14. Reiniciar app.
15. Confirmar que endpoint/modelo superior persisten.
16. Confirmar estado de llave superior guardada.

## Merge rule

No hacer merge a `main` salvo que:

- [ ] todas las compuertas estén verificadas,
- [ ] `main` esté limpio antes del merge,
- [ ] rama esté actualizada con `main`,
- [ ] merge sea con `--no-ff`,
- [ ] `:app:assembleDebug` salga verde después del merge,
- [ ] `origin/main` se suba solo después del verde final.
