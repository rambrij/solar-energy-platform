import ts from 'typescript';
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

const navigationDefinitions=[...navigationSource.matchAll(/\{\s*id:\s*'([^']+)',\s*label:\s*'[^']+',\s*group:\s*'([^']+)',[\s\S]*?path:\s*'([^']+)'\s*\}/g)].map(m=>({id:m[1],group:m[2],path:m[3]}));
const actualNavigation=new Map(navigationDefinitions.map(item=>[item.id,item]));
for(const route of actionRegistry.routes){
 const actual=actualNavigation.get(route.id);
 if(!actual||actual.path!==route.path||actual.group!==route.group)throw new Error('Menu/submenu route metadata diverges from navigation source: '+route.id);
}

if(JSON.stringify(routeIds)!==JSON.stringify(Object.keys(registry.frontendRoutes).sort()))throw new Error('Action registry routes diverge from canonical navigation permissions');
const tabIds=uniqueIds(actionRegistry.tabs);
if(JSON.stringify(tabIds)!==JSON.stringify(Object.keys(registry.frontendPlatformPages).sort()))throw new Error('Platform tab registry diverges from canonical platform page permissions');
const menuItems=actionRegistry.menus.flatMap(menu=>menu.items);
if(new Set(menuItems).size!==menuItems.length||JSON.stringify([...menuItems].sort())!==JSON.stringify(routeIds.concat(tabIds).sort()))throw new Error('Every route and platform tab must appear exactly once in a menu registry');
for(const menu of actionRegistry.menus){
 for(const id of menu.items){
  const route=actionRegistry.routes.find(item=>item.id===id);
  const tab=actionRegistry.tabs.find(item=>item.id===id);
  const actor=tab?'PLATFORM':route?.path.startsWith('/marketplace/')?'CUSTOMER':route?.path.startsWith('/vendor/')?'VENDOR':null;
  if(!actor||menu.actor!==actor)throw new Error('Menu actor does not match route/tab actor: '+menu.id+' -> '+id);
 }
}
const submenuItems=actionRegistry.submenus.flatMap(item=>item.items);
if(new Set(submenuItems).size!==submenuItems.length||JSON.stringify([...submenuItems].sort())!==JSON.stringify(routeIds))throw new Error('Every workspace route must appear in exactly one submenu');
for(const submenu of actionRegistry.submenus){
 if(!submenu.items.length||submenu.items.some(id=>!routeIds.includes(id)))throw new Error('Unknown/empty submenu '+submenu.id);
 for(const id of submenu.items){
  const route=actionRegistry.routes.find(item=>item.id===id);
  if(!route||route.group!==submenu.label)throw new Error('Submenu group metadata mismatch: '+submenu.id+' -> '+id);
 }
}
for(const tab of actionRegistry.tabs){
 const rule=registry.frontendPlatformPages?.[tab.id];
 const mode=rule?.allOf?'allOf':rule?.anyOf?'anyOf':null;
 const permissions=rule?.[mode]??[];
 if(mode!==tab.mode||JSON.stringify([...permissions].sort())!==JSON.stringify([...tab.permissions].sort()))throw new Error('Platform tab permission mapping mismatch: '+tab.id);
}
const actionIds=uniqueIds(actionRegistry.actions);

const expectedButtonIds=actionRegistry.actions.filter(a=>a.trigger==='button'||a.trigger==='submit').map(a=>a.id).sort();
if(JSON.stringify(uniqueIds(actionRegistry.buttons))!==JSON.stringify(expectedButtonIds))throw new Error('Frontend button/form inventory is incomplete or diverges from action mappings');
for(const button of actionRegistry.buttons){
 const buttonSource=fs.readFileSync(path.join(frontend,'src',button.component),'utf8');
 if(button.trigger==='button'&&!/\bonClick\s*=/.test(buttonSource))throw new Error('Registered button action has no JSX onClick control: '+button.id);
 if(button.trigger==='submit'&&!/\bonSubmit\s*=/.test(buttonSource))throw new Error('Registered submit action has no JSX onSubmit control: '+button.id);
 const action=actionRegistry.actions.find(item=>item.id===button.id);
 if(!action||button.component!==action.component||button.trigger!==action.trigger||button.backendOperation!==action.backendOperation)throw new Error('Button-to-backend-action mapping mismatch: '+button.id);
}

