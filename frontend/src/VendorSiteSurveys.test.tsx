import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import VendorSiteSurveys from './VendorSiteSurveys';
import {vendorApi} from './api';
import {usePermissions} from './usePermissions';

vi.mock('./api',()=>({vendorApi:vi.fn()}));
vi.mock('./usePermissions',()=>({usePermissions:vi.fn()}));
function grant(...grants:string[]){
 vi.mocked(usePermissions).mockReturnValue({
  hasPermission:permission=>grants.includes(permission),
  permissions:{username:'sales@vendor.test'},
 } as ReturnType<typeof usePermissions>);
}
beforeEach(()=>grant('LEAD_VIEW','LEAD_UPDATE'));
afterEach(()=>{cleanup();vi.resetAllMocks();});

it('loads tenant leads and surveys and schedules a survey against the selected lead',async()=>{
 vi.mocked(vendorApi).mockImplementation(async(path,method='GET')=>{
  if(path==='/leads')return [{id:21,status:'QUALIFIED',contactName:'Customer A',productName:'5 kW package',assignedTo:'sales@vendor.test'}] as never;
  if(path==='/site-surveys'&&method==='GET')return [] as never;
  if(path==='/leads/21/site-surveys'&&method==='POST')return {id:9,status:'SCHEDULED'} as never;
  throw new Error('Unexpected request '+path);
 });
 render(<VendorSiteSurveys/>);
 await screen.findByRole('option',{name:/Customer A/});
 fireEvent.change(screen.getByLabelText('Lead'),{target:{value:'21'}});
 fireEvent.change(screen.getByLabelText('Site address'),{target:{value:'12 Solar Park'}});
 fireEvent.change(screen.getByLabelText('Scheduled date and time'),{target:{value:'2026-12-01T10:30'}});
 fireEvent.change(screen.getByLabelText('Reason'),{target:{value:'Customer requested a survey'}});
 fireEvent.click(screen.getByText('Schedule survey'));
 await screen.findByText('Site survey scheduled.');
 await waitFor(()=>expect(vendorApi).toHaveBeenCalledWith('/leads/21/site-surveys','POST',expect.objectContaining({
  siteAddress:'12 Solar Park',reason:'Customer requested a survey'
 })));
});

it('shows an API denial without claiming that a survey was created',async()=>{
 vi.mocked(vendorApi).mockImplementation(async(path)=>{
  if(path==='/leads')return [] as never;
  if(path==='/site-surveys')throw new Error('Missing LEAD_VIEW');
  throw new Error('Unexpected request '+path);
 });
 render(<VendorSiteSurveys/>);
 expect((await screen.findByRole('alert')).textContent).toContain('Missing LEAD_VIEW');
 expect(screen.queryByText('Site survey scheduled.')).toBeNull();
});


it('shows scheduled surveys without loading write options or rendering mutation controls for LEAD_VIEW',async()=>{
 grant('LEAD_VIEW');
 vi.mocked(vendorApi).mockResolvedValue([{
  id:9,enquiryId:21,siteAddress:'12 Solar Park',scheduledAt:'2026-12-01T10:30:00Z',
  status:'SCHEDULED',assignedTo:'sales@vendor.test',version:2,
 }]);
 render(<VendorSiteSurveys/>);
 await screen.findByText('12 Solar Park');
 for(const label of ['Schedule survey','Mark completed','Cancel survey']){
  expect(screen.queryByText(label)).toBeNull();
 }
 expect(vendorApi).toHaveBeenCalledTimes(1);
 expect(vendorApi).toHaveBeenCalledWith('/site-surveys');
});

it('requires completion findings and submits the current survey version',async()=>{
 vi.mocked(vendorApi).mockImplementation(async path=>{
  if(path==='/leads')return [];
  if(path==='/site-surveys')return [{
   id:9,enquiryId:21,siteAddress:'12 Solar Park',scheduledAt:'2026-12-01T10:30:00Z',
   status:'SCHEDULED',assignedTo:'sales@vendor.test',version:2,
  }];
  if(path==='/site-surveys/9')return {};
  throw new Error('Unexpected request '+path);
 });
 render(<VendorSiteSurveys/>);
 const complete=await screen.findByRole('button',{name:'Mark completed'});
 expect((complete as HTMLButtonElement).disabled).toBe(true);
 fireEvent.change(screen.getByLabelText('Findings'),{target:{value:'Roof suitable'}});
 fireEvent.click(complete);
 await screen.findByText('Survey marked complete.');
 expect(vendorApi).toHaveBeenCalledWith('/site-surveys/9','PATCH',{
  status:'COMPLETED',expectedVersion:2,findings:'Roof suitable',reason:'Site survey completed'
 });
});
