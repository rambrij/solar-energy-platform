import {authenticatedFetch} from './session';
import {api,ApiError} from './api';
import {downloadFilename, downloadReference} from './downloadHeaders';
import {jsonRequestOptions} from './jsonRequest';
export type CsvDownload={blob:Blob;filename:string;reference:string};
export async function requestReportExport(kind:'sales'|'cash-refunds',body:{from:string;to:string;reason:string;channel?:string}):Promise<CsvDownload>{
 const {token}=await api('/csrf');
 const response=await authenticatedFetch(`/api/v1/vendor/pos/reports/${kind}/export`,{
  method:'POST',
  ...jsonRequestOptions(body,token),
 });
 if(!response.ok)throw new ApiError(response.status);
 const reference=downloadReference(response,'X-Export-Reference');
 if(!response.headers.get('Content-Type')?.toLowerCase().includes('text/csv')||!reference)throw new Error('Invalid export response');
 const filename=downloadFilename(response,'report-export.csv');
 return {blob:await response.blob(),filename,reference};
}
