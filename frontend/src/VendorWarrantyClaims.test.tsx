import {cleanup,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import VendorWarrantyClaims from './VendorWarrantyClaims';
import {vendorApi,vendorUpload} from './api';
vi.mock('./api',()=>({vendorApi:vi.fn(),vendorUpload:vi.fn()}));
vi.mock('./session',()=>({useSession:()=>({account:{username:'manager@vendor.test',tenantId:7,permissions:['SERVICE_VIEW','SERVICE_ASSIGN','SERVICE_UPDATE','SERVICE_RESOLVE']}})}));
afterEach(()=>{cleanup();vi.resetAllMocks();});
it('loads the tenant claim queue and available service assignees',async()=>{
 const row={id:8,warrantyId:3,orderId:'order-3',productName:'Solar Panel',subject:'Panel fault',description:'Panel stopped producing',status:'SUBMITTED',assignedTo:null,inspectionSummary:null,decision:null,decisionReason:null,resolutionType:null,version:0};
 vi.mocked(vendorApi).mockImplementation(async(path)=>path==='/warranty-claims'?[row]:path==='/service-requests/assignees'?['engineer@vendor.test']:[]);
 render(<VendorWarrantyClaims/>);
 expect(await screen.findByText('Claim #8 · Panel fault')).toBeTruthy();
 expect(vendorApi).toHaveBeenCalledWith('/warranty-claims');
 expect(vendorApi).toHaveBeenCalledWith('/service-requests/assignees');
});
it('shows an empty state for a vendor with no warranty claims',async()=>{
 vi.mocked(vendorApi).mockResolvedValue([]);
 render(<VendorWarrantyClaims/>);
 expect(await screen.findByText('No warranty claims submitted.')).toBeTruthy();
});
