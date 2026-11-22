import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import CategoryNav from './CategoryNav';
afterEach(cleanup);
it('provides named category controls and exposes the active selection', () => {
 const select = vi.fn(); render(<CategoryNav categories={[{ id: '1', name: 'Panels', count: 3 }]} selected="1" onSelect={select} total={3}/>);
 expect(screen.getByRole('button', { name: /Panels/ }).getAttribute('aria-pressed')).toBe('true');
 fireEvent.click(screen.getByRole('button', { name: /All products/ })); expect(select).toHaveBeenCalledWith('all');
});
