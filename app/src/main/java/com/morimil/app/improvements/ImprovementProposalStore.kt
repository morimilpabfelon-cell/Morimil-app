package com.morimil.app.improvements

import android.content.Context
import com.morimil.app.data.local.ImprovementDecisionHistoryEntity
import com.morimil.app.data.local.MorimilDatabase

enum class ImprovementDecision {
    PENDING,
    APPROVED,
    DENIED
}

data class ImprovementProposal(
    val id: String,
    val title: String,
    val problem: String,
    val proposal: String,
    val risk: String,
    val affectedAreas: List<String>,
    val actionPlan: List<String> = emptyList(),
    val validationChecks: List<String> = emptyList(),
    val decision: ImprovementDecision = ImprovementDecision.PENDING
)

class ImprovementProposalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "morimil_improvement_proposals",
        Context.MODE_PRIVATE
    )

    fun loadProposals(): List<ImprovementProposal> {
        return (seedProposals + loadObservedProposals())
            .distinctBy { proposal -> proposal.id }
            .map { proposal -> proposal.copy(decision = readDecision(proposal.id)) }
    }

    fun approve(id: String) {
        setDecision(id, ImprovementDecision.APPROVED)
    }

    fun deny(id: String) {
        setDecision(id, ImprovementDecision.DENIED)
    }


    fun loadDecisionHistory(): List<ImprovementDecisionHistoryEntry> {
        val historyIds = prefs.getStringSet(HISTORY_IDS_KEY, emptySet()).orEmpty()
        return historyIds.mapNotNull { historyId ->
            val proposalId = prefs.getString("$FIELD_HISTORY_PROPOSAL_ID$historyId", null) ?: return@mapNotNull null
            val title = prefs.getString("$FIELD_HISTORY_TITLE$historyId", null) ?: proposalId
            val rawDecision = prefs.getString("$FIELD_HISTORY_DECISION$historyId", null) ?: return@mapNotNull null
            val decision = runCatching { ImprovementDecision.valueOf(rawDecision) }
                .getOrNull()
                ?: return@mapNotNull null
            val decidedAtMillis = prefs.getLong("$FIELD_HISTORY_AT$historyId", 0L)
            if (decidedAtMillis <= 0L) return@mapNotNull null

            ImprovementDecisionHistoryEntry(
                historyId = historyId,
                proposalId = proposalId,
                proposalTitle = title,
                decision = decision,
                decidedAtMillis = decidedAtMillis
            )
        }
            .sortedByDescending { entry -> entry.decidedAtMillis }
            .take(MAX_HISTORY_ENTRIES)
    }
    fun refreshObservedSignals(
        chatError: String?,
        internalComponent: String?,
        internalMessage: String?,
        memoryNeedsAttention: Boolean
    ): Int {
        var captured = 0

        if (!chatError.isNullOrBlank()) {
            recordObservedProposal(
                ImprovementProposal(
                    id = "observed-chat-error",
                    title = "Investigar error del motor de chat",
                    problem = "El chat reporto un fallo del motor: ${chatError.cleanSignal()}",
                    proposal = "Crear una investigacion de causa raiz antes de cambiar el kernel: endpoint, proveedor, llave, fallback y modo usado.",
                    risk = "Medio. Puede tocar runtime de razonamiento; requiere revision antes de aplicar cambios.",
                    affectedAreas = listOf("chat", "reasoning kernel", "fallback", "configuracion de motor"),
                    actionPlan = listOf(
                        "Registrar el prompt o accion que produjo el fallo.",
                        "Confirmar modo usado: local, superior o degradado.",
                        "Revisar endpoint, proveedor, modelo y llave activa.",
                        "Verificar si el fallback respondio honestamente.",
                        "Proponer correccion minima con diff separado."
                    ),
                    validationChecks = listOf(
                        ".\\gradlew.bat :app:assembleDebug",
                        "Enviar un prompt ligero y confirmar respuesta local.",
                        "Enviar un prompt fuerte y confirmar escalacion o fallback correcto."
                    )
                )
            )
            captured += 1
        }

        if (!internalComponent.isNullOrBlank()) {
            val safeComponent = internalComponent.cleanSignal()
            val safeMessage = internalMessage?.cleanSignal().orEmpty()
            recordObservedProposal(
                ImprovementProposal(
                    id = "observed-internal-${internalComponent.toStableId()}",
                    title = "Revisar issue interno: $safeComponent",
                    problem = if (safeMessage.isBlank()) {
                        "Morimil detecto un issue interno en $safeComponent."
                    } else {
                        "Morimil detecto un issue interno en $safeComponent: $safeMessage"
                    },
                    proposal = "Revisar el organo afectado, confirmar caller/composition root/runtime path y proponer correccion con diff antes de aplicar.",
                    risk = "Medio. Puede indicar organo desconectado o fallo de runtime.",
                    affectedAreas = listOf(safeComponent, "organism health", "runtime interno"),
                    actionPlan = listOf(
                        "Identificar archivo principal del organo afectado.",
                        "Confirmar caller real.",
                        "Confirmar ruta en composition root o inyeccion.",
                        "Confirmar ruta reachable: UI, worker, use case o runtime.",
                        "Definir correccion minima sin tocar memoria critica."
                    ),
                    validationChecks = listOf(
                        ".\\gradlew.bat :app:assembleDebug",
                        "Abrir la pantalla relacionada y confirmar que no hay crash.",
                        "Repetir la accion que produjo el issue interno."
                    )
                )
            )
            captured += 1
        }

        if (memoryNeedsAttention) {
            recordObservedProposal(
                ImprovementProposal(
                    id = "observed-memory-attention",
                    title = "Auditar memoria con atencion pendiente",
                    problem = "El estado de memoria indica que necesita revision o auditoria.",
                    proposal = "Ejecutar auditoria de integridad, revisar cuarentena si existe y no escribir memoria critica hasta confirmar estado.",
                    risk = "Alto. Toca continuidad de memoria; requiere aprobacion explicita.",
                    affectedAreas = listOf("memoria", "integridad", "cuarentena", "gobernanza"),
                    actionPlan = listOf(
                        "Ejecutar auditoria de integridad desde la app.",
                        "Revisar si hay eventos en cuarentena.",
                        "Bloquear escritura core/constitucional hasta confirmar estado.",
                        "Preparar propuesta de reparacion si hay fallo verificable.",
                        "No ejecutar migracion cognitiva hasta resolver la auditoria."
                    ),
                    validationChecks = listOf(
                        ".\\gradlew.bat :app:assembleDebug",
                        "Abrir Memory y confirmar estado visible.",
                        "Ejecutar auditoria y confirmar resultado antes de aprobar cambios."
                    )
                )
            )
            captured += 1
        }

        return captured
    }

    private fun recordObservedProposal(proposal: ImprovementProposal) {
        val ids = prefs.getStringSet(OBSERVED_IDS_KEY, emptySet()).orEmpty().toMutableSet()
        ids.add(proposal.id)

        prefs.edit()
            .putStringSet(OBSERVED_IDS_KEY, ids)
            .putString("$FIELD_TITLE${proposal.id}", proposal.title)
            .putString("$FIELD_PROBLEM${proposal.id}", proposal.problem)
            .putString("$FIELD_PROPOSAL${proposal.id}", proposal.proposal)
            .putString("$FIELD_RISK${proposal.id}", proposal.risk)
            .putString("$FIELD_AREAS${proposal.id}", proposal.affectedAreas.pack())
            .putString("$FIELD_ACTION_PLAN${proposal.id}", proposal.actionPlan.pack())
            .putString("$FIELD_VALIDATION${proposal.id}", proposal.validationChecks.pack())
            .apply()
    }

    private fun loadObservedProposals(): List<ImprovementProposal> {
        val ids = prefs.getStringSet(OBSERVED_IDS_KEY, emptySet()).orEmpty().sorted()
        return ids.mapNotNull { id ->
            val title = prefs.getString("$FIELD_TITLE$id", null) ?: return@mapNotNull null
            val problem = prefs.getString("$FIELD_PROBLEM$id", null) ?: return@mapNotNull null
            val proposal = prefs.getString("$FIELD_PROPOSAL$id", null) ?: return@mapNotNull null
            val risk = prefs.getString("$FIELD_RISK$id", null) ?: return@mapNotNull null
            val areas = prefs.getString("$FIELD_AREAS$id", "").orEmpty().unpack()
            val actionPlan = prefs.getString("$FIELD_ACTION_PLAN$id", "").orEmpty().unpack()
            val validation = prefs.getString("$FIELD_VALIDATION$id", "").orEmpty().unpack()

            ImprovementProposal(
                id = id,
                title = title,
                problem = problem,
                proposal = proposal,
                risk = risk,
                affectedAreas = areas,
                actionPlan = actionPlan,
                validationChecks = validation
            )
        }
    }

    private fun setDecision(id: String, decision: ImprovementDecision) {
        val now = System.currentTimeMillis()
        val historyId = "${now}_${decision.name}_${id.toStableId()}"
        val historyIds = prefs.getStringSet(HISTORY_IDS_KEY, emptySet()).orEmpty().toMutableSet()
        historyIds.add(historyId)

        prefs.edit()
            .putString("decision_$id", decision.name)
            .putLong("decision_at_$id", now)
            .putStringSet(HISTORY_IDS_KEY, historyIds)
            .putString("$FIELD_HISTORY_PROPOSAL_ID$historyId", id)
            .putString("$FIELD_HISTORY_TITLE$historyId", findProposalTitle(id))
            .putString("$FIELD_HISTORY_DECISION$historyId", decision.name)
            .putLong("$FIELD_HISTORY_AT$historyId", now)
            .apply()
    }

    private fun findProposalTitle(id: String): String {
        return (seedProposals + loadObservedProposals())
            .firstOrNull { proposal -> proposal.id == id }
            ?.title
            ?: id
    }

    private fun readDecision(id: String): ImprovementDecision {
        val raw = prefs.getString("decision_$id", null) ?: return ImprovementDecision.PENDING
        return runCatching { ImprovementDecision.valueOf(raw) }
            .getOrDefault(ImprovementDecision.PENDING)
    }

    private fun String.cleanSignal(): String {
        return replace(Regex("\\s+"), " ").trim().take(MAX_SIGNAL_LENGTH)
    }

    private fun String.toStableId(): String {
        return lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "unknown" }
            .take(80)
    }

    private fun List<String>.pack(): String {
        return joinToString(LIST_SEPARATOR)
    }

    private fun String.unpack(): List<String> {
        return split(LIST_SEPARATOR)
            .map { item -> item.trim() }
            .filter { item -> item.isNotBlank() }
    }

    private val seedProposals = listOf(
        ImprovementProposal(
            id = "repo-convergence",
            title = "Convergencia del repositorio",
            problem = "Morimil crece rapido y puede acumular ramas, documentos duplicados y organos dificiles de revisar.",
            proposal = "Mantener main como verdad estable, dejar una sola rama viva por ciclo y limpiar o archivar documentos obsoletos.",
            risk = "Bajo. No toca memoria ni kernel; reduce friccion de desarrollo.",
            affectedAreas = listOf("Git", "docs", "proceso de merge"),
            actionPlan = listOf(
                "Listar ramas locales y remotas.",
                "Clasificar ramas: fusionada, vieja, dudosa o activa.",
                "Mantener solo una rama viva de trabajo.",
                "Listar docs duplicados o obsoletos.",
                "Archivar o borrar ruido con commit separado."
            ),
            validationChecks = listOf(
                "git status limpio",
                ".\\gradlew.bat :app:assembleDebug",
                "git log --oneline -6 confirma merge controlado"
            )
        ),
        ImprovementProposal(
            id = "architecture-map",
            title = "Mapa interno de organos",
            problem = "Morimil ya tiene muchos organos y ningun humano deberia depender solo de memoria mental para entenderlos.",
            proposal = "Crear un mapa de organos con archivo principal, funcion, caller, composition root, UI/runtime y estado de fallo.",
            risk = "Bajo. Es documentacion operativa para que Morimil pueda entender Morimil.",
            affectedAreas = listOf("docs/ARCHITECTURE_MAP.md", "kernel", "mantenimiento"),
            actionPlan = listOf(
                "Crear docs/ARCHITECTURE_MAP.md.",
                "Registrar organos principales.",
                "Agregar caller y ruta runtime para cada organo.",
                "Marcar organos sin caller como riesgo.",
                "Usar el mapa como fuente para futuras propuestas."
            ),
            validationChecks = listOf(
                ".\\gradlew.bat :app:assembleDebug",
                "Revisar que cada organo nuevo tenga caller",
                "Confirmar que el mapa no contradice el codigo"
            )
        ),
        ImprovementProposal(
            id = "reincarnation-export",
            title = "Export cifrado y firmado",
            problem = "La memoria local vive en el telefono. Si el telefono se pierde o muere, Morimil pierde continuidad.",
            proposal = "Crear exportacion cifrada y firmada con manifest, hashes, verificacion, dry-run de restore y rollback.",
            risk = "Alto. Toca memoria e identidad; debe requerir aprobacion explicita y pruebas fuertes.",
            affectedAreas = listOf("memoria", "SQLite", "identidad local", "backup", "restore"),
            actionPlan = listOf(
                "Disenar manifest de export sin tocar datos reales.",
                "Definir que se exporta y que no se exporta.",
                "Crear dry-run de restore antes de restaurar.",
                "Agregar verificacion de hash/firma.",
                "Exigir aprobacion UI antes de restore real."
            ),
            validationChecks = listOf(
                ".\\gradlew.bat :app:assembleDebug",
                "Export de prueba genera manifest",
                "Restore dry-run no modifica memoria",
                "Restore real requiere aprobacion explicita"
            )
        ),
        ImprovementProposal(
            id = "on-device-runtime",
            title = "Modelo local dentro del telefono",
            problem = "Hoy el motor local depende de la PC encendida y en la misma red.",
            proposal = "Agregar backend LOCAL_IN_PROCESS para modelo pequeÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â±o en Android cuando la memoria ya tenga reencarnacion segura.",
            risk = "Medio/alto. Puede aumentar complejidad y consumo del telefono; no debe venir antes del backup.",
            affectedAreas = listOf("router", "runtime local", "Android", "modelo on-device"),
            actionPlan = listOf(
                "No iniciar hasta tener export/reincarnacion.",
                "Crear interfaz LOCAL_IN_PROCESS sin modelo real primero.",
                "Conectar router sin romper Ollama local.",
                "Agregar fallback si el telefono no soporta el runtime.",
                "Probar consumo y latencia antes de usarlo por defecto."
            ),
            validationChecks = listOf(
                ".\\gradlew.bat :app:assembleDebug",
                "Motor local/Ollama sigue funcionando",
                "Fallback honesto si LOCAL_IN_PROCESS no esta disponible"
            )
        )
    )

    companion object {
        private const val OBSERVED_IDS_KEY = "observed_ids"
        private const val FIELD_TITLE = "observed_title_"
        private const val FIELD_PROBLEM = "observed_problem_"
        private const val FIELD_PROPOSAL = "observed_proposal_"
        private const val FIELD_RISK = "observed_risk_"
        private const val FIELD_AREAS = "observed_areas_"
        private const val FIELD_ACTION_PLAN = "observed_action_plan_"
        private const val FIELD_VALIDATION = "observed_validation_"
        private const val HISTORY_IDS_KEY = "decision_history_ids"
        private const val FIELD_HISTORY_PROPOSAL_ID = "history_proposal_id_"
        private const val FIELD_HISTORY_TITLE = "history_title_"
        private const val FIELD_HISTORY_DECISION = "history_decision_"
        private const val FIELD_HISTORY_AT = "history_at_"
        private const val MAX_HISTORY_ENTRIES = 30
        private const val LIST_SEPARATOR = "\u001F"
        private const val MAX_SIGNAL_LENGTH = 180
    }
}
