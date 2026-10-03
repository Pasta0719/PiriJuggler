// Specification research only. Run: node skill-stop-phases/research/verify-economy.cjs
// Does not read or modify game config/code. All reel indexes are zero-based top positions.
"use strict";
const storage=new Map();const load=k=>storage.get(k);const store=(k,v)=>storage.set(k,v);
const records=[
  {
    "setting": 1,
    "replay": 0.137023842,
    "grape": 0.166599213,
    "bell": 0.000915525,
    "cherry": 0.026532274,
    "piero": 0.000686643,
    "big": 0.002794577,
    "reg": 0.00145985,
    "cherry_big": 0.000849314,
    "cherry_reg": 0.000694893,
    "piero_big": 0.000109863,
    "piero_reg": 0.000119018,
    "miss": 0.662214988
  },
  {
    "setting": 2,
    "replay": 0.137023842,
    "grape": 0.166783634,
    "bell": 0.000915525,
    "cherry": 0.026476121,
    "piero": 0.000686643,
    "big": 0.002898373,
    "reg": 0.001589789,
    "cherry_big": 0.00084819,
    "cherry_reg": 0.000752169,
    "piero_big": 0.000114441,
    "piero_reg": 0.000114441,
    "miss": 0.661796832
  },
  {
    "setting": 3,
    "replay": 0.137023842,
    "grape": 0.167751292,
    "bell": 0.000915525,
    "cherry": 0.026391892,
    "piero": 0.000686643,
    "big": 0.002928092,
    "reg": 0.001796183,
    "cherry_big": 0.00085914,
    "cherry_reg": 0.000825449,
    "piero_big": 0.000119018,
    "piero_reg": 0.000109863,
    "miss": 0.660593061
  },
  {
    "setting": 4,
    "replay": 0.137023842,
    "grape": 0.168647497,
    "bell": 0.000915525,
    "cherry": 0.026307662,
    "piero": 0.000686643,
    "big": 0.003040213,
    "reg": 0.002072823,
    "cherry_big": 0.000849033,
    "cherry_reg": 0.000919785,
    "piero_big": 0.000123596,
    "piero_reg": 0.000105285,
    "miss": 0.659308096
  },
  {
    "setting": 5,
    "replay": 0.137023842,
    "grape": 0.170413045,
    "bell": 0.000915525,
    "cherry": 0.026223433,
    "piero": 0.000686643,
    "big": 0.003202886,
    "reg": 0.002221245,
    "cherry_big": 0.000833871,
    "cherry_reg": 0.001019176,
    "piero_big": 0.000128173,
    "piero_reg": 0.000100708,
    "miss": 0.657231453
  },
  {
    "setting": 6,
    "replay": 0.137023842,
    "grape": 0.172701646,
    "bell": 0.000915525,
    "cherry": 0.026111127,
    "piero": 0.000686643,
    "big": 0.003584744,
    "reg": 0.00258388,
    "cherry_big": 0.000825449,
    "cherry_reg": 0.001139905,
    "piero_big": 0.000137329,
    "piero_reg": 0.000091552,
    "miss": 0.654198358
  }
];
const fits=[
  {
    "setting": 1,
    "scale": 0.7450360638899274,
    "bigDenom": 357.5665601115377,
    "regDenom": 590.3069431153605,
    "combined": 222.68163667530072,
    "ratio": 0.985,
    "actual": 0.9849999999999999,
    "baseIN": 2.588928474,
    "baseOUT": 1.45860658,
    "coefIN": 0.25153209475912225,
    "coefOUT": 1.7127727144125653,
    "pendingPasses": 0.00820279054,
    "onePay": 0.00009035798966666665,
    "firstTrial": {
      "upper": "BIG",
      "cherry": "BIG",
      "piero": "REG"
    },
    "oneEach": 0.000034701010805288696,
    "standaloneBig": 0.0018738545834855894
  },
  {
    "setting": 2,
    "scale": 0.7428621886197149,
    "bigDenom": 348.65146543161495,
    "regDenom": 548.0154904139463,
    "combined": 213.0851399914375,
    "ratio": 1,
    "actual": 1,
    "baseIN": 2.588928474,
    "baseOUT": 1.459857336,
    "coefIN": 0.26181871920309885,
    "coefOUT": 1.781711850522497,
    "pendingPasses": 0.008681128759999998,
    "onePay": 0.00009371406033333333,
    "firstTrial": {
      "upper": "BIG",
      "cherry": "BIG",
      "piero": "REG"
    },
    "oneEach": 0.00003588486183693815,
    "standaloneBig": 0.00193778253919466
  },
  {
    "setting": 3,
    "scale": 0.748404459335693,
    "bigDenom": 342.0610297101029,
    "regDenom": 489.17383971235154,
    "combined": 201.29967290172934,
    "ratio": 1.02,
    "actual": 1.02,
    "baseIN": 2.588928474,
    "baseOUT": 1.467261684,
    "coefIN": 0.27135151462548834,
    "coefOUT": 1.8447079510596807,
    "pendingPasses": 0.00927213124,
    "onePay": 0.00009467497466666667,
    "firstTrial": {
      "upper": "BIG",
      "cherry": "REG",
      "piero": "BIG"
    },
    "oneEach": 0.000036523285169086135,
    "standaloneBig": 0.001972257399130651
  },
  {
    "setting": 4,
    "scale": 0.7568414798528161,
    "bigDenom": 329.2630626291717,
    "regDenom": 426.5094523170976,
    "combined": 185.8149188187959,
    "ratio": 1.05,
    "actual": 1.05,
    "baseIN": 2.588928474,
    "baseOUT": 1.4740944040000001,
    "coefIN": 0.28630236884891935,
    "coefOUT": 1.9446612227668265,
    "pendingPasses": 0.01000421796,
    "onePay": 0.00009830022033333333,
    "firstTrial": {
      "upper": "BIG",
      "cherry": "REG",
      "piero": "BIG"
    },
    "oneEach": 0.000038349321766462834,
    "standaloneBig": 0.002070863375388993
  },
  {
    "setting": 5,
    "scale": 0.779112577772078,
    "bigDenom": 308.17122013009225,
    "regDenom": 384.1550445542284,
    "combined": 170.99673208756084,
    "ratio": 1.09,
    "actual": 1.09,
    "baseIN": 2.588928474,
    "baseOUT": 1.4878818720000002,
    "coefIN": 0.3003322182631053,
    "coefOUT": 2.0396309256762613,
    "pendingPasses": 0.010524102870000001,
    "onePay": 0.00010355998066666666,
    "firstTrial": {
      "upper": "BIG",
      "cherry": "REG",
      "piero": "BIG"
    },
    "oneEach": 0.00004159014612950167,
    "standaloneBig": 0.00224586789099309
  },
  {
    "setting": 6,
    "scale": 0.7553802569342876,
    "bigDenom": 291.1116337422509,
    "regDenom": 346.97762187162715,
    "combined": 158.2995192073462,
    "ratio": 1.13,
    "actual": 1.1300000000000001,
    "baseIN": 2.588928474,
    "baseOUT": 1.505741456,
    "coefIN": 0.3321891621202779,
    "coefOUT": 2.2548876888421128,
    "pendingPasses": 0.01171492523,
    "onePay": 0.00011590672266666666,
    "firstTrial": {
      "upper": "BIG",
      "cherry": "REG",
      "piero": "BIG"
    },
    "oneEach": 0.00004513074739606076,
    "standaloneBig": 0.0024370603593872812
  }
];
const buildNormal=function buildNormalModels(){
 const reels=[
 "R G BAR C G R G R B 7 P R G C BAR G R G P 7 G",
 "C G P R C G BAR R C G R C G P BAR R C G B 7 R",
 "B R P G B R P G B R P G B R P G B R BAR 7 G"
 ].map(x=>x.split(" "));
 const lines=[[0,0,0],[1,1,1],[2,2,2],[0,1,2],[2,1,0]];
 const patterns=["R R R","G G G","B B B","P P P","7 7 7","7 7 BAR","BAR BAR BAR","7 C BAR","B 7 B","G G P","P G P","P P 7","P BAR P"].map(x=>x.split(" "));
 const win=(r,t,row)=>reels[r][(t-row+21)%21];
 const masks=new Uint16Array(9261),leftBad=new Uint8Array(21),leftC=new Uint8Array(21);
 for(let l=0;l<21;l++){leftBad[l]=win(0,l,1)==="C"?1:0;leftC[l]=[0,2].some(row=>win(0,l,row)==="C")?1:0;}
 for(let l=0;l<21;l++)for(let m=0;m<21;m++)for(let r=0;r<21;r++){
  let mask=0; const ts=[l,m,r];
  for(let p=0;p<patterns.length;p++)if(lines.some(line=>line.every((row,k)=>win(k,ts[k],row)===patterns[p][k])))mask|=1<<p;
  masks[l*441+m*21+r]=mask;
 }
 const orders=[[0,1,2],[0,2,1],[1,0,2],[1,2,0],[2,0,1],[2,1,0]];
 const states=[
 ...["MISS","R","G","B","C","P"].map((small,si)=>({name:small,small,si,type:null,one:0,F:false,B:false})),
 ...["BIG","REG"].flatMap(type=>["MISS","C","P"].map(small=>({name:small==="MISS"?type:small+type,small,type,one:0,F:false,B:false}))),
 ...[7,8,9,10,11,12].map(p=>({name:patterns[p].join(""),small:"MISS",type:"BIG",one:1<<p,F:false,B:false})),
 ...["MISS","C","P"].map(small=>({name:"F"+small,small,type:"BIG",one:0,F:true,B:false})),
 ...[7,8,9,10,11,12].map(p=>({name:"F"+patterns[p].join(""),small:"MISS",type:"BIG",one:1<<p,F:true,B:false})),
 {name:"BC",small:"C",type:"BIG",one:0,F:false,B:true}
 ];
 const all=[];let failures=0;
 for(const s of states){
  const smallbit={R:1,G:2,B:4,P:8}[s.small]||0, bonusbit=s.type==="BIG"?16:s.type==="REG"?32:0;
  const permitted=smallbit|s.one;
  for(let oi=0;oi<6;oi++){
   const order=orders[oi],pairCache=new Map();
   function pair(ft,st,elig,leftBit){
    const key=(ft*42+st*2+(elig?1:0))*2+(leftBit?1:0);if(pairCache.has(key))return pairCache.get(key);
    const tops=[];tops[order[0]]=ft;tops[order[1]]=st;
    if(s.F && lines.some(line=>win(order[0],ft,line[order[0]])==="7"&&win(order[1],st,line[order[1]])==="7")){pairCache.set(key,null);return null;}
    let currentInput=-1;
    const legal=(lt,entry)=>{
     tops[order[2]]=lt;
     if(leftBad[tops[0]]&&!(s.B&&(order[2]===0?lt===currentInput:leftBit)))return false;
     if(s.small!=="C"&&leftC[tops[0]])return false;
     const mask=masks[tops[0]*441+tops[1]*21+tops[2]];
     return !(mask&~(permitted|(entry?bonusbit:0)));
    };
    let third=[],onePossible=false;
    for(let c=0;c<21;c++){
     currentInput=c;let out=null;
     if(elig&&!s.F&&legal(c,true)&&masks[tops[0]*441+tops[1]*21+tops[2]]&bonusbit)out=c;
     if(out===null&&s.one)for(let d=0;d<=4;d++){let t=(c+d)%21;if(legal(t,false)&&masks[tops[0]*441+tops[1]*21+tops[2]]&s.one){out=t;break;}}
     if(out===null)for(let d=0;d<=4;d++){let t=(c+d)%21;if(legal(t,false)){out=t;break;}}
     if(out===null){pairCache.set(key,null);return null;}
     tops[order[2]]=out;const mask=masks[tops[0]*441+tops[1]*21+tops[2]];
     const entry=!!(mask&bonusbit),replay=!!(mask&1);
     const pay=(mask&2?8:0)+(mask&4?14:0)+(mask&8?10:0)+(s.small==="C"&&(leftC[tops[0]]||leftBad[tops[0]])?4:0)+(mask&s.one?1:0);
     if(mask&s.one)onePossible=true;
     third.push((out<<9)|(entry?256:0)|(replay?128:0)|pay);
    }
    const result={third,onePossible,bonusPossible:elig&&!s.F&&lines.some(line=>win(order[0],ft,line[order[0]])==="7"&&win(order[1],st,line[order[1]])==="7")};
    pairCache.set(key,result);return result;
   }
   const first=[],second=[],third=[];
   function chooseSecond(ft,a,elig){
    const ss=[],tt=[];let anyOne=false;
    for(let b=0;b<21;b++){
     const options=[];
     for(let d=0;d<=4;d++){let st=(b+d)%21;const pp=pair(ft,st,elig&&d===0,order[0]===0?ft===a:order[1]===0?d===0:false);if(pp)options.push({st,d,pp});}
     if(!options.length)return null;
     let pick=options.find(x=>x.d===0&&x.pp.bonusPossible)|| (s.one?options.find(x=>x.pp.onePossible):null)||options[0];
     ss.push(pick.st);tt.push(pick.pp.third);if(pick.pp.onePossible)anyOne=true;
    }
    return {second:ss,third:tt,anyOne};
   }
   for(let a=0;a<21;a++){
    let forced=null;
    if(order[0]===1&&(a===19||a===20)){
     forced=s.small==="G"?1:s.small==="P"?2:s.small==="C"?20:s.small==="R"?0:(s.type||s.small==="B"?a:0);
    }
    if(order[0]===2&&a===20)forced=s.small==="B"?0:s.small==="P"?2:(s.type||s.small==="G"?20:0);
    let candidates=[];
    for(let d=0;d<=4;d++){
     const ft=(a+d)%21;if(forced!==null&&ft!==forced)continue;
     if(order[0]===0&&(leftBad[ft]&&!(s.B&&d===0)||s.small!=="C"&&leftC[ft]))continue;
     const elig=!!s.type&&(d===0||forced!==null);
     const chosen=chooseSecond(ft,a,elig);if(chosen)candidates.push({ft,d,chosen});
    }
    let pick=null;
    if(s.one && [0,1,2].some(row=>win(order[0],a,row)==="7"))pick=candidates.find(x=>x.d===0);
    if(!pick&&s.one)pick=candidates.find(x=>{
      const ts=[];ts[order[0]]=x.ft;
      for(let u=0;u<21;u++)for(let v=0;v<21;v++){
       ts[order[1]]=u;ts[order[2]]=v;
       if(leftBad[ts[0]]||leftC[ts[0]])continue;
       const mask=masks[ts[0]*441+ts[1]*21+ts[2]];
       if((mask&s.one)&&!(mask&~permitted))return true;
      }return false;
     });
    if(!pick)pick=candidates[0];
    if(!pick){failures++;throw new Error("firstFail "+s.name+" "+oi+" "+(a+1));}
    first.push(pick.ft);second.push(pick.chosen.second);third.push(pick.chosen.third);
   }
   all.push({...s,oi,order:order.map(i=>"LMR"[i]).join(""),first,second,third});
  }
 }
 return {all,failures};
};
const buildPending=function buildNormalModels(){
 const reels=[
 "R G BAR C G R G R B 7 P R G C BAR G R G P 7 G",
 "C G P R C G BAR R C G R C G P BAR R C G B 7 R",
 "B R P G B R P G B R P G B R P G B R BAR 7 G"
 ].map(x=>x.split(" "));
 const lines=[[0,0,0],[1,1,1],[2,2,2],[0,1,2],[2,1,0]];
 const patterns=["R R R","G G G","B B B","P P P","7 7 7","7 7 BAR","BAR BAR BAR","7 C BAR","B 7 B","G G P","P G P","P P 7","P BAR P"].map(x=>x.split(" "));
 const win=(r,t,row)=>reels[r][(t-row+21)%21];
 const masks=new Uint16Array(9261),leftBad=new Uint8Array(21),leftC=new Uint8Array(21);
 for(let l=0;l<21;l++){leftBad[l]=win(0,l,1)==="C"?1:0;leftC[l]=[0,2].some(row=>win(0,l,row)==="C")?1:0;}
 for(let l=0;l<21;l++)for(let m=0;m<21;m++)for(let r=0;r<21;r++){
  let mask=0; const ts=[l,m,r];
  for(let p=0;p<patterns.length;p++)if(lines.some(line=>line.every((row,k)=>win(k,ts[k],row)===patterns[p][k])))mask|=1<<p;
  masks[l*441+m*21+r]=mask;
 }
 const orders=[[0,1,2],[0,2,1],[1,0,2],[1,2,0],[2,0,1],[2,1,0]];
 const states=["BIG","REG"].flatMap(type=>["MISS","R","G","B","C","P"].map((small,si)=>({name:type+small,small,si,type,one:0,F:false,B:false})));
 const all=[];let failures=0;
 for(const s of states){
  const smallbit={R:1,G:2,B:4,P:8}[s.small]||0, bonusbit=s.type==="BIG"?16:s.type==="REG"?32:0;
  const permitted=smallbit|s.one;
  for(let oi=0;oi<6;oi++){
   const order=orders[oi],pairCache=new Map();
   function pair(ft,st,elig,leftBit){
    const key=(ft*42+st*2+(elig?1:0))*2+(leftBit?1:0);if(pairCache.has(key))return pairCache.get(key);
    const tops=[];tops[order[0]]=ft;tops[order[1]]=st;
    if(s.F && lines.some(line=>win(order[0],ft,line[order[0]])==="7"&&win(order[1],st,line[order[1]])==="7")){pairCache.set(key,null);return null;}
    let currentInput=-1;
    const legal=(lt,entry)=>{
     tops[order[2]]=lt;
     if(leftBad[tops[0]]&&!(s.B&&(order[2]===0?lt===currentInput:leftBit)))return false;
     if(s.small!=="C"&&leftC[tops[0]])return false;
     const mask=masks[tops[0]*441+tops[1]*21+tops[2]];
     return !(mask&~(permitted|(entry?bonusbit:0)));
    };
    let third=[],onePossible=false;
    for(let c=0;c<21;c++){
     currentInput=c;let out=null;
     if(elig&&!s.F&&legal(c,true)&&masks[tops[0]*441+tops[1]*21+tops[2]]&bonusbit)out=c;
     if(out===null&&s.one)for(let d=0;d<=4;d++){let t=(c+d)%21;if(legal(t,false)&&masks[tops[0]*441+tops[1]*21+tops[2]]&s.one){out=t;break;}}
     if(out===null)for(let d=0;d<=4;d++){let t=(c+d)%21;if(legal(t,false)){out=t;break;}}
     if(out===null){pairCache.set(key,null);return null;}
     tops[order[2]]=out;const mask=masks[tops[0]*441+tops[1]*21+tops[2]];
     const entry=!!(mask&bonusbit),replay=!!(mask&1);
     const pay=(mask&2?8:0)+(mask&4?14:0)+(mask&8?10:0)+(s.small==="C"&&(leftC[tops[0]]||leftBad[tops[0]])?4:0)+(mask&s.one?1:0);
     if(mask&s.one)onePossible=true;
     third.push((out<<9)|(entry?256:0)|(replay?128:0)|pay);
    }
    const result={third,onePossible,bonusPossible:elig&&!s.F&&lines.some(line=>win(order[0],ft,line[order[0]])==="7"&&win(order[1],st,line[order[1]])==="7")};
    pairCache.set(key,result);return result;
   }
   const first=[],second=[],third=[];
   function chooseSecond(ft,a,elig){
    const ss=[],tt=[];let anyOne=false;
    for(let b=0;b<21;b++){
     const options=[];
     for(let d=0;d<=4;d++){let st=(b+d)%21;const pp=pair(ft,st,elig&&d===0,order[0]===0?ft===a:order[1]===0?d===0:false);if(pp)options.push({st,d,pp});}
     if(!options.length)return null;
     let pick=options.find(x=>x.d===0&&x.pp.bonusPossible)|| (s.one?options.find(x=>x.pp.onePossible):null)||options[0];
     ss.push(pick.st);tt.push(pick.pp.third);if(pick.pp.onePossible)anyOne=true;
    }
    return {second:ss,third:tt,anyOne};
   }
   for(let a=0;a<21;a++){
    let forced=null;
    if(order[0]===1&&(a===19||a===20)){
     forced=s.small==="G"?1:s.small==="P"?2:s.small==="C"?20:s.small==="R"?0:(s.type||s.small==="B"?a:0);
    }
    if(order[0]===2&&a===20)forced=s.small==="B"?0:s.small==="P"?2:(s.type||s.small==="G"?20:0);
    let candidates=[];
    for(let d=0;d<=4;d++){
     const ft=(a+d)%21;if(forced!==null&&ft!==forced)continue;
     if(order[0]===0&&(leftBad[ft]&&!(s.B&&d===0)||s.small!=="C"&&leftC[ft]))continue;
     const elig=!!s.type&&(d===0||forced!==null);
     const chosen=chooseSecond(ft,a,elig);if(chosen)candidates.push({ft,d,chosen});
    }
    let pick=null;
    if(s.one && [0,1,2].some(row=>win(order[0],a,row)==="7"))pick=candidates.find(x=>x.d===0);
    if(!pick&&s.one)pick=candidates.find(x=>{
      const ts=[];ts[order[0]]=x.ft;
      for(let u=0;u<21;u++)for(let v=0;v<21;v++){
       ts[order[1]]=u;ts[order[2]]=v;
       if(leftBad[ts[0]]||leftC[ts[0]])continue;
       const mask=masks[ts[0]*441+ts[1]*21+ts[2]];
       if((mask&s.one)&&!(mask&~permitted))return true;
      }return false;
     });
    if(!pick)pick=candidates[0];
    if(!pick){failures++;throw new Error("firstFail "+s.name+" "+oi+" "+(a+1));}
    first.push(pick.ft);second.push(pick.chosen.second);third.push(pick.chosen.third);
   }
   all.push({...s,oi,order:order.map(i=>"LMR"[i]).join(""),first,second,third});
  }
 }
 return {all,failures};
};
const models=buildPending().all;
const probs=[0.7959716796875,0.1,0.1,0.00006103515625,0.00390625,0.00006103515625];
store("normalModels",buildNormal().all);
const pendingBackup=function backup(q,r,V) {
 let best={value:-Infinity};
 for(let oi=0;oi<6;oi+=2){
  const states=models.filter(m=>m.oi===oi).map(m=>({m,w:probs[m.si]*(m.type==="BIG"?q:1-q)})).filter(x=>x.w>0);
  for(let a=0;a<21;a++){
   const groups=new Map(); for(const x of states){const o=x.m.first[a];if(!groups.has(o))groups.set(o,[]);groups.get(o).push(x);}
   let total=-r; const policy=[];
   for(const [o,g] of groups){
    let bestBranch={value:-Infinity};
    for(let alt=0;alt<2;alt++){
    const gg=g.map(x=>({...x,m:models.find(m=>m.oi===oi+alt&&m.type===x.m.type&&m.si===x.m.si)}));
    let best2={value:-Infinity};
    for(let b=0;b<21;b++){
     const groups2=new Map();for(const x of gg){const o2=x.m.second[a][b];if(!groups2.has(o2))groups2.set(o2,[]);groups2.get(o2).push(x);}
     let val2=0;const third=[];
     for(const [o2,g2] of groups2){
      let best3={value:-Infinity};
      for(let c=0;c<21;c++){
       let val3=0;const outcomes=new Map();
       for(const x of g2){
        const code=x.m.third[a][b][c],pay=code&31,entry=!!(code&256),free=!!(code&128);
        val3+=x.w*(pay+(free?r:0));
        if(!entry){if(!outcomes.has(code))outcomes.set(code,[0,0]);outcomes.get(code)[x.m.type==="BIG"?0:1]+=x.w;}
       }
       for(const [wb,wr] of outcomes.values()) val3+=V*(wb+wr+Math.min(wb,wr));
       if(val3>best3.value+1e-14)best3={value:val3,input:c+1};
      }
      val2+=best3.value;third.push({observed:o2+1,input:best3.input});
     }
     if(val2>best2.value+1e-14)best2={value:val2,input:b+1,third};
    }
    if(best2.value>bestBranch.value+1e-14)bestBranch={...best2,order:models.find(m=>m.oi===oi+alt).order};
    }
    total+=bestBranch.value;policy.push({observed:o+1,input:bestBranch.input,third:bestBranch.third,order:bestBranch.order});
   }
   if(total>best.value+1e-14)best={value:total,order:models.find(m=>m.oi===oi).order,input:a+1,policy};
  }
 }
 return {...best,q,r,candidate:V*(1+Math.min(q,1-q)),improvement:best.value-V*(1+Math.min(q,1-q))};
};
const normalBackup=function normalBackup(rec,scale,R,V){
 const all=load("normalModels"); const models=all.filter(x=>x.oi===0), pool=[],early=[];
 function add(name,w,known=false,target=pool){if(w>0)target.push({name,w,known});}
 for(const [small,k] of [["R","replay"],["G","grape"],["B","bell"],["C","cherry"],["P","piero"]])add(small,rec[k]);
 const totalBonus=rec.big+rec.reg+rec.cherry_big+rec.cherry_reg+rec.piero_big+rec.piero_reg;
 add("MISS",1-rec.replay-rec.grape-rec.bell-rec.cherry-rec.piero-scale*totalBonus);
 for(const [name,key]of[["REG","reg"],["CREG","cherry_reg"],["PREG","piero_reg"]])add(name,scale*rec[key]);
 for(const m of models.filter(x=>x.type==="BIG"&&!x.F&&!x.B)){
  const gross=scale*(m.one?(rec.oneEach??rec.big*.1/6):m.small==="MISS"?(rec.standaloneBigGross??rec.big*.9):m.small==="C"?rec.cherry_big:rec.piero_big);
  const premiumEach=m.small==="C"?.05/6:.01;
  add(m.name,gross*.95);add(m.name,gross*premiumEach,true);add("F"+(m.one?m.name:m.small),gross*premiumEach,true);
  add(m.name,gross*(m.small==="C"?.025:.03),true,early);
  if(m.small==="C")add("BC",gross*premiumEach,true);
 }
 function optimize(p){
  let best={value:-Infinity};
  for(let oi=0;oi<6;oi+=2){
   const states=p.map(x=>({...x,m:all.find(m=>m.oi===oi&&m.name===x.name)}));
   for(let a=0;a<21;a++){
    const groups=new Map();for(const x of states){const o=x.m.first[a];if(!groups.has(o))groups.set(o,[]);groups.get(o).push(x);}
    let total=0,policy=[];
    for(const [o,g]of groups){
     let bestBranch={value:-Infinity};
     for(let alt=0;alt<2;alt++){
     const gg=g.map(x=>({...x,m:all.find(m=>m.oi===oi+alt&&m.name===x.name)}));
     let best2={value:-Infinity};
     for(let b=0;b<21;b++){
      const groups2=new Map();for(const x of gg){const o2=x.m.second[a][b];if(!groups2.has(o2))groups2.set(o2,[]);groups2.get(o2).push(x);}
      let val2=0,third=[];
      for(const [o2,g2]of groups2){
       let best3={value:-Infinity};
       for(let c=0;c<21;c++){
        let val3=0;const unknown=new Map();
        for(const x of g2){
         const code=x.m.third[a][b][c],pay=code&31,entry=!!(code&256),free=!!(code&128),type=x.m.type;
         val3+=x.w*(pay+(free?3*R:0)+(type==="BIG"?350-50*R:type==="REG"?168-24*R:0));
         if(type&&!entry){
          if(x.known)val3+=x.w*V;
          else{if(!unknown.has(code))unknown.set(code,[0,0]);unknown.get(code)[type==="BIG"?0:1]+=x.w;}
         }
        }
        for(const [wb,wr]of unknown.values())val3+=V*(wb+wr+Math.min(wb,wr));
        if(val3>best3.value+1e-14)best3={value:val3,input:c+1};
       }
       val2+=best3.value;third.push({observed:o2+1,input:best3.input});
      }
      if(val2>best2.value+1e-14)best2={value:val2,input:b+1,third};
     }
     if(best2.value>bestBranch.value+1e-14)bestBranch={...best2,order:all.find(m=>m.oi===oi+alt).order};
     }
     total+=bestBranch.value;policy.push({observed:o+1,input:bestBranch.input,third:bestBranch.third,order:bestBranch.order});
    }
    if(total>best.value+1e-14)best={value:total,order:all.find(m=>m.oi===oi).order,input:a+1,policy};
   }
  }
  return best;
 }
 store("pools",{pool,early});const main=optimize(pool), knownEarly=optimize(early);
 return {setting:rec.setting,scale,R,main,early:knownEarly,value:main.value+knownEarly.value-3*R,mass:pool.concat(early).reduce((s,x)=>s+x.w,0)};
};
const report=[];
for(const fit of fits){
 const R=fit.ratio;
 const V=(.8162353515625-.9*R)/.79603271484375;
 const pending=[0,.5,1].map(q=>pendingBackup(q,R,V));
 const normal=normalBackup(records[fit.setting-1],fit.scale,R,V);store("pools-"+fit.setting,load("pools"));
 if(Math.max(...pending.map(x=>Math.abs(x.improvement)))>1e-11||Math.abs(normal.value)>1e-11||Math.abs(normal.mass-1)>1e-12)throw Error("Economy check failed for setting "+fit.setting);
 report.push({setting:fit.setting,target:R,pendingResidual:Math.max(...pending.map(x=>Math.abs(x.improvement))),normalResidual:normal.value,normal,successRates:[{"setting":1,"values":[{"success":0,"bigGames":20,"regGames":8,"rtp":89.36820091446185},{"success":0.5,"bigGames":22.22222222222222,"regGames":9.6,"rtp":93.32806022386906},{"success":0.8,"bigGames":23.80952380952381,"regGames":10.909090909090908,"rtp":96.24819409336779},{"success":1,"bigGames":25,"regGames":11.999999999999998,"rtp":98.49999999999999}]},{"setting":2,"values":[{"success":0,"bigGames":20,"regGames":8,"rtp":90.52311000685971},{"success":0.5,"bigGames":22.22222222222222,"regGames":9.6,"rtp":94.62942370248172},{"success":0.8,"bigGames":23.80952380952381,"regGames":10.909090909090908,"rtp":97.66061708286854},{"success":1,"bigGames":25,"regGames":11.999999999999998,"rtp":100}]},{"setting":3,"values":[{"success":0,"bigGames":20,"regGames":8,"rtp":92.07361955184915},{"success":0.5,"bigGames":22.22222222222222,"regGames":9.6,"rtp":96.36714698747193},{"success":0.8,"bigGames":23.80952380952381,"regGames":10.909090909090908,"rtp":99.54381164716624},{"success":1,"bigGames":25,"regGames":11.999999999999998,"rtp":102.00000000000001}]},{"setting":4,"values":[{"success":0,"bigGames":20,"regGames":8,"rtp":94.39555021939637},{"success":0.5,"bigGames":22.22222222222222,"regGames":9.6,"rtp":98.97459983309844},{"success":0.8,"bigGames":23.80952380952381,"regGames":10.909090909090908,"rtp":102.37000729348634},{"success":1,"bigGames":25,"regGames":11.999999999999998,"rtp":105}]},{"setting":5,"values":[{"success":0,"bigGames":20,"regGames":8,"rtp":97.6320584698451},{"success":0.5,"bigGames":22.22222222222222,"regGames":9.6,"rtp":102.54004526017954},{"success":0.8,"bigGames":23.80952380952381,"regGames":10.909090909090908,"rtp":106.18004523895077},{"success":1,"bigGames":25,"regGames":11.999999999999998,"rtp":108.99999999999999}]},{"setting":6,"values":[{"success":0,"bigGames":20,"regGames":8,"rtp":100.88905585782861},{"success":0.5,"bigGames":22.22222222222222,"regGames":9.6,"rtp":106.1161054841088},{"success":0.8,"bigGames":23.80952380952381,"regGames":10.909090909090908,"rtp":109.99437407516875},{"success":1,"bigGames":25,"regGames":11.999999999999998,"rtp":112.99999999999997}]}][fit.setting-1]});
}
console.log(JSON.stringify({scope:"specification research controller; not game implementation",normalModels:load("normalModels").length,normalHistories:load("normalModels").length*9261,pendingModels:models.length,pendingHistories:models.length*9261,report},null,2));

