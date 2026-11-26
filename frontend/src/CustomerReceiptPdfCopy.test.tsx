import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import CustomerReceiptPdfCopy from './CustomerReceiptPdfCopy';
import {requestCustomerReceiptPdf} from './customerReceiptPdfApi';
import {downloadFile} from './fileDownload';
vi.mock('./customerReceiptPdfApi',()=>({requestCustomerReceiptPdf:vi.fn()}));
vi.mock('./fileDownload',()=>({downloadFile:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();});
const file={blob:new Blob(['%PDF-copy']),filename:'receipt-copy.pdf',reference:'11111111-1111-4111-8111-111111111111'};
it('downloads only after the customer explicitly requests a PDF copy',async()=>{
 vi.mocked(requestCustomerReceiptPdf).mockResolvedValue(file);
 render(<CustomerReceiptPdfCopy orderId="order-1" disabled={false}/>);
 expect(requestCustomerReceiptPdf).not.toHaveBeenCalled();
 fireEvent.click(screen.getByText('Download PDF receipt copy'));
 await waitFor(()=>expect(downloadFile).toHaveBeenCalledWith(file));
 expect(requestCustomerReceiptPdf).toHaveBeenCalledWith('order-1');
 expect(screen.getByText(/PDF receipt copy generated/)).toBeTruthy();
});
it('keeps the browser print path available when PDF creation fails',async()=>{
 vi.mocked(requestCustomerReceiptPdf).mockRejectedValue(new Error('PDF rejected'));
 render(<CustomerReceiptPdfCopy orderId="order-1" disabled={false}/>);
 fireEvent.click(screen.getByText('Download PDF receipt copy'));
 await screen.findByRole('alert');
 expect(downloadFile).not.toHaveBeenCalled();
});
it('ignores a late PDF response after the component unmounts or order changes',async()=>{
 let resolve!:(value:typeof file)=>void;
 vi.mocked(requestCustomerReceiptPdf).mockImplementation(()=>new Promise(done=>{resolve=done;}));
 const view=render(<CustomerReceiptPdfCopy orderId="one" disabled={false}/>);
 fireEvent.click(screen.getByText('Download PDF receipt copy'));
 view.rerender(<CustomerReceiptPdfCopy orderId="two" disabled={false}/>);
 resolve(file);await Promise.resolve();
 expect(downloadFile).not.toHaveBeenCalled();
});
