export function printDocument(kind: 'sale' | 'refund') {
 document.body.dataset.printDocument = kind;
 try { window.print(); } finally { delete document.body.dataset.printDocument; }
}
