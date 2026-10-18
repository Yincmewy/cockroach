// Reproducible rigid-part flight rig. Original CK GLB is never overwritten.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { MeshoptSimplifier } from 'meshoptimizer';
await MeshoptSimplifier.ready;
const root = path.resolve(import.meta.dirname, '..');
const directory = path.join(root, 'src/main/resources/assets/cockroach/models/entity');
const source = fs.readFileSync(path.join(directory, 'cockroach.glb'));
const length = source.readUInt32LE(12);
const original = JSON.parse(source.subarray(20, 20 + length));
const bin = source.subarray(28 + length);
function read(index) {
  const a = original.accessors[index], view = original.bufferViews[a.bufferView];
  const width = { SCALAR: 1, VEC2: 2, VEC3: 3, VEC4: 4 }[a.type];
  const bytes = { 5126: 4, 5125: 4, 5123: 2 }[a.componentType];
  return Array.from({ length: a.count * width }, (_, i) => {
    const offset = (view.byteOffset || 0) + (a.byteOffset || 0) + Math.floor(i / width) * (view.byteStride || width * bytes) + i % width * bytes;
    return a.componentType === 5126 ? bin.readFloatLE(offset) : a.componentType === 5125 ? bin.readUInt32LE(offset) : bin.readUInt16LE(offset);
  });
}
const primitive = original.meshes[0].primitives[0];
const positions = read(primitive.attributes.POSITION), normals = read(primitive.attributes.NORMAL);
const uvs = read(primitive.attributes.TEXCOORD_0), indices = read(primitive.indices);
const parents = Array.from({ length: positions.length / 3 }, (_, i) => i);
function find(i) { return parents[i] === i ? i : parents[i] = find(parents[i]); }
function join(a, b) { parents[find(a)] = find(b); }
// Weld UV seams only for component detection, retaining all original UVs in the output.
const welded = new Map();
for (let i = 0; i < parents.length; i++) {
  const key = positions.slice(i * 3, i * 3 + 3).map(x => x.toFixed(5)).join();
  if (welded.has(key)) join(i, welded.get(key)); else welded.set(key, i);
}
for (let i = 0; i < indices.length; i += 3) { join(indices[i], indices[i + 1]); join(indices[i], indices[i + 2]); }
const components = new Map();
for (let i = 0; i < indices.length; i += 3) {
  const key = find(indices[i]);
  const group = components.get(key) || { indices: [], min: [Infinity, Infinity, Infinity], max: [-Infinity, -Infinity, -Infinity] };
  group.indices.push(...indices.slice(i, i + 3));
  for (const v of indices.slice(i, i + 3)) for (let c = 0; c < 3; c++) {
    group.min[c] = Math.min(group.min[c], positions[v * 3 + c]);
    group.max[c] = Math.max(group.max[c], positions[v * 3 + c]);
  }
  components.set(key, group);
}
const definitions = new Map([['body', { indices: [], pivot: [0, 0, 0] }]]);
for (const group of components.values()) {
  let name = 'body', pivot = [0, 0, 0];
  const sign = (group.min[0] + group.max[0]) < 0 ? -1 : 1;
  const side = sign < 0 ? 'left' : 'right';
  if (group.indices.length / 3 === 892 && group.min[1] > 0.6 && group.max[2] < -0.24) {
    name = `elytron_${side}`;
    pivot = [sign * 0.13, 0.80, -0.28];
  } else if (group.indices.length / 3 === 1920 && group.min[1] < 0.02) {
    const section = group.max[2] > 0.4 ? 'front' : group.min[2] < -1 ? 'back' : 'middle';
    name = `leg_${side}_${section}`;
    pivot = [sign * 0.10, 0.61, section === 'front' ? -0.03 : section === 'middle' ? -0.32 : -0.63];
  }
  if (!definitions.has(name)) definitions.set(name, { indices: [], pivot });
  definitions.get(name).indices.push(...group.indices);
}
if (definitions.size !== 9) throw new Error('Source anatomy changed: expected body, two elytra and six legs');
const baseParts = [...definitions].map(([name, group]) => {
  const remap = new Map();
  for (const old of group.indices) if (!remap.has(old)) remap.set(old, remap.size);
  const p = [], n = [], uv = [];
  for (const old of remap.keys()) {
    p.push(...positions.slice(old * 3, old * 3 + 3).map((x, c) => x - group.pivot[c]));
    n.push(...normals.slice(old * 3, old * 3 + 3)); uv.push(...uvs.slice(old * 2, old * 2 + 2));
  }
  return { name, pivot: group.pivot, p, n, uv, indices: group.indices.map(i => remap.get(i)), material: 0 };
});

