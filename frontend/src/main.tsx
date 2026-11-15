import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import Marketplace from './Marketplace';
import CustomerAssociation from './CustomerAssociation';
import AuthenticationBoundary, { SessionBootstrap } from './AuthenticationBoundary';
import WorkspaceErrorBoundary from './WorkspaceErrorBoundary';
import './style.css';

const path = window.location.pathname;
const marketplace = path === '/' || path === '/marketplace' || path === '/vendors' || path.startsWith('/vendors/');
const content = path === '/customer/associate'
  ? <AuthenticationBoundary permission="CUSTOMER" mode="online"><CustomerAssociation /></AuthenticationBoundary>
  : marketplace
    ? <Marketplace />
    : <AuthenticationBoundary permission="POS_VIEW" mode="pos"><App /></AuthenticationBoundary>;

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <SessionBootstrap>
      <WorkspaceErrorBoundary>{content}</WorkspaceErrorBoundary>
    </SessionBootstrap>
  </StrictMode>,
);
