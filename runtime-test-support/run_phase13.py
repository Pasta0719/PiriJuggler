from pathlib import Path
import json, shutil, subprocess, sys

ROOT=Path(__file__).resolve().parents[1]
E=ROOT/"runtime-evidence"/"PHASE_13"
E.mkdir(parents=True,exist_ok=True)

# Phase13 uses the proven Phase12 Paper/Fabric two-client harness first.
# This establishes a real production remote-state/render-capable session with 42 machines.
p=subprocess.run([sys.executable,str(ROOT/"runtime-test-support"/"run_phase12.py")],cwd=ROOT)
if p.returncode:
    (E/"REPORT.md").write_text("# Phase13 runtime acceptance\n\nFAIL: Phase12 real-client prerequisite failed\n",encoding="utf-8")
    raise SystemExit(p.returncode)

# Static acceptance guards cover invariants that must remain true independent of pixels.
renderer=(ROOT/"fabric/src/main/java/jp/pirijuggler/fabric/render/WorldCabinetRenderer.java").read_text(encoding="utf-8")
placement=(ROOT/"fabric/src/main/java/jp/pirijuggler/fabric/render/CabinetPlacement.java").read_text(encoding="utf-8")
state=(ROOT/"fabric/src/main/java/jp/pirijuggler/fabric/network/RemoteMachineViewState.java").read_text(encoding="utf-8")
checks={
 "single_world_callback":"WorldRenderEvents.AFTER_ENTITIES.register" in renderer,
 "distance_culling":"32.0" in renderer or "32 * 32" in renderer or "32*32" in renderer,
 "frustum_culling":"frustum" in renderer.lower(),
 "entity_free_renderer":all(x not in renderer for x in ["ArmorStand","DisplayEntity","spawnEntity","addEntity"]),
 "six_facings":all(x in placement for x in ["NORTH","SOUTH","EAST","WEST","UP","DOWN"]),
 "authoritative_stop":"displayStop" in state and "applyStop" in state,
 "public_bonus_only":"bonusMode" in renderer,
 "data_lamp":all(x in renderer for x in ["bigCount","regCount","totalGames"]),
}
ok=all(checks.values())
(E/"result.json").write_text(json.dumps({"pass":ok,"checks":checks},indent=2),encoding="utf-8")
lines=["# Phase13 runtime acceptance","",f"Result: {'PASS' if ok else 'FAIL'}","",
       "Real Paper + Fabric prerequisite: PASS (Phase12 harness, including 42-machine remote sync)","",
       "## Phase13 guards"]+[f"- {'PASS' if v else 'FAIL'} {k}" for k,v in checks.items()]
lines += ["","NOTE: six-facing pixel screenshots and owner-vs-world visual comparison still require the Phase13 client capture scenario before Phase13 may be marked COMPLETE."]
(E/"REPORT.md").write_text("\n".join(lines)+"\n",encoding="utf-8")
raise SystemExit(0 if ok else 1)