// New curved hindwing membranes: broad fan outline, ribbed amber texture, independent hinges.
for (const sign of [-1, 1]) {
  const p = [0, 0, 0], n = [0, 1, 0], uv = [0, 0], ix = [];
  const outline = [[0.06,-0.05],[0.25,-0.16],[0.51,-0.40],[0.66,-0.73],[0.65,-1.05],[0.49,-1.34],[0.25,-1.52],[0.07,-1.49],[-0.04,-1.12],[-0.045,-0.63]];
  for (const [x,z] of outline) {
    p.push(sign * x, 0.045 * Math.sin(-z * Math.PI / 1.52), z);
    n.push(0, 1, 0); uv.push((x + 0.05) / 0.72, -z / 1.52);
  }
  for (let i = 1; i <= outline.length; i++) ix.push(0, i, i % outline.length + 1);
  baseParts.push({ name: sign < 0 ? 'wing_left' : 'wing_right', pivot: [sign * 0.14, 0.78, -0.32], p, n, uv, indices: ix, material: 1 });
}
function crc32(data) { let crc = -1; for (const b of data) { crc ^= b; for (let i=0;i<8;i++) crc = (crc>>>1) ^ ((crc&1) ? 0xedb88320 : 0); } return (crc ^ -1) >>> 0; }
function pngChunk(name, bytes) { const kind=Buffer.from(name), head=Buffer.alloc(4), tail=Buffer.alloc(4); head.writeUInt32BE(bytes.length);tail.writeUInt32BE(crc32(Buffer.concat([kind,bytes])));return Buffer.concat([head,kind,bytes,tail]); }
const width = 128, height = 256, pixels = Buffer.alloc((width * 4 + 1) * height);
for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
  const u = x / (width - 1), v = y / (height - 1);
  const ribs = Math.abs(Math.sin(Math.atan2(u - 0.07, v + 0.015) * 19));
  const cross = Math.abs(Math.sin(v * 65 + u * 9));
  const vein = ribs < 0.09 || cross < 0.045;
  const offset = y * (width * 4 + 1) + 1 + x * 4;
  pixels.set(vein ? [117, 73, 32, 235] : [215, 174, 106, 165], offset);
}
const header = Buffer.alloc(13); header.writeUInt32BE(width);header.writeUInt32BE(height,4);header[8]=8;header[9]=6;
const membrane = Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),pngChunk('IHDR',header),pngChunk('IDAT',zlib.deflateSync(pixels)),pngChunk('IEND',Buffer.alloc(0))]);
const img = original.images[original.textures[original.materials[0].pbrMetallicRoughness.baseColorTexture.index].source];
const imageView = original.bufferViews[img.bufferView];
const texture = bin.subarray(imageView.byteOffset, imageView.byteOffset + imageView.byteLength);
const report = [];
for (const [tier, ratio, error] of [['high',1,0],['medium',4000/29454,0.02],['low',800/29454,0.05]]) {
  const doc = { asset: { version: '2.0', generator: 'Cockroach flight rig tool', extras: { ...original.asset.extras, modifications: 'Separated original elytra and legs into hinged rigid parts; added procedural hindwing membranes; stripped unused skin animation; simplified LODs.' } }, scene: 0, scenes: [{ nodes: [] }], nodes: [], meshes: [],
    materials: [{ name:'CK_original_body',doubleSided:true,pbrMetallicRoughness:{baseColorTexture:{index:0},metallicFactor:0,roughnessFactor:1}}, {name:'new_hindwing_membrane',doubleSided:true,alphaMode:'BLEND',pbrMetallicRoughness:{baseColorTexture:{index:1},metallicFactor:0,roughnessFactor:0.8}}],
    textures:[{source:0},{source:1}],images:[],buffers:[{byteLength:0}],bufferViews:[],accessors:[] };
  const chunks=[]; let offset=0;
  function addView(bytes) { const index=doc.bufferViews.length;doc.bufferViews.push({buffer:0,byteOffset:offset,byteLength:bytes.length});const pad=Buffer.alloc((4-bytes.length%4)%4);chunks.push(bytes,pad);offset+=bytes.length+pad.length;return index; }
  function accessor(array,type,componentType) { const bufferView=addView(Buffer.from(array.buffer));const size={VEC3:3,VEC2:2,SCALAR:1}[type];const index=doc.accessors.length;const entry={bufferView,componentType,count:array.length/size,type};if(type==='VEC3'){entry.min=[Infinity,Infinity,Infinity];entry.max=[-Infinity,-Infinity,-Infinity];for(let i=0;i<array.length;i++){entry.min[i%3]=Math.min(entry.min[i%3],array[i]);entry.max[i%3]=Math.max(entry.max[i%3],array[i]);}}doc.accessors.push(entry);return index; }
  let count=0;
  for (const part of baseParts) {
    let selected=Uint32Array.from(part.indices);
    if(ratio<1 && part.material===0) {
      const attrs=new Float32Array(part.p.length/3*5);
      for(let i=0;i<part.p.length/3;i++)attrs.set([...part.n.slice(i*3,i*3+3),...part.uv.slice(i*2,i*2+2)],i*5);
      [selected]=MeshoptSimplifier.simplifyWithAttributes(selected,Float32Array.from(part.p),3,attrs,5,[0.05,0.05,0.05,0.5,0.5],null,Math.max(24,Math.floor(selected.length/3*ratio))*3,error,['Permissive']);
    }
    const remap=new Map();for(const old of selected)if(!remap.has(old))remap.set(old,remap.size);
    const p=[],n=[],uv=[];for(const old of remap.keys()){p.push(...part.p.slice(old*3,old*3+3));n.push(...part.n.slice(old*3,old*3+3));uv.push(...part.uv.slice(old*2,old*2+2));}
    const attributes={POSITION:accessor(Float32Array.from(p),'VEC3',5126),NORMAL:accessor(Float32Array.from(n),'VEC3',5126),TEXCOORD_0:accessor(Float32Array.from(uv),'VEC2',5126)};
    const indexAccessor=accessor(Uint32Array.from(selected,i=>remap.get(i)),'SCALAR',5125);
    const index=doc.nodes.length;doc.scenes[0].nodes.push(index);doc.nodes.push({name:part.name,mesh:index,translation:part.pivot});doc.meshes.push({name:part.name,primitives:[{attributes,indices:indexAccessor,material:part.material,mode:4}]});count+=selected.length/3;
  }
  for(const bytes of [texture,membrane])doc.images.push({bufferView:addView(bytes),mimeType:'image/png'});
  doc.buffers[0].byteLength=offset;
  let json=Buffer.from(JSON.stringify(doc));json=Buffer.concat([json,Buffer.alloc((4-json.length%4)%4,32)]);
  const data=Buffer.concat(chunks),head=Buffer.alloc(20),binHead=Buffer.alloc(8);head.write('glTF');head.writeUInt32LE(2,4);head.writeUInt32LE(28+json.length+data.length,8);head.writeUInt32LE(json.length,12);head.write('JSON',16);binHead.writeUInt32LE(data.length);binHead.write('BIN\0',4);
  const name=`cockroach_flight_${tier}.glb`;fs.writeFileSync(path.join(directory,name),Buffer.concat([head,json,binHead,data]));
  report.push({name,triangles:count,parts:doc.nodes.map(n=>n.name)});
}
fs.writeFileSync(path.join(root,'tools/flight-model-report.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));
