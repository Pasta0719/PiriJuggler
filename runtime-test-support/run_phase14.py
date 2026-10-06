"""Phase14 short performance acceptance: 42-cabinet scene only, no gameplay/economy fixtures."""
from pathlib import Path
import datetime,json,os,shutil,subprocess,time,re
ROOT=Path(__file__).resolve().parents[1];E=ROOT/"runtime-evidence/PHASE_14";RUN=datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ");OUT=E/"attempts"/RUN;SERVER=E/"work"/("server-"+RUN)
JAVA=shutil.which("java");PAPER=ROOT/"runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar";GRADLE=["cmd.exe","/d","/c",str(ROOT/"gradlew.bat")] if os.name=="nt" else [str(ROOT/"gradlew")];FLAGS=subprocess.CREATE_NO_WINDOW if os.name=="nt" else 0
prod={s:ROOT/s/f"build/libs/piri-juggler-{s}-1.0.0.jar" for s in ("paper","fabric")};helper={s:ROOT/f"runtime-test-support/{m}/build/libs/piri-runtime-test-{s}-1.0.0.jar" for s,m in (("paper","paper"),("client","client"))}
OUT.mkdir(parents=True,exist_ok=True);sr=OUT/"server-result.json";cr=OUT/"client-result.json";cache={};server=client=None;handles=[];manifest={"run":RUN,"passed":False,"metrics":{},"assertions":[]}
def load(p):
 try:cache[p]=json.loads(p.read_text())
 except:pass
 return cache.get(p,{})
def log(p):return p.read_text(errors="replace") if p.exists() else ""
def wait(fn,label,t=300):
 end=time.monotonic()+t
 while time.monotonic()<end:
  if fn():return
  if server and server.poll() is not None:raise RuntimeError("Paper exited "+label)
  if client and client.poll() is not None:raise RuntimeError("Client exited "+label)
  time.sleep(.25)
 raise TimeoutError(label)
def cmd(text):server.stdin.write(text+"\n");server.stdin.flush();time.sleep(.3)
def sample(seconds):
 vals=[];end=time.monotonic()+seconds
 while time.monotonic()<end:
  s=load(sr);c=load(cr)
  if s and c:vals.append({"mspt":s.get("mspt",0),"tps":s.get("tps1m",20),"serverHeap":s.get("heapMiB",0),"fps":c.get("cabinetFps",0),"frameMs":c.get("cabinetFrameMs",0),"clientHeap":c.get("heapMiB",0)})
  time.sleep(1)
 return vals
def avg(v,k):a=[x[k] for x in v if isinstance(x.get(k),(int,float)) and x[k]>=0];return sum(a)/len(a) if a else 0
try:
 plugins=SERVER/"plugins";plugins.mkdir(parents=True);shutil.copy2(prod["paper"],plugins);shutil.copy2(helper["paper"],plugins);(SERVER/"eula.txt").write_text("eula=true\n");(SERVER/"server.properties").write_text("server-ip=127.0.0.1\nserver-port=25592\nonline-mode=false\nenforce-secure-profile=false\nview-distance=6\nsimulation-distance=5\ngenerate-structures=false\n")
 h=(OUT/"server.log").open("w");handles.append(h);server=subprocess.Popen([JAVA,"-Xms512M","-Xmx1536M","-Dpiri.runtime.phase=phase13",f"-Dpiri.runtime.serverResult={sr}","-jar",str(PAPER),"nogui"],cwd=SERVER,stdin=subprocess.PIPE,stdout=h,stderr=subprocess.STDOUT,text=True);wait(lambda:"PIRI_DATABASE_READY" in log(OUT/"server.log"),"server")
 work=E/"work"/("client-"+RUN);work.mkdir(parents=True,exist_ok=True);(work/"options.txt").write_text("version:3953\nrenderDistance:6\nmaxFps:120\npauseOnLostFocus:false\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\n")
 h=(OUT/"client.log").open("w");handles.append(h);client=subprocess.Popen(GRADLE+["-PruntimeAcceptance=true","-PruntimeScenario=phase14-perf",f"-PruntimeRun={RUN}","-PruntimeEvidencePhase=PHASE_14",":runtime-test-client:runClient","--console=plain"],cwd=ROOT,stdout=h,stderr=subprocess.STDOUT);wait(lambda:load(cr).get("connected") and load(cr).get("handshake"),"client",600)
 cmd("op PiriRuntimeTest");cmd("tp PiriRuntimeTest 200 100 200");baseline=sample(15)
 cmd("piritest phase13grid");wait(lambda:len(load(sr).get("machines",[]))==42,"42 machines",60);cmd("tp PiriRuntimeTest 0.5 99 0.5");wait(lambda:load(cr).get("remoteCacheSize")==42,"42 cache",120);kinds={"REMOTE_MACHINE_SPIN","REMOTE_MACHINE_STOP","REMOTE_MACHINE_NOTICE","REMOTE_MACHINE_BONUS","REMOTE_MACHINE_SOUND"}; count=lambda:sum(1 for p in load(cr).get("packets",[]) if p.get("type") in kinds);idlePackets=count();idle=sample(30);idlePacketsAfter=count()
 b={k:avg(baseline,k) for k in ("mspt","tps","serverHeap","fps","frameMs","clientHeap")};i={k:avg(idle,k) for k in b}
 manifest["metrics"]={"baseline":b,"idle42":i,"idleGameplayPackets":idlePacketsAfter-idlePackets,"msptIncrease":i["mspt"]-b["mspt"],"fpsDegradationPct":0 if b["fps"]<=0 else max(0,(b["fps"]-i["fps"])/b["fps"]*100),"serverHeapDeltaMiB":i["serverHeap"]-b["serverHeap"],"clientHeapDeltaMiB":i["clientHeap"]-b["clientHeap"]}
 checks={"42Cached":load(cr).get("remoteCacheSize")==42,"idleGameplayTrafficZero":idlePacketsAfter-idlePackets==0,"msptIncreaseLe3":manifest["metrics"]["msptIncrease"]<=3.0,"fpsDegradationLe15":manifest["metrics"]["fpsDegradationPct"]<=15.0}
 manifest["assertions"]=[{"name":k,"passed":v} for k,v in checks.items()];manifest["passed"]=all(checks.values());(E/"result.json").write_text(json.dumps(manifest,indent=2));print(json.dumps(manifest,indent=2));raise SystemExit(0 if manifest["passed"] else 1)
finally:
 if client and client.poll() is None:client.kill()
 if server and server.poll() is None:
  try:server.stdin.write("stop\n");server.stdin.flush();server.wait(20)
  except:server.kill()
 for h in handles:h.close()
