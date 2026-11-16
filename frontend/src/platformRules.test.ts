import {describe,expect,it} from 'vitest';
import {configProblem,money,permissionForTarget,slugProblem,subscriptionTransitions,supportProblem,tenantTransitions,usernameProblem} from './platformRules';
describe('platform rules',()=>{
 it('mirrors the tenant lifecycle and keeps CLOSED terminal',()=>{
  expect(tenantTransitions.PENDING).toEqual(['UNDER_REVIEW','CLOSED']);expect(tenantTransitions.UNDER_REVIEW).toEqual(['ACTIVE','PENDING','CLOSED']);expect(tenantTransitions.ACTIVE).toEqual(['SUSPENDED','CLOSED']);expect(tenantTransitions.SUSPENDED).toEqual(['ACTIVE','CLOSED']);expect(tenantTransitions.CLOSED).toEqual([]);
 });
 it('maps targets to the permission the server requires',()=>{
  expect(permissionForTarget('SUSPENDED')).toBe('PLATFORM_TENANT_SUSPEND');expect(permissionForTarget('CLOSED')).toBe('PLATFORM_TENANT_CLOSE');
  for(const target of ['ACTIVE','UNDER_REVIEW','PENDING'] as const)expect(permissionForTarget(target)).toBe('PLATFORM_TENANT_UPDATE');
 });
 it('mirrors the subscription state machine',()=>{
  expect(subscriptionTransitions.PENDING).toEqual(['ACTIVE','PAST_DUE']);expect(subscriptionTransitions.GRACE_PERIOD).toEqual(['ACTIVE','SUSPENDED']);expect(subscriptionTransitions.CANCELLED).toEqual([]);expect(subscriptionTransitions.EXPIRED).toEqual([]);
 });
 it('validates slugs, usernames, secret references and support requests',()=>{
  expect(slugProblem('pa-store')).toBeNull();for(const bad of ['','a','Bad Slug','admin','-x1','UPPER'])expect(slugProblem(bad)).not.toBeNull();
  expect(usernameProblem('store-admin')).toBeNull();expect(usernameProblem('X Y')).not.toBeNull();
  expect(configProblem(true,'env:GATEWAY_KEY')).toBeNull();expect(configProblem(true,'vault:platform/key')).toBeNull();expect(configProblem(true,'sk_live_123')).not.toBeNull();expect(configProblem(false,'billing@example.com')).toBeNull();expect(configProblem(false,' ')).not.toBeNull();
  expect(supportProblem('Investigating a failed receipt','SUP-1',30)).toBeNull();expect(supportProblem('short','SUP-1',30)).not.toBeNull();expect(supportProblem('Investigating a failed receipt','a b',30)).not.toBeNull();expect(supportProblem('Investigating a failed receipt','SUP-1',241)).not.toBeNull();expect(supportProblem('Investigating a failed receipt','SUP-1',4)).not.toBeNull();expect(supportProblem('Investigating a failed receipt','SUP-1',30.5)).not.toBeNull();
 });
 it('formats money',()=>{expect(money('1180',"INR")).toBe('INR 1180.00');});
});
