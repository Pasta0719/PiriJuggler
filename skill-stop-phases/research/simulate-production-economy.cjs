// Independent cycle sampler: consumes saved public-observation policies and production tables.
'use strict';
const fs=require('node:fs');
const analysis=JSON.parse(fs.readFileSync('build/skill-stop-economy-analysis.json','utf8'));
const tables=JSON.parse(fs.readFileSync('build/skill-stop-production-models.json','utf8'));
let seed=987654321;const random=()=>{seed^=seed<<13;seed^=seed>>>17;seed^=seed<<5;return(seed>>>0)/4294967296;};
const small=['MISS','R','G','B','C','P'],prob=[65206,8192,8192,5,320,5];
const names={miss:'MISS',replay:'R',grape:'G',bell:'B',cherry:'C',piero:'P',big:'BIG',reg:'REG',cherry_big:'CBIG',cherry_reg:'CREG',piero_big:'PBIG',piero_reg:'PREG',ONE_A:'7CBAR',ONE_B:'B7B',ONE_CD:'GGP',ONE_E:'PGP',ONE_F:'PP7',ONE_H:'PBARP'};
function play(models,name,policy){
 const a=policy.input-1,m=models.find(t=>t.name===name&&t.order===policy.order),f=m.first[a];
 const p=policy.policy.find(t=>t.observed===f+1),b=p.input-1,m2=models.find(t=>t.name===name&&t.order===p.order),s=m2.second[a][b];
 const c=p.third.find(t=>t.observed===s+1).input-1;
 return {type:m.type,code:m2.third[a][b][c],first:f,key:[f,s,m2.third[a][b][c]].join('/')};
}
const results=[];
for(const setting of analysis.settings){
 const atoms=[];
 for(const [key,weight] of Object.entries(setting.weights)){
  const name=names[key],model=tables.normal.find(t=>t.name===name),type=model.type;
  if(type!=='BIG'){atoms.push({name,weight,early:false,known:false});continue;}
  const cherry=name==='CBIG',each=weight*.05/(cherry?6:5);
  atoms.push({name,weight:weight*.95,early:false,known:false},{name,weight:each,early:false,known:true},{name:'F'+(name==='BIG'?'MISS':cherry?'C':name==='PBIG'?'P':name),weight:each,early:false,known:true},{name,weight:each*3,early:true,known:true});
  if(cherry)atoms.push({name:'BC',weight:each,early:false,known:true});
 }
 let cumulative=0;
 for(const atom of atoms){Object.assign(atom,play(tables.normal,atom.name,atom.early?setting.policy.early:setting.policy.main));atom.key=atom.early+'/'+atom.key;cumulative+=atom.weight;atom.cdf=cumulative;}
 for(const atom of atoms){const g=atoms.filter(t=>!t.known&&t.type&&!(t.code&256)&&t.key===atom.key),total=g.reduce((s,t)=>s+t.weight,0);atom.q=atom.known?1:total?g.filter(t=>t.type==='BIG').reduce((s,t)=>s+t.weight,0)/total:0;}
 for(const success of [0,.5,.8,1]){
  const cycles=25000;let input=0,output=0,sum=0,squares=0,draws=0;
  const target=setting.successRates.find(t=>t.success===success).rtp/100;
  for(let n=0;n<cycles;n++){
   let cin=0,cout=0,free=false,atom;
   do{const roll=random()*1e9;atom=atoms.find(t=>roll<t.cdf);if(!atom)throw Error('Missing draw');cin+=free?0:3;cout+=atom.code&31;free=!!(atom.code&128);draws++;}while(!atom.type);
   let entry=!!(atom.code&256),q=atom.q;free=false;
   while(!entry){
    cin+=free?0:1;let roll=random()*81920,i=0;for(;i<5&&roll>=prob[i];i++)roll-=prob[i];
    const tryBig=q>=.5,p=setting.pendingPolicy.find(t=>t.q===(tryBig?1:0));
    const round=play(tables.pending,atom.type+small[i],p);cout+=round.code&31;free=!!(round.code&128);entry=!!(round.code&256);
    if(!entry&&round.first===19)q=atom.type==='BIG'?1:0;
   }
   let remaining=atom.type==='BIG'?20:8;
   while(remaining){cin+=2;cout+=14;remaining--;if(random()<(atom.type==='BIG'?1/15:1/9)&&random()<success)remaining+=3;}
   input+=cin;output+=cout;const difference=cout-target*cin;sum+=difference;squares+=difference*difference;
  }
  const se=Math.sqrt((squares-sum*sum/cycles)/(cycles-1)/cycles)/(input/cycles),rtp=100*output/input,half95=196*se;
  results.push({setting:setting.setting,success,cycles,normalDraws:draws,input,output,rtp,target:target*100,half95,z:(rtp-target*100)/(100*se)});
 }
}
fs.writeFileSync('build/skill-stop-economy-simulation.json',JSON.stringify({initialSeed:987654321,random:'xorshift32 continuous across all cases',endPolicy:'Complete every drawn bonus and all added games; no truncated cycle',results},null,2));
console.log(JSON.stringify(results.map(t=>({setting:t.setting,success:t.success,rtp:t.rtp,z:t.z})),null,2));
if(results.some(t=>Math.abs(t.z)>4))throw Error('Independent simulation exceeds four standard errors');
