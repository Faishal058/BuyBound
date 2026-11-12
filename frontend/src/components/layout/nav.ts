export type NavIconKey = 'bag' | 'bell' | 'tag' | 'receipt' | 'shield' | 'sliders';

export interface NavItem {
  to: string;
  label: string;
  shortLabel?: string;
  icon: NavIconKey;
}

export const APP_NAV_ITEMS: NavItem[] = [
  { to: '/app/shop', label: 'Shop Products', shortLabel: 'Shop', icon: 'bag' },
  { to: '/app/watches', label: 'Price Alerts', shortLabel: 'Alerts', icon: 'bell' },
  { to: '/app/offers', label: 'Compare Offers', shortLabel: 'Offers', icon: 'tag' },
  { to: '/app/purchases', label: 'Orders & Receipts', shortLabel: 'Orders', icon: 'receipt' },
  { to: '/app/safety', label: 'Wallet & Safety', shortLabel: 'Safety', icon: 'shield' },
  { to: '/app/agent-settings', label: 'Agent Settings', shortLabel: 'Agent', icon: 'sliders' },
];
