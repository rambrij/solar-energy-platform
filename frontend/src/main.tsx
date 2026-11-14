import {createRoot} from 'react-dom/client';
import App from './App';
import Marketplace from './Marketplace';
import CustomerAssociation from './CustomerAssociation';
import AuthenticationBoundary,{SessionBootstrap} from './AuthenticationBoundary';
import './style.css';
const marketplace=window.location.pathname==='/'||window.location.pathname==='/marketplace'||window.location.pathname==='/vendors'||window.location.pathname.startsWith('/vendors/');
const content=window.location.pathname==='/customer/associate'?<AuthenticationBoundary permission="CUSTOMER" mode="online"><CustomerAssociation/></AuthenticationBoundary>:marketplace?<Marketplace/>:<AuthenticationBoundary permission="POS_VIEW" mode="pos"><App/></AuthenticationBoundary>;
createRoot(document.getElementById('root')!).render(<SessionBootstrap>{content}</SessionBootstrap>);
