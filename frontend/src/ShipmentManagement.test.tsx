import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';import {afterEach,expect,it,vi} from 'vitest';import ShipmentManagement from './ShipmentManagement';import {vendorApi} from './api';
vi.mock('./api',()=>({vendorApi:vi.fn()}));afterEach(()=>{cleanup();vi.resetAllMocks();});
it('registers a shipment and appends a tracking event using the tenant APIs',async()=>{
 let rows:any[]=[];vi.mocked(vendorApi).mockImplementation(async(path,method='GET',body?:any)=>{
 if(path==='/shipments/orders')return rows.length?[]:[{orderId:'order-1',orderedAt:'2026-11-26T08:00:00Z',total:100}];
 if(path==='/shipments')return rows;
 if(path==='/orders/order-1/shipments'&&method==='POST'){rows=[{id:1,orderId:'order-1',carrier:body.carrier,trackingReference:body.trackingReference,trackingUrl:null,events:[]}];return rows[0];}
 if(path==='/shipments/1/events'&&method==='POST'){rows[0].events.push({id:2,eventCode:body.eventCode,description:body.description,reason:body.reason});return rows[0].events[0];}
 return [];
 });
 render(<ShipmentManagement/>);fireEvent.change(await screen.findByLabelText('Paid online order'),{target:{value:'order-1'}});fireEvent.change(screen.getByLabelText('Carrier'),{target:{value:'Carrier'}});fireEvent.change(screen.getByLabelText('Tracking reference'),{target:{value:'T-1'}});fireEvent.change(screen.getByLabelText('Audit reason'),{target:{value:'Shipment created'}});fireEvent.click(screen.getByText('Register shipment'));await screen.findByText('Carrier · order-1');expect(vendorApi).toHaveBeenCalledWith('/orders/order-1/shipments','POST',expect.objectContaining({trackingReference:'T-1'}));
 fireEvent.change(screen.getByLabelText('Event code'),{target:{value:'IN_TRANSIT'}});fireEvent.change(screen.getByLabelText('Event description'),{target:{value:'Parcel moving'}});fireEvent.change(screen.getByLabelText('Audit reason'),{target:{value:'Carrier update'}});fireEvent.click(screen.getByText('Add tracking event'));await screen.findByText(/IN_TRANSIT/);expect(vendorApi).toHaveBeenCalledWith('/shipments/1/events','POST',expect.objectContaining({description:'Parcel moving'}));
});

it('captures serial numbers against an existing shipment and reloads its asset ledger',async()=>{
 let captured:any[]=[];
 vi.mocked(vendorApi).mockImplementation(async(path,method='GET',body?:any)=>{
  if(path==='/shipments/orders')return [];
  if(path==='/shipments')return [{id:7,orderId:'paid-order',carrier:'Carrier',trackingReference:'TRK-7',trackingUrl:null,events:[]}];
  if(path==='/shipments/7/serial-assets'&&method==='POST'){captured=body.serialNumbers.map((serialNumber:string,index:number)=>({id:index+1,productId:44,productName:'Solar inverter',serialNumber,orderId:'paid-order',shipmentId:7,installationId:null,recordedAt:'2026-11-28T10:00:00Z'}));return captured;}
  if(path==='/shipments/7/serial-assets')return captured;
  return [];
 });
 render(<ShipmentManagement/>);
 await screen.findByText('Carrier · paid-order');
 fireEvent.change(screen.getByLabelText('Product ID'),{target:{value:'44'}});
 fireEvent.change(screen.getByLabelText('Serial numbers (one per line or comma-separated)'),{target:{value:'SN-001\nSN-002'}});
 fireEvent.change(screen.getByLabelText('Serial capture reason'),{target:{value:'Recorded during packing'}});
 fireEvent.click(screen.getByText('Record serial numbers'));
 await screen.findByText('Solar inverter — SN-001');
 await screen.findByText('Solar inverter — SN-002');
 expect(vendorApi).toHaveBeenCalledWith('/shipments/7/serial-assets','POST',expect.objectContaining({productId:44,serialNumbers:['SN-001','SN-002']}));
});
