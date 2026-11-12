import {render,screen,fireEvent,waitFor,cleanup} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import App from './App';
import {api,ApiError} from './api';
vi.mock('./api',async importOriginal=>({...await importOriginal<typeof import('./api')>(),api:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();sessionStorage.clear();});
it('does not request customer identity for guest checkout',async()=>{
 vi.mocked(api).mockResolvedValue([]);render(<App/>);
 expect(screen.queryByLabelText(/email|phone|customer/i)).toBeNull();
 await waitFor(()=>expect(api).toHaveBeenCalledWith('/products'));
});
it('clears a closed saved session without reopening it and offers authorized sessions',async()=>{
 sessionStorage.setItem('pos-session-v1','closed-shift');
 vi.mocked(api).mockImplementation(async(path)=>{
  if(path==='/products')return [];
  if(path==='/sessions/closed-shift')return {id:'closed-shift',state:'CLOSED'};
  if(path==='/sessions/active')return [{id:'open-shift',state:'OPEN',registerId:2,openingFloat:50}];
  throw new Error('Unexpected request');
 });
 render(<App/>);
 await screen.findByText('Resume register 2');
 expect(sessionStorage.getItem('pos-session-v1')).toBeNull();
 expect(screen.queryByLabelText('Movement amount')).toBeNull();
 expect(vi.mocked(api).mock.calls.some(call=>call[0].endsWith('/resume'))).toBe(false);
});
it('retains unresolved sale details through a failed saved-session check and a denied retry',async()=>{
 const attempt={key:'old-sale-key',body:{sessionId:'session',items:[{productId:1,quantity:1}],cashReceived:'10',invoiceRequested:false}};
 sessionStorage.setItem('pos-session-v1','session');sessionStorage.setItem('pos-attempt-v1',JSON.stringify(attempt));
 let checked=false;
 vi.mocked(api).mockImplementation(async(path)=>{
  if(path==='/products')return [];
  if(path==='/sessions/session'){if(!checked){checked=true;throw new Error('Offline');}return {id:'session',state:'OPEN'};}
  if(path==='/sales')throw new ApiError(403);
  return {state:'OPEN',openingFloat:0,cashReceived:0,changeGiven:0,cashIn:0,cashOut:0,safeDrops:0,expectedCash:0,movements:[]};
 });
 render(<App/>);
 await screen.findByText('Retry session check');
 expect(screen.queryByText('Complete cash sale')).toBeNull();
 expect(JSON.parse(sessionStorage.getItem('pos-attempt-v1')!)).toEqual(attempt);
 fireEvent.click(screen.getByText('Retry session check'));
 fireEvent.click(await screen.findByText('Retry same sale'));
 await screen.findByText(/Retry uses the same sale key/);
 expect(JSON.parse(sessionStorage.getItem('pos-attempt-v1')!)).toEqual(attempt);
 expect(api).toHaveBeenCalledWith('/sales',attempt.body,attempt.key);
});
it('keeps a closed session available only for resolving a saved movement',async()=>{
 sessionStorage.setItem('pos-session-v1','session');
 const attempt={key:'old-movement',body:{type:'CASH_IN',amount:'10',reason:'Float'}};
 sessionStorage.setItem('pos-drawer-attempt-v1:session',JSON.stringify(attempt));
 vi.mocked(api).mockImplementation(async(path)=>{
  if(path==='/products')return [];
  if(path==='/sessions/session')return {id:'session',state:'CLOSED'};
  return {state:'CLOSED',openingFloat:0,cashReceived:0,changeGiven:0,cashIn:0,cashOut:0,safeDrops:0,expectedCash:0,movements:[]};
 });
 render(<App/>);
 await screen.findByText('Retry same cash movement');
 expect((screen.getByText('Choose another session') as HTMLButtonElement).disabled).toBe(true);
 expect((screen.getByText('Complete cash sale') as HTMLButtonElement).disabled).toBe(true);
 expect(JSON.parse(sessionStorage.getItem('pos-drawer-attempt-v1:session')!)).toEqual(attempt);
});
it('blocks checkout and session close while a cash movement outcome is uncertain',async()=>{
 sessionStorage.setItem('pos-session-v1','session');
 vi.mocked(api).mockImplementation(async(path,body)=>{
  if(path==='/products')return [];
  if(body)throw new Error('Connection lost');
  return {state:'OPEN',openingFloat:50,cashReceived:0,changeGiven:0,cashIn:0,cashOut:0,safeDrops:0,expectedCash:50,movements:[]};
 });
 render(<App/>);
 fireEvent.change(await screen.findByLabelText('Movement amount'),{target:{value:'10'}});
 fireEvent.change(screen.getByLabelText('Movement reason'),{target:{value:'Float'}});
 fireEvent.click(screen.getByText('Record cash movement'));
 await screen.findByText(/outcome is uncertain/);
 expect((screen.getByText('Reconcile and close session') as HTMLButtonElement).disabled).toBe(true);
 expect((screen.getByText('Complete cash sale') as HTMLButtonElement).disabled).toBe(true);
 expect((screen.getByText('Retry same cash movement') as HTMLButtonElement).disabled).toBe(false);
});
it('preserves a sale key after an uncertain failure and does not claim completion',async()=>{
 vi.mocked(api).mockImplementation(async(path)=>{if(path==='/products')return [{id:1,name:'Panel',sku:'P1',price:10,tax_rate:0,quantity:2}];if(path==='/sessions/active')return [];if(path==='/sessions')return {id:'session'};throw new Error('Connection lost');});
 render(<App/>);await screen.findByText('No open sessions found for your account.');fireEvent.change(screen.getByLabelText('Assigned register'),{target:{value:'1'}});fireEvent.change(screen.getByLabelText('Opening float'),{target:{value:'0'}});fireEvent.click(screen.getByText('Open session'));
 fireEvent.click(await screen.findByText('Add Panel'));fireEvent.change(screen.getByLabelText('Cash received'),{target:{value:'10'}});fireEvent.click(screen.getByText('Complete cash sale'));
 await screen.findByText('Retry same sale');expect(screen.queryByLabelText('Sale receipt')).toBeNull();fireEvent.click(screen.getByText('Retry same sale'));
 await waitFor(()=>expect(vi.mocked(api).mock.calls.filter(c=>c[0]==='/sales')).toHaveLength(2));
 const calls=vi.mocked(api).mock.calls.filter(c=>c[0]==='/sales');expect(calls[0][2]).toBe(calls[1][2]);expect(calls[0][1]).toEqual(calls[1][1]);
});
