import type {Draft} from './draftTypes';
export type HeldTab='all'|'held'|'done';
export const heldTabs:{id:HeldTab;label:string}[]=[{id:'all',label:'All'},{id:'held',label:'Held bills'},{id:'done',label:'Completed'}];
export const isHeld=(draft:Draft)=>draft.status==='DRAFT';
export function filterDrafts(drafts:Draft[],tab:HeldTab){return tab==='all'?drafts:drafts.filter(draft=>tab==='held'?isHeld(draft):!isHeld(draft));}
export function countDrafts(drafts:Draft[]){const held=drafts.filter(isHeld).length;return {all:drafts.length,held,done:drafts.length-held};}
/** Human time left on a hold; never negative. */
export function formatTimeLeft(ms:number):string{
 if(!Number.isFinite(ms)||ms<=0)return 'Expired';
 const total=Math.floor(ms/1000),days=Math.floor(total/86400),hours=Math.floor(total%86400/3600),minutes=Math.floor(total%3600/60),seconds=total%60;
 if(days>0)return `${days}d ${hours}h`;
 if(hours>0)return `${hours}h ${minutes}m`;
 return `${minutes}:${String(seconds).padStart(2,'0')}`;
}
