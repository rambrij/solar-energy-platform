import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const here=path.dirname(fileURLToPath(import.meta.url));
const frontend=path.resolve(here,'..');
const repo=path.resolve(frontend,'..');
const registryPath=path.join(repo,'backend/src/main/resources/authorization-policy-registry.json');
const registry=JSON.parse(fs.readFileSync(registryPath,'utf8'));
const permissions=new Set([...registry.vendorPermissions,...registry.platformPermissions,...(registry.customerPermissions??[])]);
const classified=[...registry.vendorPermissions,...registry.reservedVendorPermissions,...registry.orphanedPermissions,...registry.conflictingPermissions.map(p=>p.id)];
if(new Set(classified).size!==classified.length)throw new Error('A permission appears in more than one canonical classification');
if(registry.unknownPermissions?.length)throw new Error('Unknown permission identifiers must be resolved before activation: '+registry.unknownPermissions.join(', '));
if(!Array.isArray(registry.permissionTraceability))throw new Error('Canonical permissionTraceability is required');
const traceIds=registry.permissionTraceability.map(item=>item.permission);
const expectedTrace=[...registry.vendorPermissions,...registry.platformPermissions].sort();
if(JSON.stringify([...traceIds].sort())!==JSON.stringify(expectedTrace))throw new Error('Every implemented vendor/platform permission must have exactly one traceability record');
for(const item of registry.permissionTraceability){
  for(const field of ['status','actor','endpoint','service','scope','ui','test'])if(typeof item[field]!=='string'||!item[field].trim())throw new Error('Incomplete permission traceability for '+item.permission+': '+field);
  if(item.status!=='IMPLEMENTED')throw new Error('Only IMPLEMENTED permissions may appear in assignable registries: '+item.permission);
}
for(const permission of [...registry.reservedVendorPermissions,...registry.orphanedPermissions,...registry.conflictingPermissions.map(p=>p.id)]){
  if(permissions.has(permission))throw new Error('Non-assignable permission appears in an active frontend/backend catalogue: '+permission);
}
const permissionSource=fs.readFileSync(path.join(frontend,'src/usePermissions.ts'),'utf8');
const navigationSource=fs.readFileSync(path.join(frontend,'src/navigation.ts'),'utf8');
const block=permissionSource.match(/export const NAVIGATION_PERMISSIONS:[^=]+=[\s\S]*?\{([\s\S]*?)\n\};/);
if(!block)throw new Error('NAVIGATION_PERMISSIONS block was not found');
const actual={};
const entryPattern=/'([^']+)':\s*\{\s*(anyOf|allOf):\s*\[([^\]]*)\]\s*\}/g;
for(const match of block[1].matchAll(entryPattern)){
  const [,id,kind,raw]=match;
  const values=[...raw.matchAll(/'([^']+)'/g)].map(x=>x[1]).sort();
  actual[id]={[kind]:values};
}
const expected=registry.frontendRoutes;
const normalize=value=>Object.fromEntries(Object.entries(value).sort(([a],[b])=>a.localeCompare(b)).map(([key,rule])=>[key,Object.fromEntries(Object.entries(rule).map(([kind,items])=>[kind,[...items].sort()]))]));

const platformBlock=permissionSource.match(/export const PLATFORM_PAGE_PERMISSIONS:[^=]*=\\{([\\s\\S]*?)\\n\\};/);
if(!platformBlock)throw new Error('PLATFORM_PAGE_PERMISSIONS block was not found');
const platformActual={};
const platformEntryPattern=/'([^']+)':\\s*\\{\\s*(anyOf|allOf):\\s*\\[([^\\]]*)\\]\\s*\\}/g;
for(const match of platformBlock[1].matchAll(platformEntryPattern)){
  const [,id,kind,raw]=match;
  platformActual[id]={[kind]:[...raw.matchAll(/'([^']+)'/g)].map(x=>x[1]).sort()};
}
if(JSON.stringify(normalize(platformActual))!==JSON.stringify(normalize(registry.frontendPlatformPages??{}))){
  throw new Error('Frontend PLATFORM_PAGE_PERMISSIONS diverge from canonical authorization-policy-registry.json');
}
for(const [page,rule] of Object.entries(registry.frontendPlatformPages??{})){
  for(const permission of Object.values(rule).flat()){
    if(!registry.platformPermissions.includes(permission))throw new Error('Unknown platform permission '+permission+' in page '+page);
  }
}

if(JSON.stringify(normalize(actual))!==JSON.stringify(normalize(expected))){
  throw new Error('Frontend NAVIGATION_PERMISSIONS diverge from canonical authorization-policy-registry.json');
}
const navIds=[...navigationSource.matchAll(/\bid:\s*'([^']+)'/g)].map(x=>x[1]);
const uniqueNav=[...new Set(navIds)];
if(uniqueNav.length!==navIds.length)throw new Error('Duplicate frontend navigation ID detected');
const expectedRoutes=Object.keys(expected).sort();
if(JSON.stringify([...uniqueNav].sort())!==JSON.stringify(expectedRoutes)){
  throw new Error('Every workspace navigation item must have exactly one canonical route policy');
}
for(const [route,rule] of Object.entries(expected)){
  for(const permission of Object.values(rule).flat()){
    if(!permissions.has(permission))throw new Error(`Unknown permission ${permission} in route ${route}`);
  }
}
async function walk(directory){
  const found=[];
  for(const entry of fs.readdirSync(directory,{withFileTypes:true})){
    const full=path.join(directory,entry.name);
    if(entry.isDirectory())found.push(...await walk(full));
    else if(entry.isFile()&&entry.name.endsWith('Controller.java'))found.push(full);
  }
  return found;
}
const javaControllers=await walk(path.join(repo,'backend/src/main/java'));
const names=javaControllers.map(file=>path.basename(file,'.java')).sort();
const registered=Object.keys(registry.controllerPolicies).sort();
if(JSON.stringify(names)!==JSON.stringify(registered)){
  const missing=names.filter(x=>!registered.includes(x));
  const stale=registered.filter(x=>!names.includes(x));
  throw new Error(`Backend controller inventory drift. Missing policies: ${missing.join(', ')}; stale entries: ${stale.join(', ')}`);
}
for(const [name,policy] of Object.entries(registry.controllerPolicies)){
  if(typeof policy!=='string'||policy.trim().length===0)throw new Error(`Missing endpoint policy description for ${name}`);
}
console.log(`Authorization registry verified: ${expectedRoutes.length} frontend routes, ${names.length} backend controllers, ${permissions.size} permission identifiers.`);
