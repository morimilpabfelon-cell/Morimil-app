package morimil.orchestrator

default allow := false

allow if {
  input.intended_effect == "read"
}

allow if {
  input.intended_effect == "compute"
}

approval_present if {
  input.approval.approved == true
  input.approval.approval_id != ""
  input.approval.approver != ""
}

allow if {
  input.intended_effect == "write"
  approval_present
}

allow if {
  input.intended_effect == "external_side_effect"
  approval_present
}

allow if {
  input.intended_effect == "irreversible"
  approval_present
  input.approval.high_impact == true
}
