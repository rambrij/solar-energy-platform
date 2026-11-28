import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import VendorLeadInbox from './VendorLeadInbox';
import { vendorApi } from './api';
import { usePermissions } from './usePermissions';

vi.mock('./api', () => ({ vendorApi: vi.fn() }));
vi.mock('./usePermissions', () => ({ usePermissions: vi.fn() }));

const lead = {
 id: 21, contactName: 'Demo buyer', contactEmail: 'buyer@example.test',
 message: 'Survey requested', createdAt: '2026-11-28T09:00:00Z',
 status: 'NEW', version: 2, assignedTo: null,
};

function grant(...permissions: string[]) {
 vi.mocked(usePermissions).mockReturnValue({
  hasPermission: permission => permissions.includes(permission),
 } as ReturnType<typeof usePermissions>);
}

beforeEach(() => {
 grant('LEAD_VIEW');
 vi.mocked(vendorApi).mockImplementation(async path => {
  if (path === '/leads') return [lead];
  throw new Error('Unexpected request: ' + path);
 });
});
afterEach(() => { cleanup(); vi.resetAllMocks(); });

it('loads enquiries without requesting assignees or exposing writes for a read-only grant', async () => {
 render(<VendorLeadInbox />);
 expect(screen.getByRole('status').textContent).toContain('Loading enquiries');
 await screen.findByText(lead.contactName);
 expect(screen.queryByText('Update lead status')).toBeNull();
 expect(screen.queryByText('Change lead assignment')).toBeNull();
 expect(vendorApi).toHaveBeenCalledTimes(1);
});

it('requires an audit reason and updates the row from the server response', async () => {
 grant('LEAD_VIEW', 'LEAD_UPDATE');
 vi.mocked(vendorApi).mockImplementationOnce(async () => [lead])
  .mockResolvedValueOnce({ ...lead, status: 'CONTACTED', version: 3 });
 render(<VendorLeadInbox />);
 fireEvent.click(await screen.findByText('Update lead status'));
 fireEvent.change(screen.getByLabelText('New status'), { target: { value: 'CONTACTED' } });
 expect((screen.getByText('Save lead change') as HTMLButtonElement).disabled).toBe(true);
 fireEvent.change(screen.getByLabelText('Reason'), { target: { value: ' Called buyer ' } });
 fireEvent.click(screen.getByText('Save lead change'));
 await screen.findByText('Lead status saved.');
 expect(vendorApi).toHaveBeenLastCalledWith('/leads/21', 'PATCH', {
  expectedVersion: 2, reason: 'Called buyer', status: 'CONTACTED',
 });
 expect(screen.getByLabelText('Vendor enquiry inbox').textContent).toContain('CONTACTED');
});

it('uses the vendor assignee list and preserves the existing row when a stale assignment fails', async () => {
 grant('LEAD_VIEW', 'LEAD_ASSIGN');
 vi.mocked(vendorApi).mockImplementation(async path => {
  if (path === '/leads') return [lead];
  if (path === '/leads/assignees') return ['sales@example.test'];
  throw new Error('Lead changed; reload before saving');
 });
 render(<VendorLeadInbox />);
 fireEvent.click(await screen.findByText('Change lead assignment'));
 fireEvent.change(screen.getByLabelText('Assignee'), { target: { value: 'sales@example.test' } });
 fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'Allocate follow-up' } });
 fireEvent.click(screen.getByText('Save lead change'));
 expect((await screen.findByRole('alert')).textContent).toContain('reload before saving');
 expect(vendorApi).toHaveBeenLastCalledWith('/leads/21/assignment', 'PUT', {
  expectedVersion: 2, reason: 'Allocate follow-up', employeeUsername: 'sales@example.test',
 });
 expect(screen.getByText(/NEW · Unassigned/)).toBeTruthy();
 expect(screen.queryByText('Lead assignment saved.')).toBeNull();
});
