vi.mock('./fileDownload',()=>({downloadFile:vi.fn()}));
import {act,cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import ReportExport from './ReportExport';
import {requestReportExport} from './reportExportApi';
import {downloadFile} from './fileDownload';
vi.mock('./reportExportApi',()=>({requestReportExport:vi.fn()}));
const value={blob:new Blob(['csv']),filename:'sales.csv',reference:'11111111-1111-4111-8111-111111111111'};
const props={kind:'sales' as const,from:'2026-11-12',to:'2026-11-13',channel:'ALL',disabled:false};
afterEach(()=>{cleanup();vi.resetAllMocks();});
function submit(){fireEvent.change(screen.getByLabelText('Sales export reason'),{target:{value:'Branch review'}});fireEvent.click(screen.getByText('Download sales CSV'));}
it('requires an explicit reason and exports only the loaded server report period',async()=>{
 vi.mocked(requestReportExport).mockResolvedValue(value);render(<ReportExport {...props}/>);expect(requestReportExport).not.toHaveBeenCalled();expect((screen.getByText('Download sales CSV') as HTMLButtonElement).disabled).toBe(true);submit();await screen.findByText(/CSV prepared/);expect(requestReportExport).toHaveBeenCalledWith('sales',{from:props.from,to:props.to,channel:'ALL',reason:'Branch review'});expect(downloadFile).toHaveBeenCalledWith(value);
});
it('does not send a sales channel in the cash-refund export',async()=>{
 vi.mocked(requestReportExport).mockResolvedValue(value);render(<ReportExport kind="cash-refunds" from={props.from} to={props.to} disabled={false}/>);fireEvent.change(screen.getByLabelText('Cash refund export reason'),{target:{value:'Payout review'}});fireEvent.click(screen.getByText('Download cash refund CSV'));await screen.findByText(/CSV prepared/);expect(requestReportExport).toHaveBeenCalledWith('cash-refunds',{from:props.from,to:props.to,reason:'Payout review'});
});
it('shows permission/network failure without downloading or discarding the reason',async()=>{
 vi.mocked(requestReportExport).mockRejectedValue(new Error('403'));render(<ReportExport {...props}/>);submit();await screen.findByRole('alert');expect(downloadFile).not.toHaveBeenCalled();expect((screen.getByLabelText('Sales export reason') as HTMLInputElement).value).toBe('Branch review');expect(screen.queryByText(/CSV prepared/)).toBeNull();
});
it('ignores a late export when the loaded period changes',async()=>{
 let finish!:(v:typeof value)=>void;vi.mocked(requestReportExport).mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));const view=render(<ReportExport {...props}/>);submit();view.rerender(<ReportExport {...props} from="2026-11-10"/>);await act(async()=>finish(value));expect(downloadFile).not.toHaveBeenCalled();expect(screen.queryByText(/CSV prepared/)).toBeNull();
});
it('disables duplicate export while pending and ignores completion after unmount',async()=>{
 let finish!:(v:typeof value)=>void;vi.mocked(requestReportExport).mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));const view=render(<ReportExport {...props}/>);submit();expect((screen.getByText('Preparing CSV...') as HTMLButtonElement).disabled).toBe(true);view.unmount();await act(async()=>finish(value));expect(downloadFile).not.toHaveBeenCalled();
});
