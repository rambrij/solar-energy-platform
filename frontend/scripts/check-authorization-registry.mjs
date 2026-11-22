import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

import assert from 'node:assert/strict';

function validatePermissionReferences(source, permissions, label='frontend source'){
  const references=[];
  const calls=/(?:hasPermission|hasAnyPermission|hasAllPermissions|hasNavigationPermission|hasPlatformPagePermission|can)\s*\(([^)]*)\)/gs;
  for(const match of source.matchAll(calls)){
    for(const literal of match[1].matchAll(/['"]([A-Z][A-Z0-9_]+|CUSTOMER)['"]/g))references.push(literal[1]);
  }
  for(const match of source.matchAll(/\.permissions\.includes\(\s*['"]([A-Z][A-Z0-9_]+|CUSTOMER)['"]\s*\)/g))references.push(match[1]);
  const unknown=[...new Set(references.filter(permission=>!permissions.has(permission)))];
  if(unknown.length)throw new Error('Unknown permission reference in '+label+': '+unknown.join(', '));
}

function validateNoHardcodedRoleAuthorization(source, label='frontend source'){
  const patterns=[
    /\b(?:role|roleKey|roleName|userRole)\s*(?:===|!==|==|!=)\s*['"](?:PLATFORM_SUPER_ADMIN|PLATFORM_SUPPORT|VENDOR_ADMIN|POS_CASHIER|CASHIER|ACCOUNTANT|ADMIN|SUPER_ADMIN|CUSTOMER|VENDOR)['"]/i,
    /['"](?:PLATFORM_SUPER_ADMIN|PLATFORM_SUPPORT|VENDOR_ADMIN|POS_CASHIER|CASHIER|ACCOUNTANT|ADMIN|SUPER_ADMIN|CUSTOMER|VENDOR)['"]\s*(?:===|!==|==|!=)\s*\b(?:role|roleKey|roleName|userRole)\b/i
  ];
  if(patterns.some(pattern=>pattern.test(source)))
    throw new Error('Hardcoded role-name authorization is prohibited in '+label+'; authorize using server-derived permissions');
}

// The guard itself is mutation-tested: these fixtures must be rejected.
assert.throws(()=>validatePermissionReferences("can('UNKNOWN_P204_PERMISSION')",new Set()),
  /Unknown permission reference/);
assert.throws(()=>validateNoHardcodedRoleAuthorization("if (user.role === 'ADMIN') allow();"),
  /Hardcoded role-name authorization/);

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

const platformStart=permissionSource.indexOf('export const PLATFORM_PAGE_PERMISSIONS:');
const platformEnd=platformStart<0?-1:permissionSource.indexOf('};',platformStart);
if(platformStart<0||platformEnd<0)throw new Error('PLATFORM_PAGE_PERMISSIONS block was not found');
const platformBlock=permissionSource.slice(platformStart,platformEnd+2);
const platformActual={};
const platformEntryPattern=/'([^']+)':\s*\{\s*(anyOf|allOf):\s*\[([^\]]*)\]\s*\}/g;
for(const match of platformBlock.matchAll(platformEntryPattern)){
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


const sourceFiles=[];
async function collectFrontendSources(directory){
  for(const entry of fs.readdirSync(directory,{withFileTypes:true})){
    const full=path.join(directory,entry.name);
    if(entry.isDirectory())await collectFrontendSources(full);
    else if(entry.isFile()&&/\.(tsx|ts)$/.test(entry.name)&&!/(\.test|\.spec)\.tsx?$/.test(entry.name))
      sourceFiles.push(full);
  }
}
await collectFrontendSources(path.join(frontend,'src'));
for(const file of sourceFiles){
  const source=fs.readFileSync(file,'utf8');
  validatePermissionReferences(source,permissions,path.relative(repo,file));
  validateNoHardcodedRoleAuthorization(source,path.relative(repo,file));
}

const platformUiSources=['PlatformApp.tsx','PlatformSubscriptions.tsx','PlatformBoundary.tsx']
  .map(file=>[file,fs.readFileSync(path.join(frontend,'src',file),'utf8')]);
for(const [file,source] of platformUiSources){
  const used=[...source.matchAll(/['"](PLATFORM_[A-Z0-9_]+)['"]/g)].map(match=>match[1]);
  const unknown=[...new Set(used.filter(permission=>!registry.platformPermissions.includes(permission)))];
  if(unknown.length)throw new Error(`Unknown platform permission reference in ${file}: ${unknown.join(', ')}`);
}


const actionRegistry=JSON.parse(fs.readFileSync(path.join(frontend,'src/authorization-action-registry.json'),'utf8'));
const uniqueIds=items=>{const ids=items.map(item=>item.id);if(new Set(ids).size!==ids.length)throw new Error('Duplicate authorization registry IDs');return ids.sort();};
const routeIds=uniqueIds(actionRegistry.routes);
if(JSON.stringify(routeIds)!==JSON.stringify(Object.keys(registry.frontendRoutes).sort()))throw new Error('Action registry routes diverge from canonical navigation permissions');
const tabIds=uniqueIds(actionRegistry.tabs);
if(JSON.stringify(tabIds)!==JSON.stringify(Object.keys(registry.frontendPlatformPages).sort()))throw new Error('Platform tab registry diverges from canonical platform page permissions');
const menuItems=actionRegistry.menus.flatMap(menu=>menu.items);
if(new Set(menuItems).size!==menuItems.length||JSON.stringify([...menuItems].sort())!==JSON.stringify(routeIds.concat(tabIds).sort()))throw new Error('Every route and platform tab must appear exactly once in a menu registry');
for(const submenu of actionRegistry.submenus)if(!submenu.items.length||submenu.items.some(id=>!routeIds.includes(id)))throw new Error('Unknown/empty submenu '+submenu.id);
const actionIds=uniqueIds(actionRegistry.actions);
const validActors=new Set(['PUBLIC','CUSTOMER','VENDOR','PLATFORM']);
for(const action of actionRegistry.actions){
 if(!validActors.has(action.actor)||!action.component||!action.trigger||!action.method||!action.endpoint||action.backendOperation!==action.method+' '+action.endpoint)throw new Error('Incomplete frontend action mapping '+action.id);
 for(const permission of action.permissions)if(!permissions.has(permission)&&permission!=='POS_CASH_MOVEMENT_DYNAMIC')throw new Error('Unknown action permission '+permission+' in '+action.id);
 if(action.actor==='VENDOR'&&action.permissions.some(p=>p.startsWith('PLATFORM_')))throw new Error('Platform permission assigned to vendor action '+action.id);
 if(!fs.existsSync(path.join(frontend,'src',action.component)))throw new Error('Missing action component '+action.component);
}
async function collectControllerRoutes(directory){
 const found=[];
 for(const entry of fs.readdirSync(directory,{withFileTypes:true})){
  const full=path.join(directory,entry.name);
  if(entry.isDirectory())found.push(...await collectControllerRoutes(full));
  else if(entry.isFile()&&entry.name.endsWith('Controller.java')){
   const source=fs.readFileSync(full,'utf8');
   const classStart=source.indexOf('public class ');
   const classPrefix=classStart<0?source:source.slice(0,classStart);
   const classPolicy=[...classPrefix.matchAll(/@PreAuthorize\s*\(\s*"([^"]*)"/g)].at(-1)?.[1]??'';
   const base=source.match(/@RequestMapping\s*\(\s*["']([^"']*)["']/)?.[1]??'';
   const pattern=/@(Get|Post|Put|Patch|Delete)Mapping\s*(?:\(\s*["']([^"']*)["'])?/g;
   const matches=[...source.matchAll(pattern)];
   for(let i=0;i<matches.length;i++){
    const match=matches[i],suffix=match[2]??'';
    const endpoint=suffix.startsWith('/api/')?suffix:base+suffix;
    const segment=source.slice(match.index,matches[i+1]?.index??source.length);
    const methodPolicy=segment.match(/@PreAuthorize\s*\(\s*"([^"]*)"/)?.[1]??'';
    found.push({method:match[1].toUpperCase(),endpoint:normalizeActionPath(endpoint),policy:methodPolicy||classPolicy});
   }
  }
 }
 return found;
}
function normalizeActionPath(value){return value.replace(/\{[^/]+\}/g,'{}').replace(/\/+/g,'/').replace(/\/$/,'')||'/';}
const backendOperations=await collectControllerRoutes(path.join(repo,'backend/src/main/java'));
for(const action of actionRegistry.actions){
 const operation=backendOperations.find(op=>op.method===action.method&&op.endpoint===normalizeActionPath(action.endpoint));
 if(!operation)throw new Error('No backend operation for frontend action '+action.id+': '+action.backendOperation);
 if(action.actor==='PUBLIC'){
  if(!/permitAll\s*\(\s*\)/.test(operation.policy))throw new Error('Public frontend action is not permitAll on backend: '+action.id);
 }else{
  const actual=[...operation.policy.matchAll(/has(?:Any)?Authority\s*\(([^)]*)\)/g)].flatMap(call=>[...call[1].matchAll(/'([A-Z][A-Z0-9_]+)'/g)].map(m=>m[1]));
  if(action.permissions.includes('POS_CASH_MOVEMENT_DYNAMIC')){
   if(!operation.policy.includes("'POS_' + #request.type().name()"))throw new Error('Dynamic POS cash-movement permission mapping mismatch: '+action.id);
  }else{
   const expected=[...action.permissions].sort(),observed=[...new Set(actual)].sort();
   if(JSON.stringify(expected)!==JSON.stringify(observed))throw new Error('Frontend/backend permission parity mismatch for '+action.id+': frontend='+expected.join(',')+' backend='+observed.join(','));
  }
 }
}

console.log(`Authorization registry verified: ${expectedRoutes.length} frontend routes, ${names.length} backend controllers, ${sourceFiles.length} frontend source files, ${permissions.size} permission identifiers.`);
