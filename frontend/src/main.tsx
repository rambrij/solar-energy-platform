import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import Marketplace from './Marketplace';
import CustomerAssociation from './CustomerAssociation';
import AuthenticationBoundary, { SessionBootstrap } from './AuthenticationBoundary';
import PlatformBoundary from './PlatformBoundary';
import VendorInvitationAcceptance from './VendorInvitationAcceptance';
import WorkspaceErrorBoundary from './WorkspaceErrorBoundary';
import './style.css';

const path=window.location.pathname;
const platform=path==='/platform'||path.startsWith('/platform/');
const marketplace=path==='/'||path==='/marketplace'||path==='/vendors'||path.startsWith('/vendors/')||path.startsWith('/marketplace/');
const content=platform?<PlatformBoundary/>:path==='/vendor/invitation'
 ?<VendorInvitationAcceptance/>:path==='/customer/associate'
 ?<AuthenticationBoundary permission="CUSTOMER" mode="online"><CustomerAssociation/></AuthenticationBoundary>
 :marketplace?<Marketplace/>:<AuthenticationBoundary mode="pos"><App/></AuthenticationBoundary>;

createRoot(document.getElementById('root')!).render(
 <StrictMode><SessionBootstrap><WorkspaceErrorBoundary>{content}</WorkspaceErrorBoundary></SessionBootstrap></StrictMode>
);
