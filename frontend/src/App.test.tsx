import {render,screen,fireEvent,waitFor,cleanup} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import App from './App';
import {api} from './api';
vi.mock('./api',async importOriginal=>({...await importOriginal<typeof import('./api')>(),api:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();sessionStorage.clear();});
it('does not request customer identity for guest checkout',async()=>{
 vi.mocked(api).mockResolvedValue([]);render(<App/>);
 expect(screen.queryByLabelText(/email|phone|customer/i)).toBeNull();
 await waitFor(()=>expect(api).toHaveBeenCalledWith('/products'));
});
it('blocks checkout and session close while a cash movement outcome is uncertain',async()=>{
 sessionStorage.setItem('pos-session-v1','session');
 vi.mocked(api).mockImplementation(async(path,body)=>{
  if(path==='/products')return [];
  if(body)throw new Error('Connection lost');
  return {state:'OPEN',openingFloat:50,cashReceived:0,changeGiven:0,cashIn:0,cashOut:0,safeDrops:0,expectedCash:50,movements:[]};
 });
 render(<App/>);
 fireEvent.change(screen.getByLabelText('Movement amount'),{target:{value:'10'}});
 fireEvent.change(screen.getByLabelText('Movement reason'),{target:{value:'Float'}});
 fireEvent.click(screen.getByText('Record cash movement'));
 await screen.findByText(/outcome is uncertain/);
 expect((screen.getByText('Reconcile and close session') as HTMLButtonElement).disabled).toBe(true);
 expect((screen.getByText('Complete cash sale') as HTMLButtonElement).disabled).toBe(true);
 expect((screen.getByText('Retry same cash movement') as HTMLButtonElement).disabled).toBe(false);
});
it('preserves a sale key after an uncertain failure and does not claim completion',async()=>{
 vi.mocked(api).mockImplementation(async(path)=>{if(path==='/products')return [{id:1,name:'Panel',sku:'P1',price:10,tax_rate:0,quantity:2}];if(path==='/sessions')return {id:'session'};throw new Error('Connection lost');});
 render(<App/>);fireEvent.change(screen.getByLabelText('Assigned register'),{target:{value:'1'}});fireEvent.change(screen.getByLabelText('Opening float'),{target:{value:'0'}});fireEvent.click(screen.getByText('Open session'));
 fireEvent.click(await screen.findByText('Add Panel'));fireEvent.change(screen.getByLabelText('Cash received'),{target:{value:'10'}});fireEvent.click(screen.getByText('Complete cash sale'));
 await screen.findByText('Retry same sale');expect(screen.queryByLabelText('Sale receipt')).toBeNull();fireEvent.click(screen.getByText('Retry same sale'));
 await waitFor(()=>expect(vi.mocked(api).mock.calls.filter(c=>c[0]==='/sales')).toHaveLength(2));
 const calls=vi.mocked(api).mock.calls.filter(c=>c[0]==='/sales');expect(calls[0][2]).toBe(calls[1][2]);expect(calls[0][1]).toEqual(calls[1][1]);
});
