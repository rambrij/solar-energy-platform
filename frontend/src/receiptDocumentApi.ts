import {api,ApiError} from './api';
export type ReceiptPdfDownload={blob:Blob;filename:string;reference:string};
export async function requestReceiptPdf(orderId:string,reason:string):Promise<ReceiptPdfDownload>{
 const {token}=await api('/csrf');const response=await fetch(`/api/vendor/pos/receipts/${encodeURIComponent(orderId)}/document`,{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':token},body:JSON.stringify({reason})});
 if(!response.ok)throw new ApiError(response.status);const reference=response.headers.get('X-Document-Reference')??'';
 if(response.headers.get('Content-Type')?.split(';')[0].trim().toLowerCase()!=='application/pdf'||!reference.match(/^[0-9a-f-]{36}$/i))throw new Error('Invalid receipt PDF response');
 const blob=await response.blob();if(blob.size<5||blob.size>2_000_000)throw new Error('Invalid receipt PDF size');
 const filename=response.headers.get('Content-Disposition')?.match(/filename="?([A-Za-z0-9._-]+)"?/)?.[1]??`receipt-copy-${reference}.pdf`;return {blob,filename,reference};
}