if(process.argv.includes("--simulate")){
 let rngstate=123456789;const rand=()=>{rngstate^=rngstate<<13;rngstate^=rngstate>>>17;rngstate^=rngstate<<5;return(rngstate>>>0)/4294967296;};
 const simulation=[];
 for(const fit of fits){
  const op=report[fit.setting-1].normal,pools=load("pools-"+fit.setting),all=load("normalModels");let cdf=0;const states=[];
  for(const [flag,pool]of[[false,pools.pool],[true,pools.early]])for(const x of pool){
   const pol=flag?op.early:op.main,m=all.find(y=>y.name===x.name&&y.order===pol.order),a=pol.input-1,fstop=m.first[a];
   const pp=pol.policy.find(y=>y.observed===fstop+1),m2=all.find(y=>y.name===x.name&&y.order===pp.order),b=pp.input-1,sstop=m2.second[a][b],c=pp.third.find(y=>y.observed===sstop+1).input-1,code=m2.third[a][b][c];
   cdf+=x.w;states.push({...x,flag,type:m.type,code,key:[flag,fstop,sstop,code].join("/"),cdf});
  }
  for(const x of states){const group=states.filter(y=>!y.known&&y.type&&!(y.code&256)&&y.key===x.key);const B=group.filter(y=>y.type==="BIG").reduce((s,y)=>s+y.w,0),T=group.reduce((s,y)=>s+y.w,0);x.q=x.known?(x.type==="BIG"?1:0):T?B/T:0;}
  const R=fit.ratio,V=(.8162353515625-.9*R)/.79603271484375,knownpol={BIG:pendingBackup(1,R,V),REG:pendingBackup(0,R,V)};
  let IN=0,OUT=0,N=0,S=0,S2=0;const cycles=100000;
  for(let cycle=0;cycle<cycles;cycle++){
   let cin=0,cout=0,free=false,state;
   do{const u=rand();state=states.find(x=>u<x.cdf)||states[states.length-1];cin+=free?0:3;cout+=state.code&31;free=!!(state.code&128);N++;}while(!state.type);
   let q=state.q,entry=!!(state.code&256);free=false;
   while(!entry){
    cin+=free?0:1;let u=rand(),sum=0,si=5;for(let i=0;i<probs.length;i++){sum+=probs[i];if(u<sum){si=i;break;}}
    const typeTry=q>=.5?"BIG":"REG",p=knownpol[typeTry],m=models.find(x=>x.type===state.type&&x.si===si&&x.order===p.order),a=p.input-1,fs=m.first[a],pp=p.policy.find(y=>y.observed===fs+1),m2=models.find(x=>x.type===state.type&&x.si===si&&x.order===pp.order),b=pp.input-1,ss=m2.second[a][b],c=pp.third.find(y=>y.observed===ss+1).input-1,code=m2.third[a][b][c];
    cout+=code&31;free=!!(code&128);entry=!!(code&256);if(!entry&&fs===19)q=state.type==="BIG"?1:0;
   }
   let rem=state.type==="BIG"?20:8,chance=state.type==="BIG"?1/15:1/9;
   while(rem>0){cin+=2;cout+=14;rem--;if(rand()<chance)rem+=3;}
   IN+=cin;OUT+=cout;const d=cout-R*cin;S+=d;S2+=d*d;
  }
  const sd=Math.sqrt((S2-S*S/cycles)/(cycles-1)),se=sd/Math.sqrt(cycles),meanIN=IN/cycles;
  simulation.push({setting:fit.setting,cycles,normalDraws:N,rtp:100*OUT/IN,target:100*R,half95:100*1.96*se/meanIN,z:(S/cycles)/se});
 }
 console.log(JSON.stringify({seed:123456789,random:"xorshift32, continuous sequence across settings",simulation},null,2));
}

