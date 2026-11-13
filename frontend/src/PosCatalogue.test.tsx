import { cleanup,fireEvent,render,screen,waitFor } from '@testing-library/react';
import { afterEach,expect,it,vi } from 'vitest';
import PosCatalogue,{type PosProduct} from './PosCatalogue';
import { api,ApiError } from './api';
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:vi.fn()}));
const product:PosProduct={id:1,name:'Panel',sku:'P1',barcode:'123',price:90,list_price:100,tax_rate:10,quantity:2};
const page={items:[product],page:0,size:24,totalElements:25,totalPages:2,catalogueTotal:25,categories:[]};
const props={locked:false,quantities:{},revision:0,onProducts:vi.fn(),onAdd:vi.fn(()=>true),onBusyChange:vi.fn()};
afterEach(()=>{cleanup();vi.resetAllMocks();props.onAdd.mockReturnValue(true);});
it('loads bounded branch products and forwards only an explicitly selected product',async()=>{
 vi.mocked(api).mockResolvedValue(page);render(<PosCatalogue {...props}/>);fireEvent.click(await screen.findByText('Add Panel'));expect(api).toHaveBeenCalledWith('/catalogue?q=&category=all&sort=name&inStock=false&page=0&size=24');expect(props.onAdd).toHaveBeenCalledWith(product);expect(props.onProducts).toHaveBeenCalledWith([product]);expect(vi.mocked(api).mock.calls.some(([path])=>path==='/products')).toBe(false);
});
it('scans a product absent from the current page and restores scanner focus',async()=>{
 const remote={...product,id:99,name:'Battery',sku:'BAT',barcode:'999'};
 vi.mocked(api).mockImplementation(async path=>path.startsWith('/catalogue/scan?')?remote:page);render(<PosCatalogue {...props}/>);const input=screen.getByLabelText('Search name, SKU or scan barcode');fireEvent.change(input,{target:{value:'999'}});fireEvent.submit(input.closest('form')!);await screen.findByText('Added Battery');expect(props.onAdd).toHaveBeenCalledWith(remote);expect(api).toHaveBeenCalledWith('/catalogue/scan?code=999');await waitFor(()=>expect(document.activeElement).toBe(input));expect((input as HTMLInputElement).value).toBe('');expect(props.onBusyChange).toHaveBeenLastCalledWith(false);
});
it.each([[400,'Enter a SKU or barcode up to 80 characters'],[404,'No exact SKU or barcode match'],[409,'More than one barcode match'],[500,'Scan lookup was not confirmed']])('does not add an unconfirmed or ambiguous scan (%s)',async(status,message)=>{
 vi.mocked(api).mockImplementation(async path=>{if(path.startsWith('/catalogue/scan?'))throw new ApiError(Number(status));return page;});render(<PosCatalogue {...props}/>);const input=screen.getByLabelText('Search name, SKU or scan barcode');fireEvent.change(input,{target:{value:'12'}});fireEvent.submit(input.closest('form')!);await screen.findByText(new RegExp(String(message)));expect(props.onAdd).not.toHaveBeenCalled();expect(props.onBusyChange).toHaveBeenLastCalledWith(false);
});
it('blocks product entry during a financial lock while permitting read-only browsing',async()=>{
 vi.mocked(api).mockResolvedValue(page);render(<PosCatalogue {...props} locked/>);expect((await screen.findByText('Add Panel') as HTMLButtonElement).disabled).toBe(true);fireEvent.change(screen.getByLabelText('Search name, SKU or scan barcode'),{target:{value:'123'}});fireEvent.click(screen.getByText('Add exact match'));expect(props.onAdd).not.toHaveBeenCalled();expect(vi.mocked(api).mock.calls.some(([path])=>path.startsWith('/catalogue/scan?'))).toBe(false);
});
it('pages without changing the selected bill and resets page for a new query',async()=>{
 vi.mocked(api).mockImplementation(async path=>new URL(path,'http://localhost').searchParams.get('page')==='1'?{...page,page:1,items:[{...product,id:25,name:'Inverter'}]}:page);render(<PosCatalogue {...props} quantities={{1:1}}/>);await screen.findByText('Add Panel');fireEvent.click(screen.getByText('Next POS products'));await screen.findByText('Add Inverter');expect(props.onAdd).not.toHaveBeenCalled();fireEvent.change(screen.getByLabelText('Search name, SKU or scan barcode'),{target:{value:'Panel'}});await screen.findByText('Add Panel');expect(vi.mocked(api).mock.calls.some(([path])=>path.includes('q=Panel')&&path.includes('page=0'))).toBe(true);
});
it('hides failed catalogue data and retries the same scoped read without adding to the bill',async()=>{
 vi.mocked(api).mockRejectedValueOnce(new ApiError(403)).mockResolvedValue(page);render(<PosCatalogue {...props}/>);fireEvent.click(await screen.findByText('Retry POS catalogue'));await screen.findByText('Add Panel');expect(props.onAdd).not.toHaveBeenCalled();expect(vi.mocked(api).mock.calls[0][0]).toBe(vi.mocked(api).mock.calls[1][0]);
});

it('ignores a stale page response after a newer branch filter read completes',async()=>{
 let finish!:(value:typeof page)=>void;
 vi.mocked(api).mockImplementation(async path=>{const params=new URL(path,'http://localhost').searchParams;if(params.get('sort')==='price-high'&&params.get('inStock')==='false')return new Promise(resolve=>{finish=resolve;});return page;});render(<PosCatalogue {...props}/>);await screen.findByText('Add Panel');fireEvent.change(screen.getByLabelText('POS sort products'),{target:{value:'price-high'}});await waitFor(()=>expect(finish).toBeTruthy());fireEvent.click(screen.getByLabelText('POS in stock only'));await screen.findByText('Add Panel');finish({...page,items:[{...product,name:'Stale inverter'}]});await waitFor(()=>expect(screen.queryByText('Add Stale inverter')).toBeNull());expect(props.onAdd).not.toHaveBeenCalled();
});
