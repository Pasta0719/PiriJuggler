"""Phase14 focused runtime acceptance: same-host 42-cabinet performance, audio lifecycle, five-minute steady state."""
from pathlib import Path
import datetime,json,os,shutil,subprocess,time
ROOT=Path(__file__).resolve().parents[1];E=ROOT/"runtime-evidence/PHASE_14";RUN=datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ");OUT=E/"attempts"/RUN;SERVER=E/"work"/("server-"+RUN)
JAVA=shutil.which("java");PAPER=ROOT/"runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar";GRADLE=["cmd.exe","/d","/c",str(ROOT/"gradlew.bat")] if os.name=="nt" else [str(ROOT/"gradlew")]
prod={s:ROOT/s/f"build/libs/piri-juggler-{s}-1.0.0.jar" for s in ("paper","fabric")};helper={s:ROOT/f"runtime-test-support/{m}/build/libs/piri-runtime-test-{s}-1.0.0.jar" for s,m in (("paper","paper"),("client","client"))}
OUT.mkdir(parents=True,exist_ok=True);sr=OUT/"server-result.json";cr=OUT/"phase14-perf"/"client-result.json";cache={};server=client=None;handles=[];manifest={"run":RUN,"passed":False,"metrics":{},"assertions":[]}
def load(p):
 try: cache[p]=json.loads(p.read_text())
 except: pass
 return cache.get(p,{})
def log(p): return p.read_text(errors="replace") if p.exists() else ""
def wait(fn,label,t=300):
 end=time.monotonic()+t
 while time.monotonic()<end:
  if fn(): return
  if server and server.poll() is not None: raise RuntimeError("Paper exited "+label)
  if client and client.poll() is not None: raise RuntimeError("Client exited "+label)
  time.sleep(.25)
 raise TimeoutError(label)
def cmd(text): server.stdin.write(text+"\n");server.stdin.flush();time.sleep(.3)
def action(kind,**kw):
 n=int(load(cr).get("completed",0))+1;body={"id":n,"kind":kind}|kw;p=cr.with_name(f"command-{n}.json");p.write_text(json.dumps(body));wait(lambda:int(load(cr).get("completed",0))>=n,"client action "+kind,30)
def sample(seconds):
 vals=[];end=time.monotonic()+seconds
 while time.monotonic()<end:
  s=load(sr);c=load(cr)
  if s and c: vals.append({"mspt":s.get("mspt",0),"tps":s.get("tps1m",20),"serverHeap":s.get("heapMiB",0),"fps":c.get("cabinetFps",0),"frameMs":c.get("cabinetFrameMs",0),"clientHeap":c.get("heapMiB",0)})
  time.sleep(1)
 return vals
def avg(v,k):
 a=[x[k] for x in v if isinstance(x.get(k),(int,float)) and x[k]>=0];return sum(a)/len(a) if a else 0
def packets():
 kinds={"REMOTE_MACHINE_SPIN","REMOTE_MACHINE_STOP","REMOTE_MACHINE_NOTICE","REMOTE_MACHINE_BONUS","REMOTE_MACHINE_SOUND"}
 return sum(1 for p in load(cr).get("packets",[]) if p.get("type") in kinds)
