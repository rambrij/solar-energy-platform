export function downloadFile<T extends {blob:Blob;filename:string}>(value:T){
 const url=URL.createObjectURL(value.blob);const link=document.createElement('a');link.href=url;link.download=value.filename;document.body.append(link);
 try{link.click();}finally{link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);}
}
