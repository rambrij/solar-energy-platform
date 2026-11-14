import {render,screen,fireEvent,waitFor,cleanup} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import App from './App';
import {api,ApiError} from './api';
vi.mock('./api',async importOriginal=>({...await importOriginal<typeof import('./api')>(),api:vi.fn()}));
function mockBackend(implementation:typeof api){
 vi.mocked(api).mockImplementation(async(path,body,key)=>{
  if(path.startsWith('/registers?'))return {branchId:1,page:0,size:12,totalElements:1,totalPages:1,items:[{id:1,available:true}]};
  if(path.startsWith('/catalogue?')){
   const products=await implementation('/products');const params=new URL(path,'http://localhost').searchParams;
   const items=(products as {id:number;name:string;sku:string;barcode?:string;quantity:number}[]).filter(p=>`${p.name} ${p.sku} ${p.barcode??''}`.toLowerCase().includes((params.get('q')??'').toLowerCase()));
   const page=Number(params.get('page')??0),size=24;return {items:items.slice(page*size,(page+1)*size),page,size,totalElements:items.length,totalPages:Math.ceil(items.length/size),catalogueTotal:products.length,categories:[]};
  }
  if(path.startsWith('/catalogue/scan?')){const code=new URL(path,'http://localhost').searchParams.get('code')!.toLowerCase();const products=await implementation('/products');const matches=products.filter((p:{sku:string;barcode?:string})=>[p.sku,p.barcode??''].some(v=>v.toLowerCase()===code));if(matches.length!==1)throw new ApiError(matches.length?409:404);return matches[0];}
  return implementation(path,body,key);
 });
}