const backendControlHelpers=new Set([
 'api','vendorApi','platformApi','marketplaceApi','authPost','authenticatedFetch','fetch',
 'signIn','switchActorContext','signOut'
]);
const localControlHelpers=new Set([
 'navigateWorkspace','printDocument','assessCash','billEstimate','formatCurrency','formatDate',
 'preventDefault','stopPropagation','writeText','reload','focus','blur','removeItem','setItem','getItem',
 'randomUUID','includes','some','every','find','findIndex','map','filter','reduce','slice','splice',
 'trim','replace','replaceAll','toLowerCase','toUpperCase','toFixed','toString','startsWith','endsWith',
 'max','min','round','floor','ceil','isFinite','isInteger','from','assign','parse','stringify','sort',
 'push','pop','join','split','has','get','set','delete','values','keys','entries',
 'now','fromEntries','pushState','replaceState','back','forward','getElementById',
 'querySelector','querySelectorAll','addEventListener','removeEventListener','scrollIntoView',
 'dispatchEvent','closest','contains','matches','getBoundingClientRect'
]);
function mergeControlClass(a,b){
 const rank={LOCAL_ONLY:0,DELEGATED_HELPER:1,DELEGATED_CALLBACK:2,BACKEND_ACTION:3};
 return rank[a]>=rank[b]?a:b;
}
function inventoryJsxControls(file,source){
 const kind=file.endsWith('.tsx')?ts.ScriptKind.TSX:ts.ScriptKind.TS;
 const unit=ts.createSourceFile(file,source,ts.ScriptTarget.Latest,true,kind);
 const functions=new Map(),imports=new Set();
 function collect(node){
  if(ts.isImportDeclaration(node)&&node.importClause){
   const clause=node.importClause;
   if(clause.name)imports.add(clause.name.text);
   if(clause.namedBindings&&ts.isNamedImports(clause.namedBindings))
    for(const item of clause.namedBindings.elements)imports.add(item.name.text);
   if(clause.namedBindings&&ts.isNamespaceImport(clause.namedBindings))imports.add(clause.namedBindings.name.text);
  }
  if(ts.isFunctionDeclaration(node)&&node.name&&node.body)functions.set(node.name.text,node.body);
  if(ts.isMethodDeclaration(node)&&node.body&&node.name)functions.set(node.name.getText(unit),node.body);
  if(ts.isVariableDeclaration(node)&&ts.isIdentifier(node.name)&&node.initializer){
   // React hook-wrapped callbacks are still local functions. Treating useCallback
   // and useMemo as opaque imports hides their real backend/local behavior.
   const initializer=node.initializer;
   const wrapped=ts.isCallExpression(initializer)
    &&ts.isIdentifier(initializer.expression)
    &&['useCallback','useMemo'].includes(initializer.expression.text)
    ?initializer.arguments[0]:initializer;
   if(wrapped&&(ts.isArrowFunction(wrapped)||ts.isFunctionExpression(wrapped)))
    functions.set(node.name.text,wrapped.body);
  }
  ts.forEachChild(node,collect);
 }
 collect(unit);
 function classifyCall(name,visited){
  if(backendControlHelpers.has(name))return 'BACKEND_ACTION';
  if(name.startsWith('on')&&name.length>2)return 'DELEGATED_CALLBACK';
  if(functions.has(name)){
   if(visited.has(name))return 'DELEGATED_HELPER';
   const next=new Set(visited);next.add(name);
   return classifyNode(functions.get(name),next);
  }
  if(name.startsWith('set')||localControlHelpers.has(name))return 'LOCAL_ONLY';
  if(imports.has(name))return 'DELEGATED_HELPER';
  return 'DELEGATED_HELPER';
 }
 function classifyNode(node,visited=new Set()){
  if(!node)return 'LOCAL_ONLY';
  if(ts.isIdentifier(node)){
   if(node.text.startsWith('on')&&node.text.length>2)return 'DELEGATED_CALLBACK';
   if(functions.has(node.text))return classifyCall(node.text,visited);
   if(backendControlHelpers.has(node.text))return 'BACKEND_ACTION';
   if(imports.has(node.text))return 'DELEGATED_HELPER';
   return 'LOCAL_ONLY';
  }
  let result='LOCAL_ONLY';
  if(ts.isCallExpression(node)){
   let name='';
   if(ts.isIdentifier(node.expression))name=node.expression.text;
   else if(ts.isPropertyAccessExpression(node.expression))name=node.expression.name.text;
   if(name)result=mergeControlClass(result,classifyCall(name,visited));
  }
  ts.forEachChild(node,child=>{result=mergeControlClass(result,classifyNode(child,visited));});
  return result;
 }
 const controls=[];
 function visit(node){
  if(ts.isJsxAttribute(node)&&ts.isIdentifier(node.name)&&/^on[A-Z]/.test(node.name.text)
    &&node.initializer&&ts.isJsxExpression(node.initializer)&&node.initializer.expression){
   const expression=node.initializer.expression;
   const classification=classifyNode(expression);
   controls.push({component:path.basename(file),line:unit.getLineAndCharacterOfPosition(node.getStart(unit)).line+1,event:node.name.text,handler:expression.getText(unit),classification});
  }
  ts.forEachChild(node,visit);
 }
 visit(unit);
 return controls;
}
const frontendControlInventory=sourceFiles.flatMap(file=>inventoryJsxControls(file,fs.readFileSync(file,'utf8')));
if(!frontendControlInventory.length)throw new Error('Frontend JSX control inventory is empty');
const controlSummary=frontendControlInventory.reduce((summary,item)=>{
 summary[item.classification]=(summary[item.classification]??0)+1;return summary;
},{});
function requireControlActionMapping(control,actions){
 const mapped=actions.some(action=>action.component===control.component);
 if(!mapped)throw new Error('Backend-triggering frontend control has no registered backend operation: '+control.component+' '+control.event+' '+control.handler);
 return mapped;
}
assert.throws(()=>requireControlActionMapping({component:'Fixture.tsx',event:'onClick',handler:'save'},[]),
 /Backend-triggering frontend control has no registered backend operation/);
