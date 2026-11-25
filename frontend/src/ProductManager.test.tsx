import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import ProductManager from './ProductManager';
import {vendorApi} from './api';
vi.mock('./api',()=>({vendorApi:vi.fn()}));vi.mock('./session',()=>({useSession:()=>({account:{permissions:['PRODUCT_VIEW','PRODUCT_CREATE','PRODUCT_UPDATE','PRODUCT_ARCHIVE','PRICE_VIEW','PRICE_UPDATE']}})}));afterEach(()=>{cleanup();vi.resetAllMocks();});
it('creates a product without accepting tenant, stock or calculated totals',async()=>{
 vi.mocked(vendorApi).mockResolvedValue({items:[],page:0,totalPages:0,totalElements:0});render(<ProductManager/>);fireEvent.change(screen.getByLabelText('Product name'),{target:{value:'Panel'}});fireEvent.change(screen.getByLabelText('Product SKU'),{target:{value:'P-NEW'}});fireEvent.change(screen.getByLabelText('Product unit price before tax'),{target:{value:'125.50'}});fireEvent.change(screen.getByLabelText('Product change reason'),{target:{value:'Approved new panel'}});fireEvent.click(screen.getByText('Save product'));await screen.findByRole('status');expect(vendorApi).toHaveBeenCalledWith('/products','POST',{sku:'P-NEW',barcode:null,name:'Panel',price:'125.50',taxRate:'0.00',published:false,categoryId:null,expectedVersion:0,reason:'Approved new panel'});
});
it('keeps the loaded version and entered data when an edit outcome is uncertain',async()=>{
 vi.mocked(vendorApi).mockImplementation(async(path,method)=>{if(method==='PUT')throw Error('Connection lost');return {items:path.startsWith('/categories')?[]:[{id:7,sku:'P7',barcode:null,name:'Panel',price:100,taxRate:10,published:true,categoryId:null,version:3}],page:0,totalPages:1,totalElements:1};});render(<ProductManager/>);fireEvent.click(screen.getByText('Load vendor products'));fireEvent.click(await screen.findByText('Edit product Panel'));fireEvent.change(screen.getByLabelText('Product name'),{target:{value:'Updated panel'}});fireEvent.change(screen.getByLabelText('Product change reason'),{target:{value:'Correct description'}});fireEvent.click(screen.getByText('Save product'));await screen.findByRole('alert');expect(vendorApi).toHaveBeenLastCalledWith('/products/7','PUT',expect.objectContaining({expectedVersion:3,name:'Updated panel'}));expect((screen.getByLabelText('Product name') as HTMLInputElement).value).toBe('Updated panel');
});

it('keeps the submitted catalogue search and publication filter while paging',async()=>{
 vi.mocked(vendorApi).mockImplementation(async(path)=>path.startsWith('/categories')?
  {items:[],page:0,totalPages:0,totalElements:0}:
  {items:[],page:path.includes('page=1')?1:0,totalPages:2,totalElements:2});
 render(<ProductManager/>);
 const search=screen.getByLabelText('Search product name, SKU, or barcode');
 fireEvent.change(search,{target:{value:'Panel & cable'}});
 fireEvent.change(screen.getByLabelText('Product publication status'),{target:{value:'false'}});
 fireEvent.click(screen.getByText('Load vendor products'));
 await screen.findByText('Unpublished products only.');
 expect(vendorApi).toHaveBeenCalledWith('/products?page=0&size=20&q=Panel%20%26%20cable&published=false');
 fireEvent.change(search,{target:{value:'new draft query'}});
 fireEvent.click(screen.getByText('Next products'));
 await waitFor(()=>expect(vendorApi).toHaveBeenCalledWith('/products?page=1&size=20&q=Panel%20%26%20cable&published=false'));
});
