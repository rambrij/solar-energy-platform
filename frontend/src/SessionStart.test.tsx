import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import SessionStart from './SessionStart';
import { api, ApiError } from './api';

vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof import('./api')>(), api: vi.fn() }));
afterEach(() => { cleanup(); vi.resetAllMocks(); });
const openShift = { id: 'own-shift', registerId: 900001, openingFloat: 50, state: 'OPEN' };

it('resumes an existing shift explicitly without posting a new float', async () => {
  vi.mocked(api).mockImplementation(async path => path === '/sessions/active' ? [openShift] : openShift);
  const onSession = vi.fn();
  render(<SessionStart onSession={onSession} />);
  fireEvent.click(await screen.findByText('Resume register 900001'));
  await waitFor(() => expect(onSession).toHaveBeenCalledWith('own-shift','OPEN'));
  expect(api).toHaveBeenCalledWith('/sessions/own-shift/resume', {});
  expect(vi.mocked(api).mock.calls.some(call => call[0] === '/sessions')).toBe(false);
  expect(screen.getByText(/Opening float 50.00/)).toBeTruthy();
});
it('refreshes recoverable sessions after an occupied register conflict', async () => {
  let conflicted = false;
  vi.mocked(api).mockImplementation(async path => {
    if(path.startsWith('/registers?'))return {branchId:1,page:0,size:12,totalElements:1,totalPages:1,items:[{id:900001,available:true}]};
    if (path === '/sessions') { conflicted = true; throw new ApiError(409); }
    return conflicted ? [openShift] : [];
  });
  render(<SessionStart onSession={vi.fn()} />);
  await screen.findByText('No open sessions found for your account.');
  fireEvent.click(screen.getByText('Load assigned registers'));await screen.findByRole('option',{name:'Register 900001 · Available'});
  fireEvent.change(screen.getByLabelText('Assigned register'), { target: { value: '900001' } });
  fireEvent.change(screen.getByLabelText('Opening float'), { target: { value: '0' } });
  fireEvent.click(screen.getByText('Open session'));
  await screen.findByText(/This register already has an open session/);
  await screen.findByText('Resume register 900001');
});
it('finds the created shift after an uncertain opening response', async () => {
  let attempted = false;
  vi.mocked(api).mockImplementation(async path => {
    if(path.startsWith('/registers?'))return {branchId:1,page:0,size:12,totalElements:1,totalPages:1,items:[{id:900001,available:true}]};
    if (path === '/sessions') { attempted = true; throw new Error('Lost response'); }
    return attempted ? [openShift] : [];
  });
  render(<SessionStart onSession={vi.fn()} />);
  await screen.findByText('No open sessions found for your account.');
  fireEvent.click(screen.getByText('Load assigned registers'));await screen.findByRole('option',{name:'Register 900001 · Available'});
  fireEvent.change(screen.getByLabelText('Assigned register'), { target: { value: '900001' } });
  fireEvent.change(screen.getByLabelText('Opening float'), { target: { value: '50' } });
  fireEvent.click(screen.getByText('Open session'));
  await screen.findByText(/Session opening was not confirmed/);
  await screen.findByText('Resume register 900001');
});
it('does not switch to a different session while a sale is unresolved', async () => {
  vi.mocked(api).mockResolvedValue([openShift, { ...openShift, id: 'other-shift', registerId: 900002 }]);
  render(<SessionStart onSession={vi.fn()} requiredSessionId="own-shift" />);
  await screen.findByText('Resume register 900001');
  expect((screen.getByText('Resume register 900001') as HTMLButtonElement).disabled).toBe(false);
  expect((screen.getByText('Resume register 900002') as HTMLButtonElement).disabled).toBe(true);
  expect((screen.getByText('Open session').closest('fieldset') as HTMLFieldSetElement).disabled).toBe(true);
});
it('does not activate a shift that closed before resume', async () => {
  let closed = false;
  vi.mocked(api).mockImplementation(async path => {
    if (path.endsWith('/resume')) { closed = true; throw new ApiError(409); }
    return closed ? [] : [openShift];
  });
  const onSession = vi.fn(); render(<SessionStart onSession={onSession} />);
  fireEvent.click(await screen.findByText('Resume register 900001'));
  await screen.findByText(/Could not resume this session/);
  await screen.findByText('No open sessions found for your account.');
  expect(onSession).not.toHaveBeenCalled();
});

it('blocks new session selection after a failed own-session lookup until refresh succeeds',async()=>{
 let failed=true;vi.mocked(api).mockImplementation(async()=>{if(failed)throw new Error('offline');return [];});render(<SessionStart onSession={vi.fn()}/>);await screen.findByText(/Could not load your open sessions/);expect(screen.getByText('Load assigned registers').matches(':disabled')).toBe(true);expect(screen.getByText('Open session').matches(':disabled')).toBe(true);failed=false;fireEvent.click(screen.getByText('Refresh open sessions'));await screen.findByText('No open sessions found for your account.');await waitFor(()=>expect(screen.getByText('Load assigned registers').matches(':disabled')).toBe(false));
});
