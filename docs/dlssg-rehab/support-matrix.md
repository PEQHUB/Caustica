# Support matrix

No hardware qualification has been performed in this worktree. The following
are build prerequisites, not runtime qualification claims:

| Component | Required/probed value | Runtime qualification |
|---|---|---|
| OS | Windows | Awaiting capture |
| GPU | NVIDIA RTX 5090 target | Awaiting capture |
| Streamline | 2.12.0 headers and libraries | Awaiting capture |
| Java | Temurin OpenJDK 25.0.1+8 | Build verified |
| Vulkan SDK | 1.4.341.1 | Build verified |
| Queue policy | AUTO default; SYNCHRONIZED or PARALLEL explicit | Awaiting capture |
| Presentation policy | FG_MAILBOX default; PRESERVE_REQUEST and UNCAPPED_IMMEDIATE explicit | Awaiting capture |

No OS/GPU/driver/refresh-rate tuple is released as qualified until the raw
FrameView, Reflex, JFR/ETW, lifecycle, visual, fault, and soak evidence exists.
