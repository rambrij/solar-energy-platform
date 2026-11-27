import {authenticatedFetch} from './session';

async function save(response:Response,fileName:string){
 if(!response.ok)throw new Error('Evidence download failed.');
 const blob=await response.blob();
 const url=URL.createObjectURL(blob),anchor=document.createElement('a');
 anchor.href=url;anchor.download=fileName;anchor.rel='noopener';document.body.appendChild(anchor);anchor.click();anchor.remove();
 URL.revokeObjectURL(url);
}
export async function downloadVendorClaimEvidence(claimId:number,evidenceId:string,fileName:string){
 const response=await authenticatedFetch(`/api/v1/vendor/warranty-claims/${claimId}/evidence/${evidenceId}`,{method:'GET',cache:'no-store'});
 await save(response,fileName);
}
export async function downloadCustomerClaimEvidence(claimId:number,evidenceId:string,fileName:string){
 const response=await authenticatedFetch(`/api/v1/customer/warranty-claims/${claimId}/evidence/${evidenceId}`,{method:'GET',cache:'no-store'});
 await save(response,fileName);
}
