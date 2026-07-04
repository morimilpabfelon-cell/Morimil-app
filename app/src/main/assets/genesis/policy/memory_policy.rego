package morimil.memory

default allow_append := false
default allow_self_update := false
default allow_genesis_mutation := false
default allow_autobiographical_snapshot := false
default allow_interaction_state := false
default allow_rest_cycle := false
default allow_knowledge_capsule := false
default allow_recall_schedule := false
default allow_memory_link := false
default allow_memory_snapshot := false
default allow_migration_record := false
default allow_genesis_manifest := false

has_genesis_anchor if {
  input.genesis.genesis_core_hash != ""
}

has_chain_integrity if {
  input.chain.event_hash != ""
  input.chain.hash_algorithm == "sha256"
  input.chain.canonicalization == "morimil.memory_event_hash.v1"
}

has_provenance if {
  input.audit.created_by != ""
  input.event.source != ""
}

private_by_default if {
  input.privacy.visibility == "private_local"
  input.privacy.cloud_sync_allowed == false
}

allow_append if {
  input.schema_version == "morimil.memory_event.v1"
  has_genesis_anchor
  has_chain_integrity
  has_provenance
  private_by_default
}

allow_knowledge_capsule if {
  input.schema_version == "morimil.knowledge_capsule.v1"
  input.genesis_core_hash != ""
  input.privacy.visibility == "private_local"
}

allow_self_update if {
  input.schema_version == "morimil.self_update_proposal.v1"
  input.governance.requires_user_approval == true
  input.status == "pending"
}

allow_autobiographical_snapshot if {
  input.schema_version == "morimil.autobiographical_snapshot.v1"
  input.genesis_core_hash != ""
  input.privacy.visibility == "private_local"
  input.privacy.cloud_sync_allowed == false
}

allow_recall_schedule if {
  input.schema_version == "morimil.recall_schedule.v1"
  input.genesis_core_hash != ""
  input.policy.low_load_only == true
}

allow_interaction_state if {
  input.schema_version == "morimil.interaction_state.v1"
  input.boundaries.claims_emotion_as_real == false
  input.boundaries.allowed_in_system_prompt == true
}

allow_rest_cycle if {
  input.schema_version == "morimil.rest_cycle.v1"
  input.safety.mutates_genesis_core == false
  input.safety.executes_external_actions == false
  input.safety.deletes_memory == false
  input.device_conditions.network_required == false
}

allow_memory_link if {
  input.schema_version == "morimil.memory_link.v1"
  input.genesis_core_hash != ""
  input.privacy.visibility == "private_local"
  input.privacy.cloud_sync_allowed == false
  input.audit.write_mode == "append_only"
}

allow_memory_snapshot if {
  input.schema_version == "morimil.memory_snapshot.v1"
  input.genesis_core_hash != ""
  input.privacy.visibility == "private_local"
  input.privacy.cloud_sync_allowed == false
  input.audit.write_mode == "append_only"
}

allow_migration_record if {
  input.schema_version == "morimil.migration_record.v1"
  input.genesis_core_hash != ""
  input.approval.required == true
  input.approval.approved_by_user == true
  input.preconditions.chain_verified == true
  input.preconditions.pre_snapshot_id != ""
}

allow_genesis_manifest if {
  input.schema_version == "morimil.genesis_manifest.v1"
  input.mobile_installation.first_birth_event_required == true
  input.mobile_installation.startup_verification_required == true
  input.signature_policy.genesis_signature_required == false
  input.signature_policy.event_signature_required == false
  input.signature_policy.event_signature_algorithm == "planned_ed25519"
  input.signature_policy.status == "planned"
}

allow_genesis_mutation if {
  false
}
