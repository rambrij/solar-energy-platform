import {checkoutStage,checkoutSteps,type CheckoutState} from './checkoutProgress';
export default function CheckoutSteps(state:CheckoutState){
 const {current,complete}=checkoutStage(state);
 return <ol className="shopping-steps" aria-label="Shopping steps">{checkoutSteps.map((label,index)=>{
  const done=complete||index<current,active=!complete&&index===current;
  return <li key={label} className={done?'step-done':active?'step-current':undefined} aria-current={active?'step':undefined}><strong aria-hidden="true">{done?'✓':index+1}</strong> {label}{done&&<span className="visually-hidden"> (done)</span>}</li>;
 })}</ol>;
}
