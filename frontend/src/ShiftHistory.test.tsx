import {cleanup,fireEvent,render,screen} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import ShiftHistory from './ShiftHistory';
import {api} from './api';
vi.mock('./api',()=>({api:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();sessionStorage.clear();});
const row={id:'shift-1',registerId:9,openingFloat:50,expectedCash:110,countedCash:109,variance:-1,closedAt:'2026-11-12T12:00:00Z'};
const result={branchId:7,items:[row],from:'2026-10-15',to:'2026-11-13',reference:'',page:0,size:12,totalElements:25,totalPages:3};
it('loads explicitly, shows closing snapshots and pages with resolved UTC dates',async()=>{
 vi.mocked(api).mockResolvedValueOnce(result).mockResolvedValueOnce({...result,page:1,items:[{...row,id:'shift-13'}]});render(<ShiftHistory/>);expect(api).not.toHaveBeenCalled();fireEvent.click(screen.getByText('Load my closed shifts'));await screen.findByText('shift-1');expect(screen.getByText('109.00')).toBeTruthy();expect(screen.getByText('-1.00')).toBeTruthy();expect(screen.getByText(/Closed with a recorded difference/)).toBeTruthy();fireEvent.click(screen.getByText('Next closed shifts'));await screen.findByText('shift-13');expect(api).toHaveBeenLastCalledWith('/sessions/history?reference=&page=1&size=12&from=2026-10-15&to=2026-11-13');
});
it('changes filters without showing stale rows and clears results on a denied refresh',async()=>{
 vi.mocked(api).mockResolvedValueOnce(result).mockRejectedValueOnce(new Error('denied'));render(<ShiftHistory/>);fireEvent.click(screen.getByText('Load my closed shifts'));await screen.findByText('shift-1');fireEvent.change(screen.getByLabelText('Closed shift reference'),{target:{value:'other'}});expect(screen.queryByText('shift-1')).toBeNull();fireEvent.click(screen.getByText('Load my closed shifts'));await screen.findByRole('alert');expect(screen.queryByText('Next closed shifts')).toBeNull();
});
it('keeps uncertain checkout and shift-close attempts untouched by a history read',async()=>{
 const attempts={'pos-close-attempt-v1:shift-1':'{"requestId":"saved-count"}','pos-attempt-v1':'{"key":"saved-sale"}'};Object.entries(attempts).forEach(([key,value])=>sessionStorage.setItem(key,value));vi.mocked(api).mockResolvedValue(result);render(<ShiftHistory/>);fireEvent.click(screen.getByText('Load my closed shifts'));await screen.findByText('shift-1');Object.entries(attempts).forEach(([key,value])=>expect(sessionStorage.getItem(key)).toBe(value));expect(vi.mocked(api).mock.calls.every(call=>call.length===1)).toBe(true);expect(screen.queryByRole('button',{name:/resume|approve|reopen/i})).toBeNull();
});
it('ignores late responses on workflow revision, disable and unmount',async()=>{
 let resolve!:(value:typeof result)=>void;vi.mocked(api).mockImplementation(()=>new Promise(r=>{resolve=r;}));const view=render(<ShiftHistory/>);fireEvent.click(screen.getByText('Load my closed shifts'));view.rerender(<ShiftHistory revision="closed"/>);resolve(result);await Promise.resolve();expect(screen.queryByText('shift-1')).toBeNull();fireEvent.click(screen.getByText('Load my closed shifts'));view.rerender(<ShiftHistory revision="closed" disabled/>);resolve(result);await Promise.resolve();expect(screen.queryByText('shift-1')).toBeNull();view.rerender(<ShiftHistory/>);fireEvent.click(screen.getByText('Load my closed shifts'));view.unmount();resolve(result);await Promise.resolve();
});
it('validates date order locally and distinguishes missing historical values from zero',async()=>{
 render(<ShiftHistory/>);fireEvent.change(screen.getByLabelText('Closed from date (UTC)'),{target:{value:'2026-11-14'}});fireEvent.change(screen.getByLabelText('Closed to date (UTC)'),{target:{value:'2026-11-13'}});fireEvent.click(screen.getByText('Load my closed shifts'));expect(api).not.toHaveBeenCalled();expect(screen.getByRole('alert').textContent).toContain('From date');fireEvent.change(screen.getByLabelText('Closed from date (UTC)'),{target:{value:'2026-11-01'}});vi.mocked(api).mockResolvedValue({...result,items:[{...row,variance:null,countedCash:null}]});fireEvent.click(screen.getByText('Load my closed shifts'));await screen.findByText('Historical variance was not recorded.');expect(screen.getAllByText('Not recorded')).toHaveLength(2);
});
it('renders empty history without suggesting a new financial command',async()=>{
 vi.mocked(api).mockResolvedValue({...result,items:[],totalElements:0,totalPages:0});render(<ShiftHistory/>);fireEvent.click(screen.getByText('Load my closed shifts'));await screen.findByText(/No closed shifts on this page/);expect((screen.getByText('Next closed shifts') as HTMLButtonElement).disabled).toBe(true);
});