for(const control of frontendControlInventory){
 if(control.classification!=='BACKEND_ACTION')continue;
 const actions=actionRegistry.actions.filter(action=>action.component===control.component);
 requireControlActionMapping(control,actionRegistry.actions);
}

const validActors=new Set(['PUBLIC','SESSION','CUSTOMER','VENDOR','PLATFORM']);
for(const action of actionRegistry.actions){
 if(!validActors.has(action.actor)||!action.component||!action.trigger||!action.method||!action.endpoint||action.backendOperation!==action.method+' '+action.endpoint)throw new Error('Incomplete frontend action mapping '+action.id);
 for(const permission of action.permissions)if(!permissions.has(permission)&&permission!=='POS_CASH_MOVEMENT_DYNAMIC')throw new Error('Unknown action permission '+permission+' in '+action.id);
 if(action.actor==='VENDOR'&&action.permissions.some(p=>p.startsWith('PLATFORM_')))throw new Error('Platform permission assigned to vendor action '+action.id);
 if(!fs.existsSync(path.join(frontend,'src',action.component)))throw new Error('Missing action component '+action.component);
}
function validateActionPermissionParity(action,policy){
 const expected=[...action.permissions].sort();
 if(action.actor==='PUBLIC'){
  if(action.permissions.length)throw new Error('Public frontend action cannot claim protected permissions: '+action.id);
  if(!/permitAll\s*\(\s*\)/.test(policy))throw new Error('Public frontend action is not permitAll on backend: '+action.id);
  return;
 }
 if(action.actor==='SESSION'){
  if(!/isAuthenticated\s*\(\s*\)/.test(policy)||/permitAll\s*\(\s*\)/.test(policy))
   throw new Error('Session frontend action must require an authenticated session: '+action.id);
  if(action.permissions.length)throw new Error('Session-only action cannot claim role permissions: '+action.id);
  return;
 }
 const actual=[...policy.matchAll(/has(?:Any)?Authority\s*\(([^)]*)\)/g)].flatMap(call=>[...call[1].matchAll(/'([A-Z][A-Z0-9_]+)'/g)].map(match=>match[1]));
 if(action.actor==='CUSTOMER'){
  if(JSON.stringify(expected)!==JSON.stringify(['CUSTOMER'])||!actual.includes('CUSTOMER')
    ||actual.some(permission=>permission!=='CUSTOMER'))
   throw new Error('Customer frontend actor does not match backend customer authority: '+action.id);
 }
 if(action.actor==='VENDOR'){
  if(action.permissions.some(permission=>permission==='CUSTOMER'||permission.startsWith('PLATFORM_'))
    ||actual.some(permission=>permission==='CUSTOMER'||permission.startsWith('PLATFORM_')))
   throw new Error('Vendor frontend actor maps to a non-vendor backend authority: '+action.id);
 }
 if(action.actor==='PLATFORM'){
  if(!action.permissions.length||action.permissions.some(permission=>!permission.startsWith('PLATFORM_'))
    ||actual.some(permission=>!permission.startsWith('PLATFORM_')))
   throw new Error('Platform frontend actor must map exclusively to platform backend authorities: '+action.id);
 }
 if(action.permissions.includes('POS_CASH_MOVEMENT_DYNAMIC')){
  if(!policy.includes("'POS_' + #request.type().name()")||policy.includes('PLATFORM_')||policy.includes("'CUSTOMER'"))
   throw new Error('Dynamic POS cash-movement permission mapping mismatch: '+action.id);
 }else{
  const observed=[...new Set(actual)].sort();
  if(JSON.stringify(expected)!==JSON.stringify(observed))throw new Error('Frontend/backend permission parity mismatch for '+action.id+': frontend='+expected.join(',')+' backend='+observed.join(','));
  if(action.permissions.length>1){
   const expectsAny=action.permissionMode==='ANY';
   const backendAny=/hasAnyAuthority\s*\(/.test(policy)||/\sor\s/.test(policy);
   if(expectsAny!==backendAny)throw new Error('Frontend/backend ANY-vs-ALL authorization mode mismatch for '+action.id);
  }
 }
}
assert.throws(()=>validateActionPermissionParity({id:'fixture-actor-mismatch',actor:'VENDOR',permissions:['POS_VIEW']}, "hasAuthority('PLATFORM_TENANT_VIEW')"),
 /Frontend\/backend permission parity mismatch|Platform permission assigned to vendor action|Vendor frontend actor maps to a non-vendor backend authority/);
