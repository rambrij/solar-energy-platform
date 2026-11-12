import {createRoot} from 'react-dom/client';
import App from './App';
import Marketplace from './Marketplace';
import './style.css';
const marketplace=window.location.pathname==='/marketplace'||window.location.pathname==='/vendors'||window.location.pathname.startsWith('/vendors/');
createRoot(document.getElementById('root')!).render(marketplace?<Marketplace/>:<App/>);
