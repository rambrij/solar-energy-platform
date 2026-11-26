import {authenticatedFetch} from './session';
import {marketplaceApi,MarketplaceError} from './marketplaceApi';
import {downloadFilename,downloadReference} from './downloadHeaders';
import {jsonRequestOptions} from './jsonRequest';

export type CustomerReceiptPdfDownload={blob:Blob;filename:string;reference:string};

export async function requestCustomerReceiptPdf(orderId:string):Promise<CustomerReceiptPdfDownload>{
 const {token}=await marketplaceApi('/api/v1/customer/csrf');
 const response=await authenticatedFetch(
  \`/api/v1/customer/orders/\${encodeURIComponent(orderId)}/receipt-document\`,
  {method:'POST',...jsonRequestOptions({},token)}
 );
 if(!response.ok)throw new MarketplaceError(response.status);
 const reference=downloadReference(response,'X-Document-Reference');
 if(response.headers.get('Content-Type')?.split(';')[0].trim().toLowerCase()!=='application/pdf'||!reference)
  throw new Error('Invalid customer receipt PDF response');
 const blob=await response.blob();
 if(blob.size<5||blob.size>2_000_000)throw new Error('Invalid customer receipt PDF size');
 return {blob,filename:downloadFilename(response,\`receipt-\${reference}-customer-copy.pdf\`),reference};
}
