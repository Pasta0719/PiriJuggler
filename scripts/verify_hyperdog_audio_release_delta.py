#!/usr/bin/env python3
"""Fail-closed comparison of previously distributed and newly proposed Fabric/Paper JARs."""
import argparse
from zipfile import ZipFile

AUDIO_ALLOW = {
    "jp/pirijuggler/fabric/ui/HallAudio.class",
    "jp/pirijuggler/fabric/ui/HyperDogAudio.class",
    "jp/pirijuggler/fabric/ui/HyperDogOwnerAudioFilter.class",
    "jp/pirijuggler/fabric/ui/HyperDogResumeAudioGuard.class",
}

def compare(base, candidate, allow):
    deleted = sorted(base.keys() - candidate.keys())
    changed = sorted(k for k in base.keys() & candidate.keys() if base[k] != candidate[k])
    added = sorted(candidate.keys() - base.keys())
    outside = sorted((set(changed) | set(added)) - allow)
    return deleted, changed, added, outside

def entries(path):
    with ZipFile(path) as jar:
        corrupt = jar.testzip()
        if corrupt: raise ValueError(f"{path}: corrupt entry {corrupt}")
        return {info.filename: jar.read(info.filename) for info in jar.infolist() if not info.is_dir()}

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--baseline-fabric")
    parser.add_argument("--candidate-fabric")
    parser.add_argument("--baseline-paper")
    parser.add_argument("--candidate-paper")
    parser.add_argument("--self-test",action="store_true")
    opts=parser.parse_args()
    if opts.self_test:
        before={"sound.ogg":b"original","existing.class":b"class"}
        altered={"sound.ogg":b"missing audio","existing.class":b"class"}
        assert compare(before,altered,AUDIO_ALLOW)[3]==["sound.ogg"]
        altered={"existing.class":b"class"}
        assert compare(before,altered,AUDIO_ALLOW)[0]==["sound.ogg"]
        added=dict(before,**{"jp/pirijuggler/fabric/ui/HyperDogResumeAudioGuard.class":b"new"})
        assert not compare(before,added,AUDIO_ALLOW)[3]
        print("PASS: unauthorized replacement, file deletion and allowed addition are classified")
        return
    if not all([opts.baseline_fabric,opts.candidate_fabric,opts.baseline_paper,opts.candidate_paper]):
        parser.error("pass both baseline and candidate JARs for Fabric and Paper")
    for side,base,after,allow in [
        ("Fabric",opts.baseline_fabric,opts.candidate_fabric,AUDIO_ALLOW),
        ("Paper",opts.baseline_paper,opts.candidate_paper,set()),
    ]:
        deleted,changed,added,unexpected=compare(entries(base),entries(after),allow)
        print(f"{side}: changed={changed}, added={added}, deleted={deleted}, unauthorized={unexpected}")
        if deleted or unexpected:raise SystemExit(f"FAIL: {side} contains unexpected changes")
    print("PASS: no unapproved ZIP entries modified or deleted")

if __name__=="__main__":main()