const proposedWeights=[{"setting":1,"weights":{"replay":137023842,"grape":166599213,"bell":915525,"cherry":26532274,"piero":686643,"big":1873855,"reg":1087641,"cherry_big":632770,"cherry_reg":517720,"piero_big":81852,"piero_reg":88673,"ONE_A":34701,"ONE_B":34701,"ONE_CD":34701,"ONE_E":34701,"ONE_F":34701,"ONE_H":34701,"miss":663751786}},{"setting":2,"weights":{"replay":137023842,"grape":166783634,"bell":915525,"cherry":26476121,"piero":686643,"big":1937783,"reg":1180994,"cherry_big":630088,"cherry_reg":558758,"piero_big":85014,"piero_reg":85014,"ONE_A":35885,"ONE_B":35885,"ONE_CD":35885,"ONE_E":35885,"ONE_F":35885,"ONE_H":35885,"miss":663421274}},{"setting":3,"weights":{"replay":137023842,"grape":167751292,"bell":915525,"cherry":26391892,"piero":686643,"big":1972257,"reg":1344271,"cherry_big":642984,"cherry_reg":617770,"piero_big":89074,"piero_reg":82222,"ONE_A":36523,"ONE_B":36523,"ONE_CD":36523,"ONE_E":36523,"ONE_F":36523,"ONE_H":36523,"miss":662263090}},{"setting":4,"weights":{"replay":137023842,"grape":168647497,"bell":915525,"cherry":26307662,"piero":686643,"big":2070863,"reg":1568798,"cherry_big":642583,"cherry_reg":696131,"piero_big":93543,"piero_reg":79684,"ONE_A":38349,"ONE_B":38349,"ONE_CD":38349,"ONE_E":38349,"ONE_F":38349,"ONE_H":38349,"miss":661037135}},{"setting":5,"weights":{"replay":137023842,"grape":170413045,"bell":915525,"cherry":26223433,"piero":686643,"big":2245868,"reg":1730600,"cherry_big":649679,"cherry_reg":794053,"piero_big":99861,"piero_reg":78463,"ONE_A":41590,"ONE_B":41590,"ONE_CD":41590,"ONE_E":41590,"ONE_F":41590,"ONE_H":41590,"miss":658889448}},{"setting":6,"weights":{"replay":137023842,"grape":172701646,"bell":915525,"cherry":26111127,"piero":686643,"big":2437060,"reg":1951812,"cherry_big":623528,"cherry_reg":861062,"piero_big":103736,"piero_reg":69157,"ONE_A":45131,"ONE_B":45131,"ONE_CD":45131,"ONE_E":45131,"ONE_F":45131,"ONE_H":45131,"miss":656244076}}];
const rounded=[];
for(const item of proposedWeights){
 const rec={...records[item.setting-1]},w=item.weights;
 for(const k of ["reg","cherry_big","cherry_reg","piero_big","piero_reg"])rec[k]=w[k]/1e9;
 rec.big=(w.big+6*w.ONE_A)/1e9;rec.oneEach=w.ONE_A/1e9;rec.standaloneBigGross=w.big/1e9;
 const R=fits[item.setting-1].ratio,d=.0001,V=r=>(.8162353515625-.9*r)/.79603271484375;
 const x=normalBackup(rec,1,R,V(R)),y=normalBackup(rec,1,R+d,V(R+d));
 const root=R-x.value*d/(y.value-x.value),z=normalBackup(rec,1,root,V(root));
 if(Math.abs(z.value)>1e-11||Math.abs(100*(root-R))>.00003)throw Error("rounded verification failed");
 rounded.push({setting:item.setting,rtp:100*root,residual:z.value});
}
console.log(JSON.stringify({rounded},null,2));
