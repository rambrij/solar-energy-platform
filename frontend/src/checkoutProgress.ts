export const checkoutSteps=['Choose your solar essentials','Review one vendor cart','Reserve at checkout','Pay and get your receipt'] as const;
export type CheckoutState={cartItems:number;reviewing:boolean;reserved:boolean;paid:boolean};
/** Index of the current step (0-3) and whether the whole journey is complete. */
export function checkoutStage({cartItems,reviewing,reserved,paid}:CheckoutState):{current:number;complete:boolean}{
 if(reserved)return {current:3,complete:false};
 if(paid&&cartItems===0)return {current:3,complete:true};
 if(reviewing)return {current:2,complete:false};
 if(cartItems>0)return {current:1,complete:false};
 return {current:0,complete:false};
}