try:
 plugins=SERVER/"plugins";plugins.mkdir(parents=True);shutil.copy2(prod["paper"],plugins);shutil.copy2(helper["paper"],plugins);(SERVER/"eula.txt").write_text("eula=true\n");(SERVER/"server.properties").write_text("server-ip=127.0.0.1\nserver-port=25592\nonline-mode=false\nenforce-secure-profile=false\nview-distance=6\nsimulation-distance=5\ngenerate-structures=false\n")
 h=(OUT/"server.log").open("w");handles.append(h);server=subprocess.Popen([JAVA,"-Xms512M","-Xmx1536M","-Dpiri.runtime.phase=phase13",f"-Dpiri.runtime.serverResult={sr}","-jar",str(PAPER),"nogui"],cwd=SERVER,stdin=subprocess.PIPE,stdout=h,stderr=subprocess.STDOUT,text=True);wait(lambda:"PIRI_DATABASE_READY" in log(OUT/"server.log"),"server")
 work=E/"work"/"client-phase14-perf"\n if work.exists(): shutil.rmtree(work)\n work.mkdir(parents=True);(work/"options.txt").write_text("version:3953\\nlang:en_us\\nrenderDistance:6\\nsimulationDistance:5\\nmaxFps:30\\npauseOnLostFocus:false\\nsoundCategory_master:0.0\\nskipMultiplayerWarning:true\\nonboardAccessibility:false\\n")\n h=(OUT/"client.log").open("w");handles.append(h);client=subprocess.Popen(GRADLE+["-PruntimeAcceptance=true","-PruntimeScenario=phase14-perf",f"-PruntimeRun={RUN}","-PruntimeEvidencePhase=PHASE_14",":runtime-test-client:runClient","--console=plain"],cwd=ROOT,stdout=h,stderr=subprocess.STDOUT);wait(lambda:load(cr).get("connected") and load(cr).get("handshake"),"client",600)
 cmd("op PiriRuntimeTest");action("command",text="piritest phase13grid");wait(lambda:len(load(sr).get("machines",[]))==42,"42 machines",60)
 cmd("tp PiriRuntimeTest 200 100 200");wait(lambda:load(cr).get("remoteCacheSize",0)==0,"baseline out of range",60);entityBefore=load(sr).get("totalEntities");baseline=sample(20)
 cmd("tp PiriRuntimeTest 0.5 99 0.5");wait(lambda:load(cr).get("remoteCacheSize")==42,"42 cache",120);idleStart=packets();idle=sample(30);idleTraffic=packets()-idleStart
 action("phase14_spin_all");spinStart=packets();spinning=sample(30);spinTraffic=packets()-spinStart
 steadyStartPackets=packets();steady=sample(300);steadyTraffic=packets()-steadyStartPackets
 views=load(cr).get("remotePresentation",{});near=sorted(int(k) for k,v in views.items() if (v.get("x",99)+.5)**2+(v.get("y",99)-97.5)**2+(v.get("z",99)+.5)**2<=100)
 audioTwo=False;audioMatchingEnd=False;audioRangeExit=False
 if len(near)>=2:
  action("phase14_bonus",machineId=near[0],active=True,bonusType="BIG");action("phase14_bonus",machineId=near[1],active=True,bonusType="REG")
  wait(lambda:load(cr).get("remoteLoopCount")==2,"two independent bonus loops",15);audioTwo=True
  action("phase14_bonus",machineId=near[0],active=False);wait(lambda:load(cr).get("remoteLoopCount")==1,"matching bonus end",15);audioMatchingEnd=True
  cmd("tp PiriRuntimeTest 200 100 200");wait(lambda:load(cr).get("remoteLoopCount")==0,"range exit stops loop",15);audioRangeExit=True
 entityAfter=load(sr).get("totalEntities")
 b={k:avg(baseline,k) for k in ("mspt","tps","serverHeap","fps","frameMs","clientHeap")};i={k:avg(idle,k) for k in b};sp={k:avg(spinning,k) for k in b}
 first=steady[:30];last=steady[-30:];serverGrowth=avg(last,"serverHeap")-avg(first,"serverHeap");clientGrowth=avg(last,"clientHeap")-avg(first,"clientHeap")
 metrics={"baseline":b,"idle42":i,"spinning42":sp,"idleGameplayPackets":idleTraffic,"spinningSteadyPacketGrowth":steadyTraffic,"msptIncrease":sp["mspt"]-b["mspt"],"fpsDegradationPct":0 if b["fps"]<=0 else max(0,(b["fps"]-sp["fps"])/b["fps"]*100),"serverSteadyHeapGrowthMiB":serverGrowth,"clientSteadyHeapGrowthMiB":clientGrowth,"entityBefore":entityBefore,"entityAfter":entityAfter}
 manifest["metrics"]=metrics
 checks={"42Cached":len(views)==42,"idleGameplayTrafficZero":idleTraffic==0,"msptIncreaseLe3":metrics["msptIncrease"]<=3.0,"fpsDegradationLe15":metrics["fpsDegradationPct"]<=15.0,"serverMemoryGrowthLe64":serverGrowth<=64.0,"clientMemoryGrowthLe64":clientGrowth<=64.0,"noPacketGrowthWithRenderFps":steadyTraffic==0,"entityDeltaZero":entityBefore==entityAfter,"twoBonusLoopsIndependent":audioTwo,"bonusEndStopsMatchingOnly":audioMatchingEnd,"rangeExitStopsLoop":audioRangeExit}
 manifest["assertions"]=[{"name":k,"passed":v} for k,v in checks.items()];manifest["passed"]=all(checks.values());(E/"result.json").write_text(json.dumps(manifest,indent=2));print(json.dumps(manifest,indent=2));raise SystemExit(0 if manifest["passed"] else 1)
finally:
 if client and client.poll() is None: client.kill()
 if server and server.poll() is None:
  try: server.stdin.write("stop\n");server.stdin.flush();server.wait(20)
  except: server.kill()
 for h in handles:h.close()
