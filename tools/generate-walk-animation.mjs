// Sample the supplied GLB's actual Take 001 joint tracks; no replacement gait is authored here.
import fs from 'node:fs';
import path from 'node:path';
const directory = path.resolve(import.meta.dirname, '../src/main/resources/assets/cockroach/models/entity');
function glb(name) {
  const bytes=fs.readFileSync(path.join(directory,name)),length=bytes.readUInt32LE(12);
  const doc=JSON.parse(bytes.subarray(20,20+length)),bin=bytes.subarray(28+length);
  function read(index) {
    const a=doc.accessors[index],v=doc.bufferViews[a.bufferView],width={SCALAR:1,VEC2:2,VEC3:3,VEC4:4,MAT4:16}[a.type],size={5126:4,5125:4,5123:2}[a.componentType];
    return Array.from({length:a.count*width},(_,i)=>{const offset=(v.byteOffset||0)+(a.byteOffset||0)+Math.floor(i/width)*(v.byteStride||width*size)+i%width*size;return a.componentType===5126?bin.readFloatLE(offset):a.componentType===5125?bin.readUInt32LE(offset):bin.readUInt16LE(offset);});
  }
  return {doc,read};
}
const source=glb('cockroach.glb'),g=source.doc,clip=g.animations[0],skin=g.skins[0],primitive=g.meshes[0].primitives[0];
const identity=()=>[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1];
function multiply(a,b){const out=Array(16).fill(0);for(let c=0;c<4;c++)for(let r=0;r<4;r++)for(let k=0;k<4;k++)out[c*4+r]+=a[k*4+r]*b[c*4+k];return out;}
function trs(t,q,s){const [x,y,z,w]=q,[sx,sy,sz]=s;return [(1-2*y*y-2*z*z)*sx,(2*x*y+2*z*w)*sx,(2*x*z-2*y*w)*sx,0,(2*x*y-2*z*w)*sy,(1-2*x*x-2*z*z)*sy,(2*y*z+2*x*w)*sy,0,(2*x*z+2*y*w)*sz,(2*y*z-2*x*w)*sz,(1-2*x*x-2*y*y)*sz,0,...t,1];}
function slerp(a,b,f){let dot=a.reduce((sum,x,i)=>sum+x*b[i],0);if(dot<0){b=b.map(x=>-x);dot=-dot;}if(dot>0.9995){const v=a.map((x,i)=>x+(b[i]-x)*f),n=Math.hypot(...v);return v.map(x=>x/n);}const angle=Math.acos(Math.min(1,dot)),s=Math.sin(angle);return a.map((x,i)=>(x*Math.sin((1-f)*angle)+b[i]*Math.sin(f*angle))/s);}
const tracks=clip.channels.map(channel=>{const sampler=clip.samplers[channel.sampler];if(sampler.interpolation!=='LINEAR')throw new Error('Unexpected source interpolation');return {...channel.target,times:source.read(sampler.input),values:source.read(sampler.output)};});
const end=Math.max(...tracks.map(t=>t.times.at(-1)));
// The export ends on its last keyed frame. Interpolate that frame back to frame 0 over one 24-fps frame.
const duration=end+1/24,frames=48,joints=skin.joints.length;
const bind=source.read(skin.inverseBindMatrices);
const parent=Array(g.nodes.length).fill(-1);g.nodes.forEach((n,i)=>(n.children||[]).forEach(c=>parent[c]=i));
function sample(track,time){const width=track.path==='rotation'?4:3;let lo=0,hi=0,f=0;if(time>end){lo=track.times.length-1;hi=0;f=(time-end)/(duration-end);}else{while(lo+1<track.times.length&&track.times[lo+1]<=time)lo++;hi=Math.min(lo+1,track.times.length-1);f=hi===lo?0:(time-track.times[lo])/(track.times[hi]-track.times[lo]);}f=Math.max(0,Math.min(1,f));const a=track.values.slice(lo*width,(lo+1)*width),b=track.values.slice(hi*width,(hi+1)*width);return track.path==='rotation'?slerp(a,b,f):a.map((x,i)=>x+(b[i]-x)*f);}
const poses=[];
for(let frame=0;frame<frames;frame++){
  const nodes=g.nodes.map(n=>({t:n.translation||[0,0,0],r:n.rotation||[0,0,0,1],s:n.scale||[1,1,1],matrix:n.matrix}));
  for(const track of tracks){const n=nodes[track.node],v=sample(track,frame/frames*duration);n[{translation:'t',rotation:'r',scale:'s'}[track.path]]=v;}
  const world=[];function global(i){if(world[i])return world[i];const n=nodes[i],local=n.matrix||trs(n.t,n.r,n.s);return world[i]=parent[i]<0?local:multiply(global(parent[i]),local);}
  // Mesh node has an identity world transform in the supplied GLB (the two root rotations cancel).
  const meshWorld=global(g.nodes.findIndex(n=>n.mesh===0));if(meshWorld.some((x,i)=>Math.abs(x-identity()[i])>1e-5))throw new Error('Source mesh transform changed');
  for(let j=0;j<joints;j++)poses.push(...multiply(global(skin.joints[j]),bind.slice(j*16,(j+1)*16)));
}
const sourcePositions=source.read(primitive.attributes.POSITION),sourceUvs=source.read(primitive.attributes.TEXCOORD_0),sourceJoints=source.read(primitive.attributes.JOINTS_0),sourceWeights=source.read(primitive.attributes.WEIGHTS_0);
// Retarget the authored rig's vertical origin to the game's feet plane, preserving all limb tracks.
const footPlane=g.accessors[primitive.attributes.POSITION].min[1],groundOffsets=[];
for(let frame=0;frame<frames;frame++){
  let bottom=Infinity;
  for(let v=0;v<sourcePositions.length/3;v++){
    let y=0;for(let k=0;k<4;k++){const weight=sourceWeights[v*4+k];if(!weight)continue;const bone=(frame*joints+sourceJoints[v*4+k])*16;y+=weight*(poses[bone+1]*sourcePositions[v*3]+poses[bone+5]*sourcePositions[v*3+1]+poses[bone+9]*sourcePositions[v*3+2]+poses[bone+13]);}
    bottom=Math.min(bottom,y);
  }
  const offset=footPlane-bottom;groundOffsets.push(offset);
  for(let j=0;j<joints;j++)poses[(frame*joints+j)*16+13]+=offset;
}
const lookup=new Map();const key=(p,uv,i)=>[...p.slice(i*3,i*3+3),...uv.slice(i*2,i*2+2)].map(v=>v.toPrecision(9)).join(',');
for(let i=0;i<sourcePositions.length/3;i++)lookup.set(key(sourcePositions,sourceUvs,i),i);
const chunks=[];function u32(n){const b=Buffer.alloc(4);b.writeUInt32LE(n);chunks.push(b);}function f32(values){const b=Buffer.alloc(values.length*4);values.forEach((v,i)=>b.writeFloatLE(v,i*4));chunks.push(b);}
chunks.push(Buffer.from('CWK1'));u32(frames);u32(joints);f32([duration]);u32(3);f32(poses);
const report=[];
for(const file of ['cockroach.glb','cockroach_medium.glb','cockroach_low.glb']){
  const model=glb(file),p=model.doc.meshes[0].primitives[0],pos=model.read(p.attributes.POSITION),norm=model.read(p.attributes.NORMAL),uv=model.read(p.attributes.TEXCOORD_0),indices=model.read(p.indices);
  const vertices=[];for(let i=0;i<pos.length/3;i++){const original=lookup.get(key(pos,uv,i));if(original===undefined)throw new Error('LOD vertex no longer matches source skin');vertices.push(...pos.slice(i*3,i*3+3),...norm.slice(i*3,i*3+3),...uv.slice(i*2,i*2+2),...sourceJoints.slice(original*4,original*4+4),...sourceWeights.slice(original*4,original*4+4));}
  u32(pos.length/3);u32(indices.length);f32(vertices);for(const index of indices)u32(index);
  report.push({file,vertices:pos.length/3,triangles:indices.length/3});
}
const output=Buffer.concat(chunks);fs.writeFileSync(path.join(directory,'cockroach_walk.bin'),output);
fs.writeFileSync(path.resolve(import.meta.dirname,'walk-animation-report.json'),JSON.stringify({source:'cockroach.glb',clip:clip.name,sourceChannels:tracks.length,sourceDuration:end,loopDuration:duration,frames,joints,groundOffsetRange:[Math.min(...groundOffsets),Math.max(...groundOffsets)],bytes:output.length,lods:report},null,2)+'\n');
console.log({clip:clip.name,frames,joints,duration,bytes:output.length,lods:report});
