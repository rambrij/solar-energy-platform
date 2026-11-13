import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import DraftReferenceRecovery from './DraftReferenceRecovery';
import {api,ApiError} from './api';
import {marketplaceApi} from './marketplaceApi';
import type {Draft} from './draftTypes';
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:vi.fn()}));
vi.mock('./marketplaceApi',()=>({marketplaceApi:vi.fn()}));
const draft:Draft={id:'old-draft',vendorName:'Vendor',channel:'ONLINE',status:'DRAFT',expiresAt:'2099-01-01T00:00:00Z',total:110,items:[],message:'Unpaid'};
afterEach(()=>{cleanup();vi.resetAllMocks();});
it('requests an owned marketplace reference only on explicit action and performs no payment',async()=>{
 const onRecovered=vi.fn();vi.mocked(marketplaceApi).mockResolvedValue(draft);render(<DraftReferenceRecovery mode="online" disabled={false} onRecovered={onRecovered}/>);expect(marketplaceApi).not.toHaveBeenCalled();fireEvent.change(screen.getByLabelText('Reservation reference'),{target:{value:'old-draft'}});fireEvent.click(screen.getByText('Recover reservation by reference'));await waitFor(()=>expect(onRecovered).toHaveBeenCalledWith(draft));expect(marketplaceApi).toHaveBeenCalledWith('/api/customer/checkout/drafts/old-draft');expect(api).not.toHaveBeenCalled();
});
it('uses the required saved-command reference for POS recovery',async()=>{
 const onRecovered=vi.fn();vi.mocked(api).mockResolvedValue({...draft,channel:'IN_STORE'});render(<DraftReferenceRecovery mode="pos" requiredId="saved-id" disabled={false} onRecovered={onRecovered}/>);expect((screen.getByLabelText('Reservation reference') as HTMLInputElement).readOnly).toBe(true);fireEvent.click(screen.getByText('Recover reservation by reference'));await waitFor(()=>expect(api).toHaveBeenCalledWith('/drafts/saved-id'));expect(onRecovered).toHaveBeenCalled();
});
it('keeps saved recovery private on denied lookup without claiming any financial outcome',async()=>{
 vi.mocked(api).mockRejectedValue(new ApiError(404));const onRecovered=vi.fn();render(<DraftReferenceRecovery mode="pos" requiredId="saved-id" disabled={false} onRecovered={onRecovered}/>);fireEvent.click(screen.getByText('Recover reservation by reference'));await screen.findByRole('alert');expect(screen.getByRole('alert').textContent).toContain('Saved retry details have been kept');expect(onRecovered).not.toHaveBeenCalled();
});
it('ignores a late lookup when a financial operation disables the form',async()=>{
 let finish!:(value:Draft)=>void;vi.mocked(api).mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));const onRecovered=vi.fn();const view=render(<DraftReferenceRecovery mode="pos" requiredId="saved-id" disabled={false} onRecovered={onRecovered}/>);fireEvent.click(screen.getByText('Recover reservation by reference'));view.rerender(<DraftReferenceRecovery mode="pos" requiredId="saved-id" disabled onRecovered={onRecovered}/>);finish(draft);await waitFor(()=>expect(onRecovered).not.toHaveBeenCalled());
});
it('ignores late results after unmount',async()=>{
 let finish!:(value:Draft)=>void;vi.mocked(api).mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));const onRecovered=vi.fn();const view=render(<DraftReferenceRecovery mode="pos" requiredId="saved-id" disabled={false} onRecovered={onRecovered}/>);fireEvent.click(screen.getByText('Recover reservation by reference'));view.unmount();finish(draft);await waitFor(()=>expect(onRecovered).not.toHaveBeenCalled());
});
