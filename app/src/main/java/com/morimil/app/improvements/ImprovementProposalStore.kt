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
        return seedProposals.map { proposal ->
            proposal.copy(decision = readDecision(proposal.id))
        }
    }

    fun approve(id: String) {
        setDecision(id, ImprovementDecision.APPROVED)
    }

    fun deny(id: String) {
        setDecision(id, ImprovementDecision.DENIED)
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
}
