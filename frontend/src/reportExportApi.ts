import {api,ApiError} from './api';
export type CsvDownload={blob:Blob;filename:string;reference:string};
export async function requestReportExport(kind:'sales'|'cash-refunds',body:{from:string;to:string;reason:string;channel?:string}):Promise<CsvDownload>{
 const {token}=await api('/csrf');
 const response=await fetch(`/api/vendor/pos/reports/${kind}/export`,{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':token},body:JSON.stringify(body)});
 if(!response.ok)throw new ApiError(response.status);
 const reference=response.headers.get('X-Export-Reference')??'';
 if(!response.headers.get('Content-Type')?.toLowerCase().includes('text/csv')||!reference.match(/^[0-9a-f-]{36}$/i))throw new Error('Invalid export response');
 const filename=response.headers.get('Content-Disposition')?.match(/filename="?([A-Za-z0-9._-]+)"?/)?.[1]??'report-export.csv';
 return {blob:await response.blob(),filename,reference};
}
