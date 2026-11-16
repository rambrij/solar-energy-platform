import {useEffect,useState} from 'react';
import {platformApi} from './api';

export default function PlatformHealthWidget(){
 const [status,setStatus]=useState('CHECKING');
 useEffect(()=>{let live=true;const refresh=()=>platformApi('/health').then(r=>{if(live)setStatus(r.status);}).catch(()=>{if(live)setStatus('UNAVAILABLE');});void refresh();return()=>{live=false;};},[]);
 return <span className="platform-health-indicator" aria-label={`Platform health ${status}`}><span className={status==='UP'?'healthy':status==='CHECKING'?'checking':'unhealthy'}/>{status==='UP'?'System healthy':status==='CHECKING'?'Checking system':'System '+status.toLowerCase()}</span>;
}
