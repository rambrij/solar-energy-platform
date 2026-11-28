import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import VendorServiceVisits from './VendorServiceVisits';
import {vendorApi} from './api';

vi.mock('./api',()=>({vendorApi:vi.fn()}));
vi.mock('./session',()=>({useSession:()=>({account:{username:'manager@vendor.test',tenantId:7,permissions:['SERVICE_VIEW','SERVICE_ASSIGN','SERVICE_UPDATE','SERVICE_RESOLVE']}})}));
afterEach(()=>{cleanup();vi.resetAllMocks();});

it('loads the tenant SLA dashboard and saves a reasoned response/resolution policy',async()=>{
 const policy={caseType:'SERVICE_REQUEST',priority:'HIGH',responseTargetMinutes:null,resolutionTargetMinutes:null,configured:false,version:0};
 vi.mocked(vendorApi).mockImplementation(async(path,method='GET')=>{
  if(path==='/service-visits'||path==='/warranty-claims'||path==='/service-requests'||path==='/service-visits/assignees'||path==='/service-sla')return [];
  if(path==='/service-sla/policies'&&method==='PUT')return {...policy,responseTargetMinutes:60,resolutionTargetMinutes:240,configured:true};
  if(path==='/service-sla/policies')return [policy,{...policy,caseType:'WARRANTY_CLAIM',priority:'NORMAL'}];
  return [];
 });
 render(<VendorServiceVisits/>);
 expect(await screen.findByText('Service level agreements')).toBeTruthy();
 fireEvent.change(screen.getAllByLabelText('Response target minutes')[0],{target:{value:'60'}});
 fireEvent.change(screen.getAllByLabelText('Resolution target minutes')[0],{target:{value:'240'}});
 fireEvent.change(screen.getAllByLabelText('Reason for policy change')[0],{target:{value:'Approved service desk targets'}});
 fireEvent.click(screen.getAllByRole('button',{name:'Save SLA policy'})[0]);
 await waitFor(()=>expect(vendorApi).toHaveBeenCalledWith('/service-sla/policies','PUT',{
  caseType:'SERVICE_REQUEST',priority:'HIGH',responseTargetMinutes:60,resolutionTargetMinutes:240,reason:'Approved service desk targets'
 }));
 expect(await screen.findByText(/SLA policy saved/)).toBeTruthy();
});

it('configures an escalation rule and acknowledges an open escalation',async()=>{
 const open={id:9,slaId:81,caseType:'SERVICE_REQUEST',caseId:21,breachType:'RESPONSE',escalationLevel:1,recipientRoleKey:'SERVICE_MANAGER',status:'OPEN',escalatedAt:'2026-11-28T00:00:00Z',acknowledgedBy:null,acknowledgedAt:null,acknowledgementNote:null};
 vi.mocked(vendorApi).mockImplementation(async(path,method='GET')=>{
  if(path==='/service-visits'||path==='/warranty-claims'||path==='/service-requests'||path==='/service-visits/assignees'||path==='/service-sla'||path==='/service-sla/policies'||path==='/service-sla/escalation-policies')return [];
  if(path==='/service-sla/escalations')return [open];
  if(path==='/service-sla/escalation-policies'&&method==='PUT')return {...open,id:31};
  if(path==='/service-sla/escalations/9/acknowledge'&&method==='POST')return {...open,status:'ACKNOWLEDGED',acknowledgedBy:'manager@vendor.test',acknowledgementNote:'I have taken ownership'};
  return [];
 });
 render(<VendorServiceVisits/>);
 expect(await screen.findByText(/SERVICE REQUEST #21/)).toBeTruthy();
 fireEvent.change(screen.getByLabelText('Acknowledgement note'),{target:{value:'I have taken ownership'}});
 fireEvent.click(screen.getByRole('button',{name:'Acknowledge escalation'}));
 await waitFor(()=>expect(vendorApi).toHaveBeenCalledWith('/service-sla/escalations/9/acknowledge','POST',{note:'I have taken ownership'}));
});
