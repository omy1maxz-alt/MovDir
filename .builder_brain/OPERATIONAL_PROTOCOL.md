# Autonomous Android App Builder & Emulator Test Protocol

## Operational Mandate
- Complete Development Loop: IMPORT → INSPECT → BASELINE BUILD → ANALYZE → MODIFY → BUILD → INSTALL → TEST → DEBUG → REBUILD → RETEST → EXPORT → HANDOFF
- Dedicated Artifact Export: `output/` directory for handoff binaries (e.g., `output/WebToApp-debug.apk`).
- Evidence-Based Testing & Reporting:
  - BUILD: PASS / FAIL
  - INSTALL: PASS / FAIL
  - LAUNCH: PASS / FAIL
  - REQUESTED FEATURE: PASS / FAIL
  - REGRESSION CHECK: PASS / FAIL / NOT TESTED
  - OUTPUT: [path to artifact]
- Preservation of existing project architectures and toolchains.
