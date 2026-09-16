# PHASE 09 Runtime Evidence

Status: COMPLETE
Date: 2026-09-16 (JST)
Build HEAD: `1be4668f981ed051478941cd39e6e77447113353`

## Runtime acceptance confirmed

The following Phase09 behavior was confirmed in the live Minecraft environment during the interactive acceptance session:

- Data lamp UI displays current-period machine data.
- Graph displays and advances from current-period data.
- Recent bonus history displays correctly.
- Piri Chain behavior is correct.
- 100G continues the chain and 101G resets it.
- `/piri data` returns the public machine summary without seating/physical access.
- `/piri data <machineId>` returns machine detail including total/current G, BIG/REG/combined observed odds, difference, max difference, and recent bonus history.
- Public remote data does not expose the machine setting.
- Admin real-data simulation updates the same current-period data consumed by the data lamp/public data path.

## Final build/test gate

After pulling the latest repository HEAD, the user ran `build-jars.bat` on Windows and reported:

- main build: `BUILD SUCCESSFUL in 28s` (`27 actionable tasks: 5 executed, 22 up-to-date`)
- runtime helper build: `BUILD SUCCESSFUL in 3s` (`8 actionable tasks: 8 up-to-date`)
- Paper and Fabric JAR packaging completed successfully into `dist/`
- runtime test helper packaging completed successfully into `dist/test-only/`
- the Fabric test suite, including the previously failing `UiResourcesTest`, passed as part of the successful build

The prior failing palette/spec-lock test was corrected without deleting the test; intentional cabinet theme palette overrides are now explicitly permitted while the remaining UI resource contract stays checked.

## Result

Phase09 runtime acceptance and the latest-HEAD build/test gate are PASS. Phase09 is COMPLETE.
