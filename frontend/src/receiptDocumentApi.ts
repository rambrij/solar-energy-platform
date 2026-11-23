import {authenticatedFetch} from './session';
import {api,ApiError} from './api';
import {downloadFilename, downloadReference} from './downloadHeaders';
import {jsonRequestOptions} from './jsonRequest';
export type ReceiptPdfDownload={blob:Blob;filename:string;reference:string};
export async function requestReceiptPdf(orderId:string,reason:string):Promise<ReceiptPdfDownload>{
 const {token}=await api('/csrf');
 const response=await authenticatedFetch(`/api/v1/vendor/pos/receipts/${encodeURIComponent(orderId)}/document`,{
  method:'POST',
  ...jsonRequestOptions({reason},token),
 });
 if(!response.ok)throw new ApiError(response.status);
 const reference=downloadReference(response,'X-Document-Reference');
 if(response.headers.get('Content-Type')?.split(';')[0].trim().toLowerCase()!=='application/pdf'||!reference)throw new Error('Invalid receipt PDF response');
 const blob=await response.blob();
 if(blob.size<5||blob.size>2_000_000)throw new Error('Invalid receipt PDF size');
 return {blob,filename:downloadFilename(response,`receipt-copy-${reference}.pdf`),reference};
}
