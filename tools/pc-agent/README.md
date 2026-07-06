# Morimil PC Agent

Run from the repository root:

```powershell
python .\tools\pc-agent\morimil_pc_agent.py --root . --host 0.0.0.0 --port 8787 --target-root-id morimil_pc_root
```

The agent prints a pairing key. Morimil must send that key in the LAN request.

Endpoints:

```text
GET  /health
GET  /capabilities
POST /file-audit
```

The first vertical is file audit over LAN. It reads the configured root and returns a structured report. It does not write files.
