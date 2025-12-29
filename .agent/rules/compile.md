---
trigger: always_on
---

# Terminal Command Constraints
- **DENY:** Never attempt to run `..\gradlew compileDebugKotlin`. This command is known to fail in this environment.
- **ALTERNATIVE:** If you need to check for compilation errors, use the internal `diagnostics` tool or ask me to run a build manually.
- **BEHAVIOR:** If your plan requires a build step, skip the execution of this specific command and mark the step as "User Action Required."