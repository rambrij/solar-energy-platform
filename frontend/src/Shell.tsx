import {useEffect,useRef,useState,type ReactNode} from 'react';
import {availableNavigation,navigationItems,selectedNavigation,type WorkspaceMode} from './navigation';
export function SolarMark() {
  return <svg viewBox="0 0 48 48" aria-hidden="true" focusable="false"><circle cx="31" cy="15" r="7" fill="currentColor"/><path d="M31 3v3m0 18v3M19 15h-3m30 0h-3M22 6l2 2m14 14 2 2M40 6l-2 2" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/><path d="M8 23h23l6 17H2z" fill="#173c64" stroke="#fff" strokeWidth="1.5"/><path d="M12 23 9 40m14-17 4 17M5 32h29" stroke="#91b5cf" strokeWidth="1.5"/><path d="M15 40v5m-5 0h15" stroke="#173c64" strokeWidth="2"/></svg>;
}
export default function Shell({ mode, children }: { mode: WorkspaceMode; children: ReactNode }) {
  const [open,setOpen]=useState(false),[hash,setHash]=useState(window.location.hash),[available,setAvailable]=useState<string[]>(['main-content']);
  const toggle=useRef<HTMLButtonElement>(null),main=useRef<HTMLElement>(null),handledHash=useRef('');
  const items=availableNavigation(mode,new Set(available)),selected=selectedNavigation(items,hash);
  useEffect(()=>{
    function sync(){setHash(window.location.hash);setOpen(false);}
    function update(){const next=navigationItems(mode).filter(item=>document.getElementById(item.id)).map(item=>item.id);setAvailable(previous=>previous.join('|')===next.join('|')?previous:next);}
    update();sync();const observer=new MutationObserver(update);if(main.current)observer.observe(main.current,{childList:true,subtree:true});
    window.addEventListener('hashchange',sync);window.addEventListener('popstate',sync);
    return()=>{observer.disconnect();window.removeEventListener('hashchange',sync);window.removeEventListener('popstate',sync);};
  },[mode]);
  useEffect(()=>{
    if(!hash){handledHash.current='';return;}
    if(handledHash.current===hash)return;
    const item=items.find(item=>`#${item.id}`===hash),target=item?document.getElementById(item.id):null;
    if(target){handledHash.current=hash;target.setAttribute('tabindex','-1');target.focus({preventScroll:true});target.scrollIntoView?.({block:'start'});}
  },[hash,available,mode]);
  function navigate(event:React.MouseEvent<HTMLAnchorElement>,id:string){
    if(event.button!==0||event.ctrlKey||event.metaKey||event.shiftKey||event.altKey)return;
    const target=document.getElementById(id);if(!target)return;event.preventDefault();
    if(window.location.hash!==`#${id}`)window.history.pushState(window.history.state,'',`#${id}`);
    handledHash.current=`#${id}`;setHash(`#${id}`);setOpen(false);if(target instanceof HTMLDetailsElement)target.open=true;target.setAttribute('tabindex','-1');target.focus({preventScroll:true});target.scrollIntoView?.({block:'start'});
  }
  return <div className="app-shell">
    <a className="skip-link" href="#main-content">Skip to content</a>
    <header className="site-header"><a className="brand" href={mode === 'pos' ? '/' : '/vendors'}><span className="brand-mark"><SolarMark/></span><span>Saumya Solar<small>ENERGY FOR A BRIGHTER TOMORROW</small></span></a>
      <nav aria-label="Main navigation"><a href="/vendors" aria-current={mode === 'online' ? 'page' : undefined}>Marketplace</a><a href="/" aria-current={mode === 'pos' ? 'page' : undefined}>Store POS</a></nav>
    </header>
    <div className="workspace-bar"><span className="workspace-label"><span className="status-dot"/>{mode === 'pos' ? 'Retail workspace' : 'Solar marketplace'}</span><span>Shared stock · Vendor-owned sales</span></div>
    <div className="workspace-layout"><aside className={`workspace-menu ${open?'menu-open':''}`} aria-label="Workspace menu" onKeyDown={event=>{if(event.key==='Escape'&&open){setOpen(false);toggle.current?.focus();}}}>
      <button type="button" ref={toggle} className="workspace-menu-toggle" aria-expanded={open} aria-controls="workspace-navigation" onClick={()=>setOpen(value=>!value)}><span aria-hidden="true">☰</span> {open?'Close workspace menu':'Open workspace menu'}</button>
      <nav id="workspace-navigation" className="workspace-navigation" aria-label="Workspace sections">{Array.from(new Set(items.map(item=>item.group))).map(group=><div className="workspace-menu-group" key={group}><h2>{group}</h2><ul>{items.filter(item=>item.group===group).map(item=><li key={item.id}><a href={`#${item.id}`} aria-current={selected?.id===item.id?'location':undefined} onClick={event=>navigate(event,item.id)}>{item.label}</a></li>)}</ul></div>)}</nav>
    </aside><div className="workspace-content"><nav className="workspace-breadcrumb" aria-label="Breadcrumb"><ol><li>{mode==='pos'?'Store POS':'Marketplace'}</li>{selected&&<><li>{selected.group}</li><li aria-current="location">{selected.label}</li></>}</ol></nav>
    <main ref={main} tabIndex={-1} id="main-content" className={mode === 'pos' ? 'workspace pos-workspace' : 'workspace marketplace-workspace'}>{children}</main></div></div>
    <footer className="site-footer"><span>Saumya Solar Energy</span><span>Smarter energy. Thoughtful service.</span></footer>
  </div>;
}
