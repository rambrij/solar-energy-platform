import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import StockReceiving from './StockReceiving';
import {vendorApi} from './api';
vi.mock('./api',()=>({vendorApi:vi.fn(),ApiError:class extends Error{constructor(public status:number){super('Rejected');}}}));const {sessionPermissions}=vi.hoisted(()=>({sessionPermissions:['PRODUCT_VIEW','INVENTORY_RECEIVE','INVENTORY_VIEW'] as string[]}));
vi.mock('./session',()=>({useSession:()=>({account:{username:'alice',tenantId:1,branchId:1,permissions:sessionPermissions}})}));
beforeEach(()=>{sessionPermissions.splice(0,sessionPermissions.length,'PRODUCT_VIEW','INVENTORY_RECEIVE','INVENTORY_VIEW');});
afterEach(()=>{cleanup();sessionStorage.clear();vi.resetAllMocks();});
const products={items:[{id:7,name:'Panel',sku:'P7',published:false}],page:0,totalPages:1};
async function fill(){fireEvent.click(screen.getByText('Load products for receiving'));await screen.findByText('Panel (P7) - unpublished');fireEvent.change(screen.getByLabelText('Receiving product'),{target:{value:'7'}});fireEvent.change(screen.getByLabelText('Quantity received'),{target:{value:'5'}});fireEvent.change(screen.getByLabelText('Stock receiving reason'),{target:{value:'Delivery inspected'}});}
it('receives stock into the server-assigned location and refreshes availability',async()=>{const changed=vi.fn();vi.mocked(vendorApi).mockImplementation(async(_path,method)=>method==='POST'?{id:'receipt-1',quantity:5,availableAfter:5}:products);render(<StockReceiving onChanged={changed}/>);await fill();fireEvent.click(screen.getByText('Record stock receipt'));await screen.findByRole('status');expect(vendorApi).toHaveBeenLastCalledWith('/inventory/receipts','POST',{productId:7,quantity:5,reason:'Delivery inspected'},expect.any(String));expect(changed).toHaveBeenCalledTimes(1);expect(sessionStorage.length).toBe(0);});
it('locks the command after connection loss and retries exactly the same delivery once',async()=>{vi.mocked(vendorApi).mockImplementation(async(_path,method)=>{if(method==='POST')throw Error('Connection lost');return products;});render(<StockReceiving/>);await fill();fireEvent.click(screen.getByText('Record stock receipt'));await screen.findByRole('alert');const original=vi.mocked(vendorApi).mock.calls.at(-1);expect(screen.getByLabelText('Quantity received').matches(':disabled')).toBe(true);fireEvent.click(screen.getByText('Retry same stock receipt'));await screen.findByRole('alert');expect(vi.mocked(vendorApi).mock.calls.at(-1)).toEqual(original);expect(sessionStorage.length).toBe(1);});

it('does not expose the stock form when product lookup permission is missing',()=>{
 sessionPermissions.splice(0,sessionPermissions.length,'INVENTORY_RECEIVE','INVENTORY_VIEW');
 render(<StockReceiving/>);
 expect(screen.queryByLabelText('Receiving product')).toBeNull();
 expect(screen.getByRole('status').textContent).toContain('requires PRODUCT_VIEW');
 expect(screen.getByText('Load stock receipt history')).toBeTruthy();
});

it('applies the product filter to receipt history and keeps it when paging',async()=>{
 vi.mocked(vendorApi).mockImplementation(async(path)=>path.includes('page=1')?
  {items:[],page:1,totalPages:2}:
  {items:[{id:'receipt-7',productId:7,quantity:2,actor:'alice',reason:'Delivery',
   createdAt:'2026-11-24T10:00:00Z'}],page:0,totalPages:2});
 render(<StockReceiving/>);
 const product=screen.getByLabelText('Filter stock receipt history by product ID');
 fireEvent.change(product,{target:{value:'7'}});
 fireEvent.click(screen.getByText('Load stock receipt history'));
 await screen.findByText(/Product #7.*Receipt receipt-7/);
 expect(vendorApi).toHaveBeenLastCalledWith('/inventory/receipts?page=0&size=20&productId=7');
 fireEvent.change(product,{target:{value:'8'}});
 fireEvent.click(screen.getByText('Next stock receipts'));
 await screen.findByText('No stock receipts found for this outlet and filter.');
 expect(vendorApi).toHaveBeenLastCalledWith('/inventory/receipts?page=1&size=20&productId=7');
 fireEvent.click(screen.getByText('Load stock receipt history'));
 await waitFor(()=>expect(vendorApi).toHaveBeenLastCalledWith('/inventory/receipts?page=0&size=20&productId=8'));
});

it('searches receiving products across pages and retains the selected product identity',async()=>{
 vi.mocked(vendorApi).mockImplementation(async(path,method)=>{
  if(method==='POST')return {id:'receipt-7',quantity:1,availableAfter:5};
  if(path.includes('page=1'))return {items:[{id:8,name:'Second panel',sku:'P8',published:true}],page:1,totalPages:2};
  return {...products,totalPages:2};
 });
 render(<StockReceiving/>);
 const search=screen.getByLabelText('Search receiving products by name, SKU, or barcode');
 fireEvent.change(search,{target:{value:' Panel & cable '}});
 fireEvent.click(screen.getByText('Load products for receiving'));
 await screen.findByRole('option',{name:'Panel (P7) - unpublished'});
 expect(vendorApi).toHaveBeenLastCalledWith('/products?page=0&size=20&q=Panel%20%26%20cable');
 const product=screen.getByLabelText('Receiving product') as HTMLSelectElement;
 fireEvent.change(product,{target:{value:'7'}});
 fireEvent.change(search,{target:{value:'Unsubmitted search'}});
 fireEvent.click(screen.getByRole('button',{name:'Next receiving products'}));
 await screen.findByRole('option',{name:'Second panel (P8)'});
 expect(vendorApi).toHaveBeenLastCalledWith('/products?page=1&size=20&q=Panel%20%26%20cable');
 expect(product.value).toBe('7');
 expect(product.selectedOptions[0].textContent).toContain('Panel (P7)');
 fireEvent.change(screen.getByLabelText('Stock receiving reason'),{target:{value:'Delivery'}});
 fireEvent.click(screen.getByRole('button',{name:'Record stock receipt'}));
 await screen.findByText(/Stock receipt receipt-7/);
 expect(vendorApi).toHaveBeenLastCalledWith('/inventory/receipts','POST',
  {productId:7,quantity:1,reason:'Delivery'},expect.any(String));
});
