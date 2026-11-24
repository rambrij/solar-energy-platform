import {useEffect,useState} from 'react';
import {platformApi} from './api';

type Health={status:string;checkedAt:string|null};
const refreshIntervalMs=30_000;

export default function PlatformHealthWidget(){
 const [health,setHealth]=useState<Health>({status:'CHECKING',checkedAt:null});

 useEffect(()=>{
  let active=true;
  let timer:number|undefined;
  async function refresh(){
   try{
    const result:Health=await platformApi('/health');
    if(active)setHealth(result);
   }catch{
    if(active)setHealth(previous=>({status:'UNAVAILABLE',checkedAt:previous.checkedAt}));
   }finally{
    if(active)timer=window.setTimeout(()=>void refresh(),refreshIntervalMs);
   }
  }

  void refresh();
  return ()=>{
   active=false;
   window.clearTimeout(timer);
  };
 },[]);

 const {status,checkedAt}=health;
 return <span className="platform-health-indicator" role="status" aria-label={`Platform health ${status}`}>
  <span className={status==='UP'?'healthy':status==='CHECKING'?'checking':'unhealthy'}/>
  {status==='UP'?'System healthy':status==='CHECKING'?'Checking system':'System '+status.toLowerCase()}
  {checkedAt&&<small>Last checked <time dateTime={checkedAt}>{new Date(checkedAt).toLocaleTimeString()}</time></small>}
 </span>;
}
