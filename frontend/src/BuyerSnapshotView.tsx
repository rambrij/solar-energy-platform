import type {BuyerDetails} from './BuyerDetailsForm';
export default function BuyerSnapshotView({buyer}:{buyer?:BuyerDetails|null}){
 if(!buyer)return null;
 return <section className="buyer-snapshot" aria-label="Buyer details at sale"><h3>Buyer details at sale</h3><dl>{buyer.name&&<div><dt>Buyer</dt><dd>{buyer.name}</dd></div>}{buyer.contact&&<div><dt>Contact</dt><dd>{buyer.contact}</dd></div>}{buyer.taxId&&<div><dt>Tax identifier</dt><dd>{buyer.taxId}</dd></div>}{buyer.address&&<div><dt>Address</dt><dd className="buyer-address">{buyer.address}</dd></div>}</dl></section>;
}
