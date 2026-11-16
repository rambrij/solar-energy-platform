import {describe,expect,it} from 'vitest';
import {checkoutStage,checkoutSteps} from './checkoutProgress';
const base={cartItems:0,reviewing:false,reserved:false,paid:false};
describe('checkoutStage',()=>{
 it('walks choose -> review -> reserve -> pay',()=>{
  expect(checkoutStage(base)).toEqual({current:0,complete:false});
  expect(checkoutStage({...base,cartItems:2})).toEqual({current:1,complete:false});
  expect(checkoutStage({...base,cartItems:2,reviewing:true})).toEqual({current:2,complete:false});
  expect(checkoutStage({...base,cartItems:2,reviewing:true,reserved:true})).toEqual({current:3,complete:false});
 });
 it('completes only when paid with an emptied cart and not while a hold is open',()=>{
  expect(checkoutStage({...base,paid:true})).toEqual({current:3,complete:true});
  expect(checkoutStage({...base,paid:true,cartItems:1})).toEqual({current:1,complete:false});
  expect(checkoutStage({...base,paid:true,reserved:true})).toEqual({current:3,complete:false});
 });
 it('names four steps',()=>{expect(checkoutSteps).toHaveLength(4);});
});
