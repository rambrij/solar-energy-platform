import {authenticatedFetch} from './session';

async function save(path:string,fileName:string){
 const response=await authenticatedFetch(path,{method:'GET',cache:'no-store'});
 if(!response.ok)throw new Error('Evidence download failed.');
 const blob=await response.blob();
 const url=URL.createObjectURL(blob),anchor=document.createElement('a');
 anchor.href=url;anchor.download=fileName;anchor.rel='noopener';document.body.appendChild(anchor);anchor.click();anchor.remove();
 URL.revokeObjectURL(url);
}
export function downloadVendorClaimEvidence(claimId:number,evidenceId:string,fileName:string){
 return save(`/api/v1/vendor/warranty-claims/${claimId}/evidence/${evidenceId}`,fileName);
}
export function downloadCustomerClaimEvidence(claimId:number,evidenceId:string,fileName:string){
 return save(`/api/v1/customer/warranty-claims/${claimId}/evidence/${evidenceId}`,fileName);
}