afterEach(()=>{cleanup();vi.resetAllMocks();vi.restoreAllMocks();sessionStorage.clear();});
it('does not request customer identity for guest checkout',async()=>{
 vi.mocked(api).mockResolvedValue([]);render(<App/>);
 expect(screen.queryByLabelText(/email|phone|customer/i)).toBeNull();
 await waitFor(()=>expect(api).toHaveBeenCalledWith('/sessions/active'));
 expect(vi.mocked(api).mock.calls.some(([path])=>path==='/products')).toBe(false);
});
it('clears a closed saved session without reopening it and offers authorized sessions',async()=>{
 sessionStorage.setItem('pos-session-v1','closed-shift');
 mockBackend(async(path)=>{
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
 mockBackend(async(path)=>{
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
 mockBackend(async(path)=>{
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
 mockBackend(async(path,body)=>{
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
it('shows a closing drawer as frozen until supervisor review',async()=>{
 sessionStorage.setItem('pos-session-v1','session');
 mockBackend(async(path)=>{
  if(path==='/products')return [{id:1,name:'Panel',sku:'P1',price:10,tax_rate:0,quantity:2}];
  if(path==='/sessions/session')return {state:'CLOSING'};
  if(path.endsWith('/close-status'))return {state:'CLOSING',expectedCash:50,countedCash:45,variance:-5,varianceReason:'Shortage'};
  return {state:'CLOSING',openingFloat:50,cashReceived:0,changeGiven:0,cashIn:0,cashOut:0,safeDrops:0,expectedCash:50,movements:[]};
 });
 render(<App/>);await screen.findByText(/Awaiting a different supervisor/);
 expect((await screen.findByText('Add Panel') as HTMLButtonElement).disabled).toBe(true);
 expect((screen.getByText('Complete cash sale') as HTMLButtonElement).disabled).toBe(true);
 expect((screen.getByText('Record cash movement') as HTMLButtonElement).disabled).toBe(true);
 expect(screen.queryByText('Reconcile and close session')).toBeNull();
});
it('preserves a sale key after an uncertain failure and does not claim completion',async()=>{
 mockBackend(async(path)=>{if(path==='/products')return [{id:1,name:'Panel',sku:'P1',price:10,tax_rate:0,quantity:2}];if(path==='/sessions/active')return [];if(path==='/sessions')return {id:'session'};if(path.startsWith('/drafts'))return [];throw new Error('Connection lost');});
 render(<App/>);await screen.findByText('No open sessions found for your account.');fireEvent.click(screen.getByText('Load assigned registers'));await screen.findByRole('option',{name:'Register 1 · Available'});fireEvent.change(screen.getByLabelText('Assigned register'),{target:{value:'1'}});fireEvent.change(screen.getByLabelText('Opening float'),{target:{value:'0'}});fireEvent.click(screen.getByText('Open session'));
 fireEvent.click(await screen.findByText('Add Panel'));fireEvent.change(screen.getByLabelText('Cash received'),{target:{value:'10'}});fireEvent.click(screen.getByText('Complete cash sale'));
 await screen.findByText('Retry same sale');expect(screen.queryByLabelText('Sale receipt')).toBeNull();fireEvent.click(screen.getByText('Retry same sale'));
 await waitFor(()=>expect(vi.mocked(api).mock.calls.filter(c=>c[0]==='/sales')).toHaveLength(2));
 const calls=vi.mocked(api).mock.calls.filter(c=>c[0]==='/sales');expect(calls[0][2]).toBe(calls[1][2]);expect(calls[0][1]).toEqual(calls[1][1]);
});

function retailMock() {
 sessionStorage.setItem('pos-session-v1', 'session');
 const products = [{ id: 1, name: 'Panel', sku: 'P1', barcode: '123', price: 90, list_price: 100, tax_rate: 10, quantity: 2 }];
 mockBackend(async path => {
  if (path === '/products') return products;
  if (path.startsWith('/drafts')) return [];
  if (path === '/sessions/session') return { state: 'OPEN' };
  return { state: 'OPEN', openingFloat: 50, cashReceived: 0, changeGiven: 0, cashIn: 0, cashOut: 0, safeDrops: 0, expectedCash: 50, movements: [] };
 });
}
it('adds exact barcode and SKU matches quickly, caps quantities and keeps scanner focus', async () => {
 retailMock(); render(<App/>); const search = await screen.findByLabelText('Search name, SKU or scan barcode');
 fireEvent.change(search, { target: { value: '123' } }); fireEvent.submit(search.closest('form')!); await screen.findByText('Added Panel');
 expect((screen.getByLabelText('Quantity 1') as HTMLInputElement).value).toBe('1'); expect(document.activeElement).toBe(search); expect((search as HTMLInputElement).value).toBe('');
 fireEvent.change(search, { target: { value: 'P1' } }); fireEvent.submit(search.closest('form')!); await waitFor(()=>expect((screen.getByLabelText('Quantity 1') as HTMLInputElement).value).toBe('2'));
 fireEvent.change(search, { target: { value: '123' } }); fireEvent.submit(search.closest('form')!); await screen.findByText('Available stock or quantity limit reached.'); expect((screen.getByLabelText('Quantity 1') as HTMLInputElement).value).toBe('2');
 expect(vi.mocked(api).mock.calls.some(call => call[0] === '/sales')).toBe(false);
});
it('does not add a partial barcode match and blocks invalid bill quantities', async () => {
 retailMock(); render(<App/>); const search = await screen.findByLabelText('Search name, SKU or scan barcode');
 fireEvent.change(search, { target: { value: '12' } }); fireEvent.submit(search.closest('form')!); await screen.findByText(/No exact SKU or barcode match/); expect(screen.queryByLabelText('Quantity 1')).toBeNull();
 fireEvent.change(search, { target: { value: '' } }); fireEvent.click(await screen.findByText('Add Panel')); fireEvent.change(screen.getByLabelText('Quantity 1'), { target: { value: '0' } }); fireEvent.change(screen.getByLabelText('Cash received'), { target: { value: '100' } });
 expect((screen.getByText('Complete cash sale') as HTMLButtonElement).disabled).toBe(true); expect((screen.getByText('Reserve walk-in sale without payment') as HTMLButtonElement).disabled).toBe(true);
});
it('prints only after an audited copy response and keeps the saved receipt identity', async () => {
 retailMock(); const receipt = { id: 'order-1', branch_id: 1, receipt_number: 1, total: 99, cash_received: 100, change_given: 1, tax_invoice: false, items: [{ name: 'Panel', quantity: 1, price: 90, tax: 9, tax_rate: 10, total: 99, discount: 10 }], invoices: [] };
 const original = vi.mocked(api).getMockImplementation()!;
 mockBackend(async (path, body, key) => path === '/sales' ? receipt : path === '/receipts/order-1/reprint' ? { ...receipt, copy: true } : original(path, body, key));
 const print = vi.spyOn(window, 'print').mockImplementation(() => {}); render(<App/>); fireEvent.click(await screen.findByText('Add Panel')); fireEvent.change(screen.getByLabelText('Cash received'), { target: { value: '100' } }); fireEvent.click(screen.getByText('Complete cash sale'));
 fireEvent.click(await screen.findByText('Print receipt copy')); await waitFor(() => expect(print).toHaveBeenCalledOnce()); expect(api).toHaveBeenCalledWith('/receipts/order-1/reprint', {}); expect(screen.getByText('COPY · Receipt 1/1')).toBeTruthy();
});

it('can finalize an owned reservation after its hold reduces free catalogue stock to zero', async () => {
 retailMock(); let held = false, paid = false;
 const original = vi.mocked(api).getMockImplementation()!;
 const draft = { id: 'd1', vendorName: 'Store', channel: 'IN_STORE', sessionId:'session', status: 'DRAFT', expiresAt: '2099-01-01T00:00:00Z', total: 99, message: 'Unpaid reservation', items: [{ productId: 1, name: 'Panel', quantity: 1, unitPrice: 90, tax: 9, total: 99, discount: 10 }] };
 const receipt = { id: 'd1', branch_id: 1, receipt_number: 1, total: 99, cash_received: 100, change_given: 1, tax_invoice: false, items: [{ name: 'Panel', quantity: 1, price: 90, tax: 9, tax_rate: 10, total: 99, discount: 10 }], invoices: [] };
 mockBackend(async (path, body, key) => {
  if (path === '/products') return [{ id: 1, name: 'Panel', sku: 'P1', barcode: '123', price: 90, list_price: 100, tax_rate: 10, quantity: held ? 0 : 1 }];
  if (path === '/drafts') { held = true; return draft; }
  if (path.startsWith('/drafts?')) return held ? [{ ...draft, status: paid ? 'PAID' : 'DRAFT' }] : [];
  if (path === '/drafts/d1/finalize-cash') { paid = true; return receipt; }
  return original(path, body, key);
 });
 render(<App/>); fireEvent.click(await screen.findByText('Add Panel')); fireEvent.click(screen.getByText('Reserve walk-in sale without payment')); await screen.findByText('0 available');
 fireEvent.change(screen.getByLabelText('Cash received for reserved sale'), { target: { value: '100' } }); const complete = screen.getByText('Complete reserved cash sale'); expect((complete as HTMLButtonElement).disabled).toBe(false); fireEvent.click(complete);
 await screen.findByLabelText('Sale receipt'); expect(api).toHaveBeenCalledWith('/drafts/d1/finalize-cash', { cashReceived: '100' }, undefined);
});
it('does not print a denied reprint and clears the old error before a successful retry', async () => {
 retailMock(); const receipt = { id: 'order-1', branch_id: 1, receipt_number: 1, total: 99, cash_received: 100, change_given: 1, tax_invoice: false, items: [], invoices: [] };
 const original = vi.mocked(api).getMockImplementation()!; let denied = true;
 mockBackend(async (path, body, key) => { if (path === '/sales') return receipt; if (path === '/receipts/order-1/reprint') { if (denied) throw new ApiError(403); return { ...receipt, copy: true }; } return original(path, body, key); });
 const print = vi.spyOn(window, 'print').mockImplementation(() => {}); render(<App/>); fireEvent.click(await screen.findByText('Add Panel')); fireEvent.change(screen.getByLabelText('Cash received'), { target: { value: '100' } }); fireEvent.click(screen.getByText('Complete cash sale')); fireEvent.click(await screen.findByText('Print receipt copy'));
 await screen.findByText('Request failed (403).'); expect(print).not.toHaveBeenCalled(); denied = false; fireEvent.click(screen.getByText('Print receipt copy')); await waitFor(() => expect(print).toHaveBeenCalledOnce()); expect(screen.queryByText('Request failed (403).')).toBeNull();
});

it('blocks other cash operations while a refund handover needs recovery',async()=>{
 sessionStorage.setItem('pos-session-v1','session');sessionStorage.setItem('pos-cash-refund-attempt-v1',JSON.stringify({key:'refund-saved',body:{returnId:'return-1',sessionId:'session',reason:'Returned',quoteHash:'a'.repeat(64),confirmation:'CASH_HANDED_TO_BUYER'},safeToDiscard:false}));
 mockBackend(async path=>{if(path==='/products')return [{id:1,name:'Panel',sku:'P1',price:100,tax_rate:10,quantity:1}];if(path==='/sessions/session')return {state:'OPEN'};return {state:'OPEN',openingFloat:200,cashReceived:0,changeGiven:0,cashIn:0,cashOut:0,safeDrops:0,cashRefunds:0,expectedCash:200,movements:[]};});
 render(<App/>);await screen.findByText('Retry same cash refund');await screen.findByText('Complete cash sale');expect((screen.getByText('Complete cash sale') as HTMLButtonElement).disabled).toBe(true);expect((screen.getByText('Record cash movement') as HTMLButtonElement).disabled).toBe(true);expect((await screen.findByText('Add Panel') as HTMLButtonElement).disabled).toBe(true);
});

it('keeps selected bill lines across pages and includes a server-scanned off-page product in checkout',async()=>{
 sessionStorage.setItem('pos-session-v1','session');const products=Array.from({length:26},(_,i)=>({id:i+1,name:`Item ${String(i+1).padStart(2,'0')}`,sku:`SKU${i+1}`,barcode:`B${i+1}`,price:10,tax_rate:0,quantity:2}));
 mockBackend(async path=>{if(path==='/products')return products;if(path==='/sessions/session')return {state:'OPEN'};if(path==='/sales')throw new Error('Lost sale response');if(path.startsWith('/drafts'))return [];return {state:'OPEN',openingFloat:50,cashReceived:0,changeGiven:0,cashIn:0,cashOut:0,safeDrops:0,expectedCash:50,movements:[]};});
 render(<App/>);fireEvent.click(await screen.findByText('Add Item 01'));const input=screen.getByLabelText('Search name, SKU or scan barcode');fireEvent.change(input,{target:{value:'SKU26'}});fireEvent.submit(input.closest('form')!);await screen.findByText('Added Item 26');await screen.findByText('Next POS products');fireEvent.click(screen.getByText('Next POS products'));await screen.findByText('Add Item 25');expect((screen.getByLabelText('Quantity 1') as HTMLInputElement).value).toBe('1');expect((screen.getByLabelText('Quantity 26') as HTMLInputElement).value).toBe('1');fireEvent.change(screen.getByLabelText('Cash received'),{target:{value:'20'}});fireEvent.click(screen.getByText('Complete cash sale'));await screen.findByText('Retry same sale');const body=vi.mocked(api).mock.calls.find(([path])=>path==='/sales')![1] as {items:{productId:number;quantity:number}[]};expect(body.items).toEqual([{productId:1,quantity:1},{productId:26,quantity:1}]);
});

it('blocks short cash locally and submits an explicitly chosen exact amount',async()=>{
 retailMock();const original=vi.mocked(api).getMockImplementation()!;mockBackend(async(path,body,key)=>path==='/sales'?{id:'fast-cash',branch_id:1,receipt_number:7,total:99,cash_received:99,change_given:0,tax_invoice:false,items:[],invoices:[]}:original(path,body,key));render(<App/>);fireEvent.click(await screen.findByText('Add Panel'));fireEvent.change(screen.getByLabelText('Cash received'),{target:{value:'98.99'}});expect((screen.getByText('Complete cash sale') as HTMLButtonElement).disabled).toBe(true);fireEvent.click(screen.getByText('Complete cash sale'));expect(vi.mocked(api).mock.calls.some(([path])=>path==='/sales')).toBe(false);fireEvent.click(screen.getByRole('button',{name:/Exact amount/}));expect((screen.getByLabelText('Cash received') as HTMLInputElement).value).toBe('99.00');expect((screen.getByText('Complete cash sale') as HTMLButtonElement).disabled).toBe(false);fireEvent.click(screen.getByText('Complete cash sale'));await waitFor(()=>expect(vi.mocked(api).mock.calls.some(([path,body])=>path==='/sales'&&(body as {cashReceived:string}).cashReceived==='99.00')).toBe(true));await screen.findByLabelText('Sale receipt');
});