assert.throws(()=>validateActionPermissionParity({id:'fixture-public-mismatch',actor:'PUBLIC',permissions:[]}, "authenticated()"),
 /Public frontend action is not permitAll/);
assert.throws(()=>validateActionPermissionParity({id:'fixture-vendor-platform-permission',actor:'VENDOR',permissions:['PLATFORM_TENANT_VIEW']}, "hasAuthority('PLATFORM_TENANT_VIEW')"),
 /Platform permission assigned to vendor action|Vendor frontend actor maps to a non-vendor backend authority/);
assert.throws(()=>validateActionPermissionParity({id:'fixture-customer-vendor-mismatch',actor:'CUSTOMER',permissions:['POS_VIEW']}, "hasAuthority('POS_VIEW')"),
 /Customer frontend actor does not match backend customer authority/);
assert.throws(()=>validateActionPermissionParity({id:'fixture-platform-vendor-mismatch',actor:'PLATFORM',permissions:['POS_VIEW']}, "hasAuthority('POS_VIEW')"),
 /Platform frontend actor must map exclusively to platform backend authorities/);
assert.throws(()=>validateActionPermissionParity({id:'fixture-public-permission-mismatch',actor:'PUBLIC',permissions:['POS_VIEW']}, "permitAll()"),
 /Public frontend action cannot claim protected permissions/);
assert.throws(()=>validateActionPermissionParity({id:'fixture-vendor-customer-mismatch',actor:'VENDOR',permissions:['CUSTOMER']}, "hasAuthority('CUSTOMER')"),
 /Vendor frontend actor maps to a non-vendor backend authority/);
