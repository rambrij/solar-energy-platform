import {cleanup,fireEvent,render,screen,waitFor,within} from '@testing-library/react';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import WarehouseTransfers from './WarehouseTransfers';
import {vendorApi} from './api';

const {permissions}=vi.hoisted(()=>({permissions:['INVENTORY_VIEW','INVENTORY_TRANSFER'] as string[]}));
vi.mock('./api',()=>({vendorApi:vi.fn(),ApiError:class extends Error{constructor(public status:number){super('Rejected');}}}));
vi.mock('./session',()=>({useSession:()=>({account:{username:'alice',tenantId:1,branchId:10,permissions}})}));

beforeEach(()=>{permissions.splice(0,permissions.length,'INVENTORY_VIEW','INVENTORY_TRANSFER');vi.mocked(vendorApi).mockImplementation(async(path)=>{
 if(path==='/inventory/warehouses')return [];
 if(path==='/inventory/transfers')return [];
 return {};
});});
afterEach(()=>{cleanup();vi.resetAllMocks();});

it('loads only the warehouse and transfer endpoints for the active vendor workspace',async()=>{
 render(<WarehouseTransfers/>);
 await screen.findByText('No warehouses found for this branch.');
 await screen.findByText('No transfers found.');
 expect(vendorApi).toHaveBeenCalledWith('/inventory/warehouses');
 expect(vendorApi).toHaveBeenCalledWith('/inventory/transfers');
});

it('creates a warehouse with a reason and refreshes the scoped lists',async()=>{
 vi.mocked(vendorApi).mockImplementation(async(path,method)=>{
  if(path==='/inventory/warehouses'&&method==='POST')return {id:2,branchId:10,code:'MAIN',name:'Main Warehouse',active:true,version:0};
  if(path==='/inventory/warehouses')return [{id:2,branchId:10,code:'MAIN',name:'Main Warehouse',active:true,version:0}];
  if(path==='/inventory/warehouses/2/stock')return [{productId:7,sku:'P7',productName:'Panel',onHand:5,reserved:1,damaged:0,available:4,version:1}];
  if(path==='/inventory/transfers')return [];
  return {};
 });
 render(<WarehouseTransfers/>);
 await screen.findByText('No warehouses found for this branch.');
 fireEvent.change(screen.getByLabelText('Warehouse code'),{target:{value:'main'}});
 fireEvent.change(screen.getByLabelText('Warehouse name'),{target:{value:'Main Warehouse'}});
 fireEvent.change(screen.getByLabelText('Reason'),{target:{value:'Approved storage location'}});
 fireEvent.click(screen.getByRole('button',{name:'Create warehouse'}));
 await screen.findByText('MAIN — Main Warehouse (Active)');
 fireEvent.click(screen.getByRole('button',{name:'View stock'}));
 await screen.findByText('Panel');
 expect(vendorApi).toHaveBeenCalledWith('/inventory/warehouses/2/stock');
 expect(vendorApi).toHaveBeenCalledWith('/inventory/warehouses','POST',{
  code:'MAIN',name:'Main Warehouse',reason:'Approved storage location'
 });
});

it('creates a branch-to-branch transfer with an idempotency key',async()=>{
 vi.mocked(vendorApi).mockImplementation(async(path,method)=>{
  if(path==='/inventory/transfers'&&method==='POST')return {id:'transfer-1'};
  if(path==='/inventory/warehouses')return [];
  if(path==='/inventory/transfers')return [];
  return {};
 });
 render(<WarehouseTransfers/>);
 await screen.findByText('No transfers found.');
 fireEvent.change(screen.getByLabelText('Destination branch ID'),{target:{value:'20'}});
 fireEvent.change(screen.getByLabelText('Product ID'),{target:{value:'7'}});
 fireEvent.change(screen.getByLabelText('Quantity'),{target:{value:'4'}});
 fireEvent.change(screen.getByLabelText('Transfer reason'),{target:{value:'Replenishment'}});
 fireEvent.click(screen.getByRole('button',{name:'Create draft transfer'}));
 await screen.findByRole('status');
 await waitFor(()=>expect(vendorApi).toHaveBeenCalledWith('/inventory/transfers','POST',{
  sourceType:'BRANCH',sourceWarehouseId:null,destinationType:'BRANCH',
  destinationWarehouseId:null,destinationBranchId:20,productId:7,quantity:4,reason:'Replenishment'
 },expect.any(String)));
});

it('hides mutation forms when the actor only has inventory view',async()=>{
 permissions.splice(0,permissions.length,'INVENTORY_VIEW');
 render(<WarehouseTransfers/>);
 await screen.findByText('No warehouses found for this branch.');
 expect(screen.queryByRole('button',{name:'Create warehouse'})).toBeNull();
 expect(screen.queryByRole('button',{name:'Create draft transfer'})).toBeNull();
});


it('updates warehouse details and active state with optimistic version and audit reason',async()=>{
 const warehouse={id:2,branchId:10,code:'MAIN',name:'Main Warehouse',active:true,version:3};
 vi.mocked(vendorApi).mockImplementation(async(path,method)=>{
  if(path==='/inventory/warehouses')return [warehouse];
  if(path==='/inventory/transfers')return [];
  if(path==='/inventory/warehouses/2'&&method==='PATCH')return {...warehouse,name:'Central Warehouse',active:false,version:4};
  return {};
 });
 render(<WarehouseTransfers/>);
 await screen.findByText('MAIN — Main Warehouse (Active)');
 fireEvent.click(screen.getByRole('button',{name:'Edit warehouse'}));
 fireEvent.change(screen.getByLabelText('Warehouse name'),{target:{value:'Central Warehouse'}});
 fireEvent.click(screen.getByLabelText('Warehouse active'));
 fireEvent.change(screen.getByLabelText('Change reason'),{target:{value:'Consolidated after stock reconciliation'}});
 fireEvent.click(screen.getByRole('button',{name:'Save warehouse changes'}));
 await screen.findByRole('status');
 await waitFor(()=>expect(vendorApi).toHaveBeenCalledWith('/inventory/warehouses/2','PATCH',{
  expectedVersion:3,name:'Central Warehouse',active:false,reason:'Consolidated after stock reconciliation'
 }));
});
