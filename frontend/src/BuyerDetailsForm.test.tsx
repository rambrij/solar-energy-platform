import {useState} from 'react';
import {cleanup,fireEvent,render,screen} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import BuyerDetailsForm,{type BuyerDetails} from './BuyerDetailsForm';
afterEach(cleanup);
it('starts as a guest and lets staff include and remove transaction details',()=>{function Form(){const [value,setValue]=useState<BuyerDetails>();return <BuyerDetailsForm value={value} onChange={setValue} locked={false}/>;}render(<Form/>);expect(screen.queryByLabelText('Buyer name')).toBeNull();fireEvent.click(screen.getByLabelText('Include buyer details supplied for this sale'));fireEvent.change(screen.getByLabelText('Buyer name'),{target:{value:'Walk-in buyer'}});expect((screen.getByLabelText('Buyer name') as HTMLInputElement).value).toBe('Walk-in buyer');expect(screen.getByText(/do not create, search or link/)).toBeTruthy();fireEvent.click(screen.getByLabelText('Include buyer details supplied for this sale'));expect(screen.queryByLabelText('Buyer name')).toBeNull();});
it('locks edits while the original sale request awaits acknowledgement',()=>{render(<BuyerDetailsForm value={{name:'Buyer'}} locked onChange={vi.fn()}/>);expect(screen.getByLabelText('Buyer name').matches(':disabled')).toBe(true);expect((screen.getByLabelText('Include buyer details supplied for this sale') as HTMLInputElement).disabled).toBe(true);});
