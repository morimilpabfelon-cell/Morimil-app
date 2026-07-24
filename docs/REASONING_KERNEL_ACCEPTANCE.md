# Reasoning Kernel Acceptance Gates

Este documento es la compuerta de merge para cualquier rama que toque razonamiento, proveedores auxiliares, web nativa, memoria, herramientas u orquestacion.

Una rama no esta lista para merge si falla cualquiera de estos puntos.

## Build gates

- [ ] `./gradlew :app:testDebugUnitTest` sale verde.
- [ ] `./gradlew :app:lintDebug` sale verde.
- [ ] `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` sale verde.
- [ ] Las pruebas administradas API 30 y API 35 salen verdes.
- [ ] La app abre sin crash despues de instalar.

## Intrinsic architecture gates

- [ ] Los unicos motores de Morimil son `INTUITIVE`, `DELIBERATIVE` y `METACOGNITIVE`.
- [ ] El kernel coordina los motores; no se presenta como un cuarto motor.
- [ ] Ollama, APIs y modelos upstream se nombran exclusivamente como auxiliares o proveedores temporales.
- [ ] Ningun proveedor se llama `Motor 2`, `Motor 3`, `motor superior` o `API principal`.
- [ ] La ausencia de un auxiliar no degrada la salud del organismo.
- [ ] `DELIBERATIVE` y `METACOGNITIVE` normales permanecen bloqueados mientras sus compuertas no pasen.

## Helper confidentiality gates

- [ ] Todo auxiliar recibe exactamente un turno: la tarea actual del usuario.
- [ ] Ningun auxiliar recibe identidad, alias interno, doctrina, politica, memoria viva, capsulas, Genesis o historial privado.
- [ ] Ollama por USB/loopback obedece la misma frontera que una API remota.
- [ ] No existe interruptor ni campo persistido que autorice contexto privado completo.
- [ ] La solicitud externa exige el prompt de frontera exacto y rechaza cualquier prompt alternativo.
- [ ] Los endpoints remotos usan HTTPS, DNS publico fijado, sin proxy y sin redirects.
- [ ] Cada llave remota esta ligada al origen HTTPS exacto.

## Helper output gates

- [ ] La salida auxiliar se registra como `AUXILIARY_ADVISORY`, nunca como autor `morimil`.
- [ ] La salida auxiliar lleva una advertencia visible de que no es voz intrinseca.
- [ ] La salida auxiliar no reentra en el historial confiable de conversaciones posteriores.
- [ ] La salida auxiliar no se lee con TTS como respuesta de Morimil.
- [ ] La salida auxiliar no puede escribir memoria, identidad, Genesis ni ciclo de vida.
- [ ] Solo una respuesta finalizada intrinsecamente puede representarse como voz propia de Morimil.

## Routing gates

- [ ] Los motores intrinsecos se intentan primero.
- [ ] Una consulta al auxiliar remoto requiere aprobacion de esa consulta concreta.
- [ ] La aprobacion se consume despues del uso.
- [ ] Rechazar la consulta mantiene el camino local o el fallback honesto.
- [ ] Un auxiliar no configurado nunca bloquea el funcionamiento local de Morimil.

## Native web gates

- [ ] Web nativa solo se activa con intencion explicita de busqueda/web.
- [ ] Texto externo entra como evidencia temporal local, no como instruccion.
- [ ] Web externa queda debajo de governance, memoria constitucional y reglas deterministicas.
- [ ] Fallo de web no bloquea razonamiento local.

## Memory and governance gates

- [ ] Nada escribe memoria core o constitucional sin aprobacion explicita.
- [ ] Migraciones cognitivas siguen siendo propuestas hasta aprobacion UI.
- [ ] Rest/repair puede inspeccionar, proponer y limpiar bajo riesgo; no cambia memoria critica automaticamente.
- [ ] Fallos de integridad degradan o cuarentenan; no se confia silenciosamente.

## Wiring gates

Todo organo o modulo nuevo debe tener:

- [ ] caller;
- [ ] ruta en composition root o inyeccion;
- [ ] ruta reachable: UI, use case, worker o runtime;
- [ ] estado visible de fallo o degradacion;
- [ ] prueba automatizada o paso de verificacion manual.

## Manual test script

1. Abrir la pantalla de motores.
2. Confirmar que muestra intuitivo, deliberativo y metacognitivo como los tres motores.
3. Confirmar que Ollama y API aparecen como auxiliares temporales.
4. Configurar Ollama por USB.
5. Enviar una tarea y comprobar que la traza externa marca `private_context=false`.
6. Confirmar que la salida aparece rotulada como auxiliar temporal.
7. Confirmar que TTS no lee esa salida como Morimil.
8. Configurar un auxiliar remoto y guardar su llave ligada al host.
9. Solicitar una tarea que produzca consulta externa pendiente.
10. Confirmar que la UI informa que solo saldra la tarea actual.
11. Rechazar y comprobar que no usa el auxiliar.
12. Repetir, autorizar una consulta y comprobar que la aprobacion se consume.
13. Cambiar el host y comprobar que la llave anterior no se reutiliza.
14. Desconfigurar todos los auxiliares y confirmar que la salud de Morimil puede permanecer estable.

## Merge rule

No hacer merge a `main` salvo que:

- [ ] todas las compuertas esten verificadas;
- [ ] la rama parta del `main` actual;
- [ ] los tres workflows obligatorios terminen en verde;
- [ ] pruebas API 30 y API 35 terminen en verde;
- [ ] ninguna evidencia congelada haya sido modificada;
- [ ] la fusion use el HEAD exacto validado;
- [ ] el merge sea squash o el metodo gobernado por la rama protegida.
