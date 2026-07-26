# Evidence rules

Source/build evidence is recorded beside the gate it proves. Runtime evidence
must be raw and immutable: FrameView CSV, Reflex HUD/RTU export, JFR, ETL/WPA,
validation-layer log, JSON trace, or a machine-readable lifecycle capture.

Every capture directory must also carry the environment manifest, exact commit,
config export, display/refresh, queue and presentation policy, pacing owner and
target, multiplier, scene/save identifier, and start/end timestamps. A screenshot
may supplement raw evidence but cannot replace it.