assert.throws(()=>validateActionPermissionParity({id:'fixture-session-permit-all',actor:'SESSION',permissions:[]}, "permitAll()"),
 /Session frontend action must require an authenticated session/);

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
function normalizeActionPath(value){
 return value
  .replace(/['"]\s*\+\s*encodeURIComponent\([^)]*\)\s*\+\s*['"]/g,'{}')
  .replace(/\$\{encodeURIComponent\(([^)]+)\)\}/g,'{}')
  .replace(/\{[^/]+\}/g,'{}')
  .replace(/\/+/g,'/')
  .replace(/\/$/,'')||'/';
}
assert.equal(
 normalizeActionPath("/api/v1/platform/identities/'+encodeURIComponent(username)+'/mfa-enrollment"),
 '/api/v1/platform/identities/{}/mfa-enrollment'
);
function requireBackendOperation(action,operations){
 const expected=action.method+' '+action.endpoint;
 if(action.backendOperation!==expected)
  throw new Error('Frontend backendOperation metadata mismatch for '+action.id+': '+action.backendOperation+' expected '+expected);
 const matches=operations.filter(op=>op.method===action.method&&op.endpoint===normalizeActionPath(action.endpoint));
 if(matches.length===0)throw new Error('No backend operation for frontend action '+action.id+': '+action.backendOperation);
 if(matches.length!==1)throw new Error('Ambiguous backend operation for frontend action '+action.id+': '+action.backendOperation);
 return matches[0];
}
assert.throws(()=>requireBackendOperation({id:'fixture-missing-backend-operation',method:'POST',endpoint:'/api/v1/vendor/missing',backendOperation:'POST /api/v1/vendor/missing'},[]),
 /No backend operation for frontend action/);
assert.throws(()=>requireBackendOperation({id:'fixture-wrong-backend-operation',method:'POST',endpoint:'/api/v1/vendor/missing',backendOperation:'GET /api/v1/vendor/missing'},[{method:'POST',endpoint:'/api/v1/vendor/missing'}]),
 /Frontend backendOperation metadata mismatch/);
assert.throws(()=>requireBackendOperation({id:'fixture-ambiguous-backend-operation',method:'POST',endpoint:'/api/v1/vendor/missing',backendOperation:'POST /api/v1/vendor/missing'},[{method:'POST',endpoint:'/api/v1/vendor/missing'},{method:'POST',endpoint:'/api/v1/vendor/missing'}]),
 /Ambiguous backend operation/);
const backendOperations=await collectControllerRoutes(path.join(repo,'backend/src/main/java'));
for(const action of actionRegistry.actions){
 const operation=requireBackendOperation(action,backendOperations);
 validateActionPermissionParity(action,operation.policy);
}


const apiHelperSource=fs.readFileSync(path.join(frontend,'src/api.ts'),'utf8');
const requiredApiHelpers=[
 "export function authPost(path:string,body:unknown){return request('/api/v1/auth'+path,'POST',body);}",
 "export function api(path:string,body?:unknown,key?:string){return request('/api/v1/vendor/pos'+path,body===undefined?'GET':'POST',body,key);}",
 "export function vendorApi(path:string,method='GET',body?:unknown,key?:string){return request('/api/v1/vendor'+path,method,body,key);}",
 "export function platformApi(path:string,method='GET',body?:unknown){return request('/api/v1/platform'+path,method,body);}"
];
for(const helper of requiredApiHelpers)if(!apiHelperSource.includes(helper))
 throw new Error('API helper must preserve its reviewed prefix and method semantics: '+helper);
if(/\bacceptVendorInvitation\b/.test(apiHelperSource))
 throw new Error('Unregistered generic auth API helper remains in api.ts');
const apiHelperExports=[...apiHelperSource.matchAll(/export function\s+([A-Za-z_$][\w$]*)\s*\(/g)].map(match=>match[1]).sort();
if(JSON.stringify(apiHelperExports)!==JSON.stringify(['api','authPost','platformApi','vendorApi']))
 throw new Error('api.ts exports an unreviewed API helper: '+apiHelperExports.join(','));
if(/authApi|\/api\/v1\/auth\/me/.test(apiHelperSource))
 throw new Error('The duplicate /auth/me API helper must not exist outside the canonical session store');
const sessionSource=fs.readFileSync(path.join(frontend,'src/session.ts'),'utf8');
if(!/fetch\s*\(\s*['"]\/api\/v1\/auth\/me['"]/.test(sessionSource)
 || [...sessionSource.matchAll(/fetch\s*\(\s*['"]\/api\/v1\/auth\/me['"]/g)].length!==1)
 throw new Error('Exactly one authoritative GET /api/v1/auth/me reader must live in session.ts');
if(!/fetch\s*\(\s*['"]\/api\/v1\/auth\/csrf['"]/.test(sessionSource)
 || !/fetch\s*\(\s*['"]\/api\/v1\/auth['"]\s*\+\s*path\s*,\s*\{\s*method\s*:\s*['"]POST['"]/.test(sessionSource)
 || [...sessionSource.matchAll(/\bfetch\s*\(/g)].length!==5){
 throw new Error('Session auth transport must use the reviewed CSRF, POST /api/v1/auth and single /auth/me path helpers');
}
const marketplaceHelperSource=fs.readFileSync(path.join(frontend,'src/marketplaceApi.ts'),'utf8');
if(!/authenticatedFetch\(\s*path\s*,\s*\{\s*method/.test(marketplaceHelperSource)
 || !/marketplaceApi\(\s*['"]\/api\/v1\/customer\/csrf['"]\s*\)/.test(marketplaceHelperSource)
 || !/authenticatedFetch\(\s*['"]\/api\/v1\/auth\/csrf['"]/.test(apiHelperSource))
 throw new Error('CSRF transport helpers must use only their reviewed fixed endpoints');
const transportEndpoints=actionRegistry.transportEndpoints??[];
const expectedTransports=['GET /api/v1/auth/csrf','GET /api/v1/customer/csrf'].sort();
const observedTransports=transportEndpoints.map(item=>item.method+' '+item.endpoint).sort();
if(JSON.stringify(observedTransports)!==JSON.stringify(expectedTransports))
 throw new Error('CSRF transport endpoint inventory is incomplete or contains an unreviewed endpoint');
const sessionOperations=actionRegistry.sessionApiOperations??[];
if(!Array.isArray(sessionOperations)||sessionOperations.length===0)throw new Error('Session API operation inventory is required');
const observedSessionPaths=[];
for(const match of sessionSource.matchAll(/\bauthRequest\s*\(([^,]+),/g)){
 const prefix=sessionSource.slice(Math.max(0,match.index-24),match.index);
 if(/function\s*$/.test(prefix))continue;
 const values=[...match[1].matchAll(/['"]([^'"]+)['"]/g)].map(item=>item[1]);
 if(!values.length)throw new Error('Dynamic authRequest path must enumerate literal path variants');
 observedSessionPaths.push(...values);
}
const expectedSessionPaths=sessionOperations.map(item=>item.path).sort();
if(JSON.stringify([...observedSessionPaths].sort())!==JSON.stringify(expectedSessionPaths))
 throw new Error('Session auth call sites diverge from explicit operation inventory: observed='+observedSessionPaths.join(',')+' expected='+expectedSessionPaths.join(','));
function requireSessionAction(item,actions){
 const action=actions.find(value=>value.id===item.action);
 if(!action||action.method!=='POST'||action.endpoint!=='/api/v1/auth'+item.path)
  throw new Error('Session auth path does not map to its exact backend action: '+item.path);
 return action;
}
assert.throws(()=>requireSessionAction({path:'/missing',action:'missing'},[]),
 /Session auth path does not map to its exact backend action/);
for(const item of sessionOperations){
 const action=requireSessionAction(item,actionRegistry.actions);
 const operation=backendOperations.find(value=>value.method===action.method&&value.endpoint===normalizeActionPath(action.endpoint));
 if(!operation)throw new Error('Session auth action has no backend controller mapping: '+item.action);
 validateActionPermissionParity(action,operation.policy);
}
for(const file of sourceFiles){
 const relative=path.relative(path.join(frontend,'src'),file);
 if(['session.ts','api.ts','marketplaceApi.ts'].includes(relative))continue;
 if(/\bfetch\s*\(/.test(fs.readFileSync(file,'utf8')))
  throw new Error('Direct fetch bypasses the registered API helpers in '+relative);
}

function splitCallArguments(source,open){
 const args=[];let start=open+1,depth=0,quote=null,escaped=false;
 for(let i=open+1;i<source.length;i++){
  const ch=source[i];
  if(quote){if(escaped){escaped=false;continue;}if(ch==='\\'){escaped=true;continue;}if(ch===quote)quote=null;continue;}
  if(ch==="'"||ch==='"'||ch===String.fromCharCode(96)){quote=ch;continue;}
  if(ch==='('||ch==='{'||ch==='[')depth++;
  else if(ch===')'||ch==='}'||ch===']'){
   if(ch===')'&&depth===0){args.push(source.slice(start,i).trim());return args;}
   depth--;
  }else if(ch===','&&depth===0){args.push(source.slice(start,i).trim());start=i+1;}
 }
 return args;
}
function literalArgument(value){
 const match=value.match(/^(['"])([\s\S]*)\1$/);
 if(match)return match[2];
 const tick=String.fromCharCode(96);
 if(value.startsWith(tick)&&value.endsWith(tick))return value.slice(1,-1);
 return null;
}
function resolveApiCall(file,source,match){
 const fn=match[1],open=source.indexOf('(',match.index),args=splitCallArguments(source,open);
 const raw=literalArgument(args[0]??'');
 if(raw===null)return {dynamic:true,component:path.basename(file),line:source.slice(0,match.index).split('\n').length,fn};
 if(fn==='api'&&raw==='/csrf')return null;
 if(fn==='marketplaceApi'&&raw.endsWith('/csrf'))return null;
 let method='GET',endpoint=raw;
 if(fn==='api')method=args.length>1&&args[1]&&!/^(?:undefined|null)$/.test(args[1])?'POST':'GET';
 else if(fn==='authPost')method='POST';
 else if(['vendorApi','platformApi','marketplaceApi'].includes(fn)){
  const declared=(args[1]??'').match(/^['"](GET|POST|PUT|PATCH|DELETE)['"]$/);
  method=declared?declared[1]:'GET';
 }
 if(fn==='api'&&!endpoint.startsWith('/api/'))endpoint='/api/v1/vendor/pos'+endpoint;
 if(fn==='vendorApi'&&!endpoint.startsWith('/api/'))endpoint='/api/v1/vendor'+endpoint;
 if(fn==='platformApi'&&!endpoint.startsWith('/api/'))endpoint='/api/v1/platform'+endpoint;
 if(fn==='authPost'&&!endpoint.startsWith('/api/'))endpoint='/api/v1/auth'+endpoint;
 const variants=[];
 const ternary=/\$\{[^{}]+?\?\s*(['"])([^'"]+)\1\s*:\s*(['"])([^'"]+)\3\}/g;
 let found;
 while((found=ternary.exec(endpoint))!==null)variants.push([found[2],found[4]]);
 let endpoints=[endpoint];
 for(const pair of variants){endpoints=endpoints.flatMap(value=>pair.map(replacement=>value.replace(/\$\{[^{}]+?\?\s*(['"])[^'"]+\1\s*:\s*(['"])[^'"]+\2\}/,replacement)));}
 endpoints=endpoints.map(value=>{for(const parameter of actionRegistry.dynamicQuerySuffixes?.[path.basename(file)]??[])value=value.replaceAll('${'+parameter+'}','');return normalizeActionPath(value.replace(/\$\{[^}]+\}/g,'{}').split('?')[0]);});
 return {component:path.basename(file),method,endpoints,fn,line:source.slice(0,match.index).split('\n').length};
}
const observedApiCalls=[];
const dynamicApiCalls=[];
for(const file of sourceFiles){
 const source=fs.readFileSync(file,'utf8');
 const relative=path.relative(path.join(frontend,'src'),file);
 if(['api.ts','marketplaceApi.ts'].includes(relative))continue;
 const callPattern=/\b(api|vendorApi|platformApi|marketplaceApi|authPost)\s*\(/g;
 for(const match of source.matchAll(callPattern)){
  const resolved=resolveApiCall(file,source,match);
  if(!resolved)continue;
  if(resolved.dynamic){dynamicApiCalls.push(resolved);continue;}
  for(const endpoint of resolved.endpoints??[resolved.endpoint])observedApiCalls.push({...resolved,endpoint});
 }
}
function requireRegisteredFrontendOperation(call,actions){
 const mapped=actions.some(action=>action.component===call.component&&action.method===call.method
  &&normalizeActionPath(action.endpoint)===call.endpoint);
 if(!mapped)throw new Error('Unregistered literal frontend API operation at '+call.component+':'+call.line+' '+call.method+' '+call.endpoint);
 return mapped;
}
assert.throws(()=>requireRegisteredFrontendOperation({component:'Fixture.tsx',line:1,method:'POST',endpoint:'/api/v1/vendor/unregistered'},[]),
 /Unregistered literal frontend API operation/);
for(const call of observedApiCalls)requireRegisteredFrontendOperation(call,actionRegistry.actions);
const dynamicCallSites=actionRegistry.dynamicApiCallSites??{};
const observedDynamicCallSites=new Set();
function requireDynamicCallSite(key,declarations){
 if(!declarations[key])throw new Error('Unregistered dynamic frontend API call site: '+key);
 return declarations[key];
}
assert.throws(()=>requireDynamicCallSite('Fixture.tsx:9',{}),/Unregistered dynamic frontend API call site/);
for(const call of dynamicApiCalls){
 const key=call.component+':'+call.line;
 const declaration=requireDynamicCallSite(key,dynamicCallSites);
 if(observedDynamicCallSites.has(key))throw new Error('Duplicate dynamic frontend API call site: '+key);
 observedDynamicCallSites.add(key);
 const open=sourceFiles.map(file=>({file,source:fs.readFileSync(file,'utf8')})).find(item=>path.basename(item.file)===call.component);
 if(!open)throw new Error('Missing dynamic API source '+call.component);
 const match=[...open.source.matchAll(/\b(api|vendorApi|platformApi|marketplaceApi)\s*\(/g)].find(item=>open.source.slice(0,item.index).split('\n').length===call.line);
 if(!match||match[1]!==declaration.fn)throw new Error('Dynamic API call-site function mismatch: '+key);
 const args=splitCallArguments(open.source,open.source.indexOf('(',match.index));
 if(args[0]!==declaration.argument)throw new Error('Dynamic API endpoint expression drift: '+key);
 if(declaration.methodArgument!==undefined&&args[1]!==declaration.methodArgument)throw new Error('Dynamic API method expression drift: '+key);
 if(!Array.isArray(declaration.actions)||declaration.actions.length===0)throw new Error('Dynamic API call site has no explicit action mappings: '+key);
 for(const id of declaration.actions){
  const action=actionRegistry.actions.find(item=>item.id===id);
  if(!action||action.component!==call.component)throw new Error('Dynamic API call-site references an unknown or foreign action: '+key+' -> '+id);
  const endpoint=normalizeActionPath(action.endpoint);
  observedApiCalls.push({component:call.component,method:action.method,endpoint,line:call.line,fn:call.fn});
 }
}
for(const key of Object.keys(dynamicCallSites)){
 if(!observedDynamicCallSites.has(key))throw new Error('Stale dynamic API call-site declaration: '+key);
}


const directHttpCalls=[];
for(const file of sourceFiles){
 const fileName=path.basename(file);
 if(['api.ts','marketplaceApi.ts','session.ts'].includes(fileName))continue;
 const source=fs.readFileSync(file,'utf8');
 const pattern=/\bauthenticatedFetch\s*\(/g;
 for(const match of source.matchAll(pattern)){
  const args=splitCallArguments(source,source.indexOf('(',match.index));
  const raw=literalArgument(args[0]??'');
  if(raw===null)throw new Error('Direct authenticatedFetch endpoint must be a literal/template in '+fileName+':'+source.slice(0,match.index).split('\n').length);
  let variants=[raw];
  const routeValues=actionRegistry.dynamicRouteParameters?.[fileName]??{};
  for(const [parameter,values] of Object.entries(routeValues)){
   variants=variants.flatMap(value=>value.includes('${'+parameter+'}')?values.map(v=>value.replaceAll('${'+parameter+'}',v)):value.includes('+parameter+')?values.map(v=>value.replaceAll('+parameter+',v)):[value]);
  }
  variants=variants.map(value=>value.replace(/\$\{encodeURIComponent\(([^)]+)\)\}/g,'{}').replace(/\$\{[^}]+\}/g,'{}'));
  const options=args[1]??'';
  const method=options.match(/\bmethod\s*:\s*['"](GET|POST|PUT|PATCH|DELETE)['"]/)?.[1]??'GET';
  for(let endpoint of variants){
   endpoint=normalizeActionPath(endpoint.split('?')[0]);
   const mapped=actionRegistry.actions.some(action=>action.implementationFile===fileName&&action.method===method&&normalizeActionPath(action.endpoint)===endpoint);
   if(!mapped)throw new Error('Unregistered direct HTTP operation at '+fileName+':'+source.slice(0,match.index).split('\n').length+' '+method+' '+endpoint);
   directHttpCalls.push({file:fileName,method,endpoint});
  }
 }
}

const controlInventoryPath=path.join(frontend,'authorization-control-inventory.generated.json');
fs.writeFileSync(controlInventoryPath,JSON.stringify({
 schemaVersion:1,
 commit:process.env.GITHUB_SHA??null,
 generatedAt:new Date().toISOString(),
 summary:controlSummary,
 controls:frontendControlInventory
},null,2)+'\n');
console.log(`Frontend controls inventoried: ${frontendControlInventory.length} (${JSON.stringify(controlSummary)}).`);
console.log(`Authorization registry verified: ${expectedRoutes.length} frontend routes, ${names.length} backend controllers, ${sourceFiles.length} frontend source files, ${permissions.size} permission identifiers.`);
