import {act,cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import ReturnAuditTimeline from './ReturnAuditTimeline';
import {api,ApiError} from './api';
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:vi.fn()}));
const history={returnId:'return-1',state:'INSPECTED',page:0,size:12,totalElements:13,totalPages:2,items:[{id:'e1',actor:'manager',action:'REFUND_COPY',createdAt:'2026-11-13T12:00:00Z'}]};
const props={returnId:'return-1',revision:'INSPECTED',disabled:false};
afterEach(()=>{cleanup();vi.resetAllMocks();});
it('loads only explicitly and distinguishes copy generation from physical delivery',async()=>{
 vi.mocked(api).mockResolvedValue(history);render(<ReturnAuditTimeline {...props}/>);expect(api).not.toHaveBeenCalled();fireEvent.click(screen.getByText('Load return audit'));await screen.findByText('Refund copy generated');expect(api).toHaveBeenCalledWith('/returns/return-1/events?page=0&size=12');expect(screen.getByText(/does not prove physical printing/)).toBeTruthy();expect(screen.getByText('manager')).toBeTruthy();expect(vi.mocked(api).mock.calls.every(call=>call.length===1)).toBe(true);
});
it('pages immutable events and clears the prior page during refresh',async()=>{
 vi.mocked(api).mockResolvedValueOnce(history).mockResolvedValueOnce({...history,page:1,items:[{id:'e2',actor:'alice',action:'REQUESTED',createdAt:'2026-11-12T12:00:00Z'}]});render(<ReturnAuditTimeline {...props}/>);fireEvent.click(screen.getByText('Load return audit'));fireEvent.click(await screen.findByText('Next audit events'));await screen.findByText('Return intake requested');expect(screen.queryByText('Refund copy generated')).toBeNull();expect(api).toHaveBeenLastCalledWith('/returns/return-1/events?page=1&size=12');
});
it('explains the dedicated permission and clears stale events after denial',async()=>{
 vi.mocked(api).mockResolvedValueOnce(history).mockRejectedValueOnce(new ApiError(403));render(<ReturnAuditTimeline {...props}/>);fireEvent.click(screen.getByText('Load return audit'));await screen.findByText('Refund copy generated');fireEvent.click(screen.getByText('Refresh return audit'));await screen.findByRole('alert');expect(screen.getByRole('alert').textContent).toContain('POS_RETURN_AUDIT_VIEW');expect(screen.queryByText('manager')).toBeNull();
});
it('ignores a stale response after another return is selected',async()=>{
 let finish!:(value:typeof history)=>void;vi.mocked(api).mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));const view=render(<ReturnAuditTimeline {...props}/>);fireEvent.click(screen.getByText('Load return audit'));view.rerender(<ReturnAuditTimeline {...props} returnId="return-2"/>);await act(async()=>finish(history));expect(screen.queryByText('manager')).toBeNull();
});
it('invalidates displayed data after a workflow revision and ignores unmounted results',async()=>{
 vi.mocked(api).mockResolvedValueOnce(history);const view=render(<ReturnAuditTimeline {...props}/>);fireEvent.click(screen.getByText('Load return audit'));await screen.findByText('Refund copy generated');view.rerender(<ReturnAuditTimeline {...props} revision="PAID"/>);expect(screen.queryByText('manager')).toBeNull();let finish!:(value:typeof history)=>void;vi.mocked(api).mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));fireEvent.click(screen.getByText('Load return audit'));view.unmount();await act(async()=>finish(history));expect(screen.queryByText('manager')).toBeNull();
});
