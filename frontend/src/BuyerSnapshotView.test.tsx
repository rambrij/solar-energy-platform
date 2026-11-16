import {cleanup,render,screen} from '@testing-library/react';
import {afterEach,expect,it} from 'vitest';
import BuyerSnapshotView from './BuyerSnapshotView';
afterEach(cleanup);
it('omits absent guest details',()=>{const {container}=render(<BuyerSnapshotView/>);expect(container.textContent).toBe('');});
it('renders supplied snapshot values as text and keeps the address lines',()=>{const {container}=render(<BuyerSnapshotView buyer={{name:'<script>Buyer</script>',taxId:'TAX-123',address:'Street\nCity'}}/>);expect(screen.getByText('<script>Buyer</script>')).toBeTruthy();expect(screen.getByText('TAX-123')).toBeTruthy();expect(container.querySelector('script')).toBeNull();expect(container.querySelector('.buyer-address')?.textContent).toBe('Street\nCity');expect(screen.queryByText('Contact')).toBeNull();});
