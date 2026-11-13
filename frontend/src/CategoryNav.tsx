export type CategoryChoice = { id: string; name: string; count: number };
export default function CategoryNav({ categories, selected, onSelect, total }: { categories: CategoryChoice[]; selected: string; onSelect: (id: string) => void; total: number }) {
  return <nav className="category-navigation" aria-label="Product categories"><h3>Shop by category</h3><button className="category-choice" aria-pressed={selected === 'all'} onClick={() => onSelect('all')}><span>All products</span><small>{total}</small></button>{categories.map(category => <button key={category.id} className="category-choice" aria-pressed={selected === category.id} onClick={() => onSelect(category.id)}><span>{category.name}</span><small>{category.count}</small></button>)}</nav>;
}
