import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ShopPage } from '../pages/ShopPage';
import { catalogApi, mandatesApi } from '../../../services/api/endpoints';

vi.mock('../../../services/api/endpoints', async () => {
  const actual = await vi.importActual<typeof import('../../../services/api/endpoints')>(
    '../../../services/api/endpoints',
  );
  return {
    ...actual,
    catalogApi: {
      ...actual.catalogApi,
      marketplace: vi.fn(),
      search: vi.fn(),
      lookup: vi.fn(),
    },
    mandatesApi: {
      ...actual.mandatesApi,
      active: vi.fn(),
    },
  };
});

describe('ShopPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(mandatesApi.active).mockResolvedValue({
      mandateId: 'm-1',
      watchId: 'w-1',
      status: 'ACTIVE',
      currency: 'USD',
      maxWeeklySpend: '500.00',
      weeklySpent: '0.00',
      remainingBudget: '500.00',
      activeWatchesCount: 3,
      totalWatchesCount: 3,
      paypalConnected: true,
      totalVerifiedSavings: '120.00',
      blockedThreatsCount: 0,
      heldApprovalsCount: 0,
      capturedCount: 1,
      totalCommissionEarned: '4.50',
      maxUnitPrice: '260.00',
      allowedMerchants: ['bestbuy.com', 'target.com', 'walmart.com'],
    });
    vi.mocked(catalogApi.marketplace).mockResolvedValue({
      categories: ['Audio', 'Laptops'],
      totalCount: 2,
      source: 'marketplace-catalog',
      query: '',
      products: [
        {
          productId: 'aud-airpods-pro-2',
          title: 'Apple AirPods Pro (2nd Gen, USB-C)',
          brand: 'Apple',
          category: 'Audio',
          description: 'Active Noise Cancellation earbuds.',
          imageUrl: 'https://images.unsplash.com/photo-1590658268037-6bf12165a8df?w=800&auto=format&fit=crop&q=80',
          bestPrice: '189.99',
          baselinePrice: '249.00',
          bestSavings: '59.01',
          savingsPercent: '23.7',
          bestMerchantName: 'Target',
          bestMerchantDomain: 'target.com',
          storeCount: 4,
          offers: [
            {
              channel3ProductId: 'aud-airpods-pro-2-target',
              title: 'Apple AirPods Pro (2nd Gen, USB-C)',
              brand: 'Apple',
              category: 'Audio',
              merchantName: 'Target',
              merchantDomain: 'target.com',
              currentPrice: '189.99',
              baselinePrice: '249.00',
              verifiedSavings: '59.01',
              savingsPercent: '23.70',
              currency: 'USD',
              availability: 'IN_STOCK',
              condition: 'new',
              commissionRatePercent: '3.50',
              source: 'marketplace-catalog',
              observedAt: '2026-11-11T00:00:00Z',
            },
          ],
        },
        {
          productId: 'lap-mba-15-m3',
          title: 'Apple MacBook Air 15-inch M3',
          brand: 'Apple',
          category: 'Laptops',
          description: 'M3 ultrabook.',
          imageUrl: 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=800&auto=format&fit=crop&q=80',
          bestPrice: '1249.00',
          baselinePrice: '1499.00',
          bestSavings: '250.00',
          savingsPercent: '16.7',
          bestMerchantName: 'Best Buy',
          bestMerchantDomain: 'bestbuy.com',
          storeCount: 4,
          offers: [
            {
              channel3ProductId: 'lap-mba-15-m3-bb',
              title: 'Apple MacBook Air 15-inch M3',
              brand: 'Apple',
              category: 'Laptops',
              merchantName: 'Best Buy',
              merchantDomain: 'bestbuy.com',
              currentPrice: '1249.00',
              baselinePrice: '1499.00',
              verifiedSavings: '250.00',
              savingsPercent: '16.68',
              currency: 'USD',
              availability: 'IN_STOCK',
              condition: 'new',
              commissionRatePercent: '3.00',
              source: 'marketplace-catalog',
              observedAt: '2026-11-11T00:00:00Z',
            },
          ],
        },
      ],
    });
  });

  it('renders products across categories and flags items above the per-item limit', async () => {
    render(
      <MemoryRouter initialEntries={['/app/shop']}>
        <ShopPage />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText('Apple AirPods Pro (2nd Gen, USB-C)')).toBeInTheDocument();
    });

    expect(screen.getByText('Apple MacBook Air 15-inch M3')).toBeInTheDocument();
    expect(screen.getByText('Within Your $260 Limit')).toBeInTheDocument();
    expect(screen.getByText('Above $260 Per-Item Limit')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Direct Buy Now (PayPal · $189.99)' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Send to AI Agent / Watch' })).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: 'Compare Offers' })).toHaveLength(2);

    const airpodsImg = screen.getByAltText('Apple AirPods Pro (2nd Gen, USB-C)') as HTMLImageElement;
    const macbookImg = screen.getByAltText('Apple MacBook Air 15-inch M3') as HTMLImageElement;
    expect(airpodsImg.src).not.toEqual(macbookImg.src);

    fireEvent.error(airpodsImg);
    expect(airpodsImg.src).toContain('https://');
    expect(airpodsImg.dataset.fallbackStage).toBe('studio');
  });
});

