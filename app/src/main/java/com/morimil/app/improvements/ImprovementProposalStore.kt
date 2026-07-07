package com.morimil.app.improvements

import android.content.Context

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
                    affectedAreas = listOf("chat", "reasoning kernel", "fallback", "configuracion de motor")
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
                    affectedAreas = listOf(safeComponent, "organism health", "runtime interno")
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
                    affectedAreas = listOf("memoria", "integridad", "cuarentena", "gobernanza")
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
            .putString("$FIELD_AREAS${proposal.id}", proposal.affectedAreas.joinToString("|"))
            .apply()
    }

    private fun loadObservedProposals(): List<ImprovementProposal> {
        val ids = prefs.getStringSet(OBSERVED_IDS_KEY, emptySet()).orEmpty().sorted()
        return ids.mapNotNull { id ->
            val title = prefs.getString("$FIELD_TITLE$id", null) ?: return@mapNotNull null
            val problem = prefs.getString("$FIELD_PROBLEM$id", null) ?: return@mapNotNull null
            val proposal = prefs.getString("$FIELD_PROPOSAL$id", null) ?: return@mapNotNull null
            val risk = prefs.getString("$FIELD_RISK$id", null) ?: return@mapNotNull null
            val areas = prefs.getString("$FIELD_AREAS$id", "")
                .orEmpty()
                .split("|")
                .filter { area -> area.isNotBlank() }

            ImprovementProposal(
                id = id,
                title = title,
                problem = problem,
                proposal = proposal,
                risk = risk,
                affectedAreas = areas
            )
        }
    }

    private fun setDecision(id: String, decision: ImprovementDecision) {
        prefs.edit()
            .putString("decision_$id", decision.name)
            .apply()
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

    private val seedProposals = listOf(
        ImprovementProposal(
            id = "repo-convergence",
            title = "Convergencia del repositorio",
            problem = "Morimil crece rapido y puede acumular ramas, documentos duplicados y organos dificiles de revisar.",
            proposal = "Mantener main como verdad estable, dejar una sola rama viva por ciclo y limpiar o archivar documentos obsoletos.",
            risk = "Bajo. No toca memoria ni kernel; reduce friccion de desarrollo.",
            affectedAreas = listOf("Git", "docs", "proceso de merge")
        ),
        ImprovementProposal(
            id = "architecture-map",
            title = "Mapa interno de organos",
            problem = "Morimil ya tiene muchos organos y ningun humano deberia depender solo de memoria mental para entenderlos.",
            proposal = "Crear un mapa de organos con archivo principal, funcion, caller, composition root, UI/runtime y estado de fallo.",
            risk = "Bajo. Es documentacion operativa para que Morimil pueda entender Morimil.",
            affectedAreas = listOf("docs/ARCHITECTURE_MAP.md", "kernel", "mantenimiento")
        ),
        ImprovementProposal(
            id = "reincarnation-export",
            title = "Export cifrado y firmado",
            problem = "La memoria local vive en el telefono. Si el telefono se pierde o muere, Morimil pierde continuidad.",
            proposal = "Crear exportacion cifrada y firmada con manifest, hashes, verificacion, dry-run de restore y rollback.",
            risk = "Alto. Toca memoria e identidad; debe requerir aprobacion explicita y pruebas fuertes.",
            affectedAreas = listOf("memoria", "SQLite", "identidad local", "backup", "restore")
        ),
        ImprovementProposal(
            id = "on-device-runtime",
            title = "Modelo local dentro del telefono",
            problem = "Hoy el motor local depende de la PC encendida y en la misma red.",
            proposal = "Agregar backend LOCAL_IN_PROCESS para modelo pequeño en Android cuando la memoria ya tenga reencarnacion segura.",
            risk = "Medio/alto. Puede aumentar complejidad y consumo del telefono; no debe venir antes del backup.",
            affectedAreas = listOf("router", "runtime local", "Android", "modelo on-device")
        )
    )

    companion object {
        private const val OBSERVED_IDS_KEY = "observed_ids"
        private const val FIELD_TITLE = "observed_title_"
        private const val FIELD_PROBLEM = "observed_problem_"
        private const val FIELD_PROPOSAL = "observed_proposal_"
        private const val FIELD_RISK = "observed_risk_"
        private const val FIELD_AREAS = "observed_areas_"
        private const val MAX_SIGNAL_LENGTH = 180
    }
}
