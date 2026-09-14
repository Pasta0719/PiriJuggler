# Post-Phase05 manual runtime check helper

This helper exists only to let the user manually verify the post-Phase05 reel/reach-eye hotfix before Phase07 economy is implemented.

It is **not** part of the production Paper JAR and does not advance any Phase.

## Build

Run:

```bat
build-runtime-test-helper.bat
```

Output:

```text
dist/test-only/piri-runtime-test-paper.jar
```

Place that JAR beside the production PiriJuggler Paper JAR in the test server `plugins` directory and restart the server.

## Fund an idle test session

1. Register/open a Piri machine once so the player has a session.
2. Ensure no game is currently in progress.
3. As OP, run:

```text
/piritest fund
```

or from console/another OP:

```text
/piritest fund <player>
```

The helper writes only the selected session to `credit=50` and `held_medals=800`.
After success, close the slot screen and right-click the same machine again so the production plugin refreshes its authoritative in-memory snapshot from SQLite.

The helper refuses non-OP users, missing sessions, and unfinished games. Remove `piri-runtime-test-paper.jar` after manual verification.
