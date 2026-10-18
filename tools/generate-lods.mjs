import fs from 'node:fs';
import path from 'node:path';
import { MeshoptSimplifier } from 'meshoptimizer';
await MeshoptSimplifier.ready;
const root=path.resolve(import.meta.dirname,'..');
const source=fs.readFileSync(path.join(root,'src/main/resources/assets/cockroach/models/entity/cockroach.glb'));
const jsonLength=source.readUInt32LE(12);
const gltf=JSON.parse(source.subarray(20,20+jsonLength).toString());
const binary=source.subarray(28+jsonLength);
const primitive=gltf.meshes[0].primitives[0];
function readAccessor(index) {
  const a=gltf.accessors[index], v=gltf.bufferViews[a.bufferView];
  const width={SCALAR:1,VEC2:2,VEC3:3,VEC4:4}[a.type];
  const bytes={5126:4,5125:4,5123:2}[a.componentType];
  const result=a.componentType===5126?new Float32Array(a.count*width):new Uint32Array(a.count*width);
  for(let i=0;i<a.count;i++) for(let c=0;c<width;c++) {
    const offset=(v.byteOffset||0)+(a.byteOffset||0)+i*(v.byteStride||width*bytes)+c*bytes;
    result[i*width+c]=a.componentType===5126?binary.readFloatLE(offset):a.componentType===5125?binary.readUInt32LE(offset):binary.readUInt16LE(offset);
  }
  return result;
}
const positions=readAccessor(primitive.attributes.POSITION), normals=readAccessor(primitive.attributes.NORMAL), uvs=readAccessor(primitive.attributes.TEXCOORD_0), indices=readAccessor(primitive.indices);
const attributes=new Float32Array(positions.length/3*5);
for(let i=0;i<positions.length/3;i++) attributes.set([...normals.subarray(i*3,i*3+3),...uvs.subarray(i*2,i*2+2)],i*5);
const output=path.join(root,'src/main/resources/assets/cockroach/models/entity');
fs.mkdirSync(output,{recursive:true});
const image=gltf.images[gltf.textures[gltf.materials[0].pbrMetallicRoughness.baseColorTexture.index].source];
const imageView=gltf.bufferViews[image.bufferView];
const imageBytes=binary.subarray(imageView.byteOffset,imageView.byteOffset+imageView.byteLength);
fs.writeFileSync(path.join(output,'cockroach_basecolor.png'),imageBytes);
const report=[];
for(const [name,target,error] of [['medium',4000,0.015],['low',800,0.04]]) {
  const [simplified,actualError]=MeshoptSimplifier.simplifyWithAttributes(indices,positions,3,attributes,5,[0.05,0.05,0.05,0.5,0.5],null,target*3,error,['Permissive']);
  const remap=new Map(); for(const index of simplified) if(!remap.has(index)) remap.set(index,remap.size);
  const p=new Float32Array(remap.size*3),n=new Float32Array(remap.size*3),uv=new Float32Array(remap.size*2);
  for(const [old,index] of remap) {p.set(positions.subarray(old*3,old*3+3),index*3); n.set(normals.subarray(old*3,old*3+3),index*3);uv.set(uvs.subarray(old*2,old*2+2),index*2);}
  const idx=Uint32Array.from(simplified,index=>remap.get(index));
  const arrays=[p,n,uv,idx], chunks=arrays.map(a=>Buffer.from(a.buffer)), views=[]; let offset=0;
  for(const b of chunks){views.push({buffer:0,byteOffset:offset,byteLength:b.length});offset+=b.length;}
  views.push({buffer:0,byteOffset:offset,byteLength:imageBytes.length});
  chunks.push(imageBytes,Buffer.alloc((4-imageBytes.length%4)%4));offset+=imageBytes.length+(4-imageBytes.length%4)%4;
  const min=[Infinity,Infinity,Infinity],max=[-Infinity,-Infinity,-Infinity];
  for(let i=0;i<p.length;i++) {min[i%3]=Math.min(min[i%3],p[i]);max[i%3]=Math.max(max[i%3],p[i]);}
  const doc={asset:{version:'2.0',generator:'cockroach LOD tool / meshoptimizer 1.2.0',extras:{...gltf.asset.extras,modifications:'Static LOD simplification, skin/animation removed; embedded base-color texture.'}},scene:0,scenes:[{nodes:[0]}],nodes:[{name:'Cockroach',mesh:0}],meshes:[{primitives:[{attributes:{POSITION:0,NORMAL:1,TEXCOORD_0:2},indices:3,material:0,mode:4}]}],materials:[{name:'roach_one_texture1',doubleSided:true,pbrMetallicRoughness:{baseColorTexture:{index:0},metallicFactor:0,roughnessFactor:1}}],textures:[{source:0}],images:[{bufferView:4,mimeType:'image/png'}],buffers:[{byteLength:offset}],bufferViews:views,accessors:[{bufferView:0,componentType:5126,count:remap.size,type:'VEC3',min,max},{bufferView:1,componentType:5126,count:remap.size,type:'VEC3'},{bufferView:2,componentType:5126,count:remap.size,type:'VEC2'},{bufferView:3,componentType:5125,count:idx.length,type:'SCALAR'}]};
  let json=Buffer.from(JSON.stringify(doc));json=Buffer.concat([json,Buffer.alloc((4-json.length%4)%4,32)]);
  const bin=Buffer.concat(chunks), header=Buffer.alloc(20),binHeader=Buffer.alloc(8);
  header.write('glTF');header.writeUInt32LE(2,4);header.writeUInt32LE(28+json.length+bin.length,8);header.writeUInt32LE(json.length,12);header.write('JSON',16);binHeader.writeUInt32LE(bin.length);binHeader.write('BIN\0',4);
  fs.writeFileSync(path.join(output,`cockroach_${name}.glb`),Buffer.concat([header,json,binHeader,bin]));
  report.push({name,triangles:idx.length/3,vertices:remap.size,relativeError:actualError});
}
fs.writeFileSync(path.join(root,'tools/lod-report.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));
