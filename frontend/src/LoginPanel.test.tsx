import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import LoginPanel from './LoginPanel';
import {signIn} from './session';
vi.mock('./session',()=>({signIn:vi.fn()}));afterEach(()=>{cleanup();vi.resetAllMocks();});
it('uses named credentials and clears the password after successful sign-in',async()=>{vi.mocked(signIn).mockResolvedValue();const completed=vi.fn();render(<LoginPanel onSignedIn={completed}/>);fireEvent.change(screen.getByLabelText('Email or username'),{target:{value:'cashier'}});fireEvent.change(screen.getByLabelText('Password'),{target:{value:'test-password'}});fireEvent.submit(screen.getByRole('form'));await waitFor(()=>expect(completed).toHaveBeenCalledOnce());expect(signIn).toHaveBeenCalledWith('cashier','test-password',false);expect((screen.getByLabelText('Password') as HTMLInputElement).value).toBe('');});
it('customer registration and failed login never leave a password displayed',async()=>{vi.mocked(signIn).mockRejectedValue(Error('Sign-in unavailable'));render(<LoginPanel/>);fireEvent.click(screen.getByText('Create a customer account'));fireEvent.change(screen.getByLabelText('Email address'),{target:{value:'buyer@example.com'}});fireEvent.change(screen.getByLabelText('Password'),{target:{value:'test-password-for-registration'}});fireEvent.submit(screen.getByRole('form'));await screen.findByRole('alert');expect((screen.getByLabelText('Password') as HTMLInputElement).value).toBe('');expect(signIn).toHaveBeenCalledWith('buyer@example.com','test-password-for-registration',true);});

it('requires a dual Customer/Vendor identity to select one actor context before retrying',async()=>{
 vi.mocked(signIn)
  .mockRejectedValueOnce(Object.assign(new Error('This identity has both customer and vendor access.'),{status:409}))
  .mockResolvedValueOnce();
 const completed=vi.fn();render(<LoginPanel onSignedIn={completed}/>);
 fireEvent.change(screen.getByLabelText('Email or username'),{target:{value:'dual@example.test'}});
 fireEvent.change(screen.getByLabelText('Password'),{target:{value:'dual-context-password'}});
 fireEvent.submit(screen.getByRole('form'));
 await screen.findByText('Choose one workspace context');
 fireEvent.click(screen.getByLabelText('Vendor employee'));
 fireEvent.submit(screen.getByRole('form'));
 await waitFor(()=>expect(completed).toHaveBeenCalledOnce());
 expect(signIn).toHaveBeenNthCalledWith(1,'dual@example.test','dual-context-password',false);
 expect(signIn).toHaveBeenNthCalledWith(2,'dual@example.test','dual-context-password',false,undefined,'VENDOR');
 expect((screen.getByLabelText('Password') as HTMLInputElement).value).toBe('');
});
