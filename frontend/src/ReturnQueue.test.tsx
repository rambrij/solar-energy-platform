import {cleanup,fireEvent,render,screen} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import ReturnQueue from './ReturnQueue';
import {api} from './api';
vi.mock('./api',()=>({api:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();});
const row={id:'return-1',orderId:'order-1',state:'REQUESTED',createdAt:'2026-11-12T12:00:00Z'};
const result={items:[row],state:'REQUESTED',from:'2026-10-15',to:'2026-11-13',reference:'',page:0,size:12,totalElements:25,totalPages:3};
const props={disabled:false,revision:'',onSelect:vi.fn()};
it('loads explicitly, pages using resolved UTC dates and selects only a reference',async()=>{
 vi.mocked(api).mockResolvedValueOnce(result).mockResolvedValueOnce({...result,page:1,items:[{...row,id:'return-13'}]});const select=vi.fn();render(<ReturnQueue {...props} onSelect={select}/>);expect(api).not.toHaveBeenCalled();fireEvent.click(screen.getByText('Load return queue'));fireEvent.click(await screen.findByText('Next returns'));await screen.findByText('Open return return-13');expect(api).toHaveBeenLastCalledWith('/returns/queue?state=REQUESTED&reference=&page=1&size=12&from=2026-10-15&to=2026-11-13');fireEvent.click(screen.getByText('Open return return-13'));expect(select).toHaveBeenCalledWith('return-13');expect(vi.mocked(api).mock.calls.every(call=>call.length===1)).toBe(true);
});
it('clears old results on filter changes and errors instead of showing a stale queue',async()=>{
 vi.mocked(api).mockResolvedValueOnce(result).mockRejectedValueOnce(new Error('denied'));render(<ReturnQueue {...props}/>);fireEvent.click(screen.getByText('Load return queue'));await screen.findByText('Open return return-1');fireEvent.change(screen.getByLabelText('Return queue state'),{target:{value:'ALL'}});expect(screen.queryByText('Open return return-1')).toBeNull();fireEvent.click(screen.getByText('Load return queue'));await screen.findByRole('alert');expect(screen.queryByText('Next returns')).toBeNull();
});
it('ignores late responses after revision, disabling or unmount',async()=>{
 let resolve!:(v:typeof result)=>void;vi.mocked(api).mockImplementation(()=>new Promise(r=>{resolve=r;}));const view=render(<ReturnQueue {...props}/>);fireEvent.click(screen.getByText('Load return queue'));view.rerender(<ReturnQueue {...props} revision="reviewed"/>);resolve(result);await Promise.resolve();expect(screen.queryByText('Open return return-1')).toBeNull();fireEvent.click(screen.getByText('Load return queue'));view.rerender(<ReturnQueue {...props} disabled revision="reviewed"/>);resolve(result);await Promise.resolve();expect(screen.queryByText('Open return return-1')).toBeNull();view.rerender(<ReturnQueue {...props}/>);fireEvent.click(screen.getByText('Load return queue'));view.unmount();resolve(result);await Promise.resolve();expect(props.onSelect).not.toHaveBeenCalled();
});
it('rejects reversed dates without a request and renders empty pages',async()=>{
 render(<ReturnQueue {...props}/>);fireEvent.change(screen.getByLabelText('Queue from date (UTC)'),{target:{value:'2026-11-14'}});fireEvent.change(screen.getByLabelText('Queue to date (UTC)'),{target:{value:'2026-11-13'}});fireEvent.click(screen.getByText('Load return queue'));expect(api).not.toHaveBeenCalled();expect(screen.getByRole('alert').textContent).toContain('From date');fireEvent.change(screen.getByLabelText('Queue from date (UTC)'),{target:{value:'2026-11-01'}});vi.mocked(api).mockResolvedValue({...result,items:[],totalElements:0,totalPages:0});fireEvent.click(screen.getByText('Load return queue'));await screen.findByText(/No returns on this page/);
});
