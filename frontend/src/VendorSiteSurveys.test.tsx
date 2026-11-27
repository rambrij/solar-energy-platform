import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import VendorSiteSurveys from './VendorSiteSurveys';
import {vendorApi} from './api';

vi.mock('./api',()=>({vendorApi:vi.fn()}));
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
  if(path==='/site-surveys')throw new Error('Missing SITE_SURVEY_VIEW');
  throw new Error('Unexpected request '+path);
 });
 render(<VendorSiteSurveys/>);
 expect(await screen.findByRole('alert')).toHaveTextContent('Missing SITE_SURVEY_VIEW');
 expect(screen.queryByText('Site survey scheduled.')).toBeNull();
});
