import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import Marketplace from './Marketplace';
import CustomerAssociation from './CustomerAssociation';
import AuthenticationBoundary, { SessionBootstrap } from './AuthenticationBoundary';
import PlatformBoundary from './PlatformBoundary';
import VendorInvitationAcceptance from './VendorInvitationAcceptance';
import CustomerEmailVerification from './CustomerEmailVerification';
import WorkspaceErrorBoundary from './WorkspaceErrorBoundary';
import './style.css';

const path=window.location.pathname;
const platform=path==='/platform'||path.startsWith('/platform/');
const marketplace=path==='/'||path==='/marketplace'||path==='/vendors'||path.startsWith('/vendors/')||path.startsWith('/marketplace/');
const content=platform?<PlatformBoundary/>:path==='/verify-email'
 ?<CustomerEmailVerification/>:path==='/vendor/invitation'
 ?<VendorInvitationAcceptance/>:path==='/customer/associate'
 ?<AuthenticationBoundary permission="CUSTOMER" mode="online"><CustomerAssociation/></AuthenticationBoundary>
 :marketplace?<Marketplace/>:<AuthenticationBoundary mode="pos"><App/></AuthenticationBoundary>;

// Email verification is a public, one-shot confirmation flow. It does not need
// session restoration; mounting SessionBootstrap here would request a second
// CSRF token concurrently with the verification POST and can race the cookie.
const application=<WorkspaceErrorBoundary>{content}</WorkspaceErrorBoundary>;
const sessionAwareApplication=path==='/verify-email'
 ?application
 :<SessionBootstrap>{application}</SessionBootstrap>;

createRoot(document.getElementById('root')!).render(
 <StrictMode>{sessionAwareApplication}</StrictMode>
);
