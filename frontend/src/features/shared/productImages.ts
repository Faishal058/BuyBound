import type { SyntheticEvent } from 'react';
import { getApiBaseUrl } from '../../services/api/client';

/**
 * Verified HTTP-200 studio product fallback pools mapped by category so images never fall
 * back to a single repeated placeholder or a broken icon.
 */
const CATEGORY_FALLBACK_POOL: Record<string, readonly string[]> = {
  Audio: [
    'https://cdn.trychannel3.com/cleaned/v1/a3b8b014-b3b2-40be-b363-b3402bb7e855?aid=01M767ET0P8FHYV5FQ3XX7P7HB&mid=oOY7',
    'https://cdn.trychannel3.com/cleaned/v1/c6a01953-ca4a-47ee-8b6d-70e93d8210c8?aid=01M767CC7FZ9YZ2E45DBFRJD4W&mid=BsaX',
    'https://cdn.trychannel3.com/cleaned/v1/44ea931f-59e1-46c5-b4a3-ec780ecf912b?aid=01M767F06BHN6ZREH319ZHFEPZ&mid=ICa0',
    'https://cdn.trychannel3.com/cleaned/v1/670ebbbe-24a3-4277-9c25-ee052ccc0401?aid=01M767CJANX8M9EPH62HWSEVE5&mid=CgP5',
  ],
  'Phones & Tablets': [
    'https://cdn.trychannel3.com/cleaned/v1/d7ea12d6-91e8-4d9b-a874-0c6a18d77701?aid=01M767M9NQ38EW71W27JGKTPN7&mid=gTIp',
    'https://cdn.trychannel3.com/ee2e1c60-1a1b-419c-9e00-d1b05c5e1db6?aid=01M767CNG3N9YFQX2BC2D5X55M&mid=BsaX',
    'https://cdn.trychannel3.com/cleaned/v1/0f60421d-1476-4ea3-8f4a-7f90aa940551?aid=01M767CQ3AD0F14920VKX0AA6Q&mid=TgS4',
    'https://cdn.trychannel3.com/9af7d459-47ae-4fc4-86d4-1cef70b4c2a1?aid=01M767CR53C2PBE4M9MD506QP4&mid=dCgO',
  ],
  Laptops: [
    'https://cdn.trychannel3.com/c938c0ec-d365-4be9-a116-8a4f9b88b34c?aid=01M767CTNPSR8MFD3D93GCXYFX&mid=DoAg',
    'https://cdn.trychannel3.com/ef47f9c7-900c-46e3-96bd-0b2559bc3425?aid=01M767CVQ32EZA6423NXC8Q434&mid=p1PB',
    'https://cdn.trychannel3.com/19a86516-499b-405b-b6b9-f8c74867f68d?aid=01M767CWY1N5NJ3S3CYFE5NQ9N&mid=LPzf',
    'https://cdn.trychannel3.com/cleaned/v1/82bcbc17-b07d-47a3-882f-536db8f4ce93?aid=01M767CYFX5WZ02W6BY9Y8236B&mid=g0qz',
  ],
  Gaming: [
    'https://cdn.trychannel3.com/cleaned/v1/ad4afa8c-5448-4e35-bc4c-46dd78587a2f?aid=01M767D0V52QZBZZB4H7AZ5H6S&mid=hl1t',
    'https://cdn.trychannel3.com/c88dd36d-ee96-4da0-aad6-840b6261c899?aid=01M767D1YRYYT8EVYG3WSTFW53&mid=Qoqa',
    'https://cdn.trychannel3.com/08baa0dd-2a20-4984-b049-525bd7caf215?aid=01M767D3C44QN67N665E193ARX&mid=vVQC',
    'https://cdn.trychannel3.com/c1616d4c-fd7a-44b2-b50f-2fd6575f5889?aid=01M767D493K2EA3K9KS253Q5DH&mid=BsaX',
  ],
  'Sneakers & Fashion': [
    'https://cdn.trychannel3.com/cleaned/v1/1db7a8d3-780f-4d08-9022-59e0504a4a0e?aid=01M767EYA6SN8BF73BETWMZT6S&mid=wscm',
    'https://cdn.trychannel3.com/4331ab0f-6cca-45a2-b934-d74ad70c6dd5?aid=01M767CK5RDXNYDHPMV08YJYBR&mid=hHvi',
    'https://cdn.trychannel3.com/cleaned/v1/0f2fa932-0cdb-4720-bf20-c53a452c26c8?aid=01M767CMSM1KY86CMQGKF11D44&mid=4Raz',
    'https://cdn.trychannel3.com/cleaned/v1/d275f05b-4a33-41a2-8f33-785646976394?aid=01M767CP8YS5VCAC3NKFESMBD6&mid=YikB',
  ],
  'Home & Kitchen': [
    'https://cdn.trychannel3.com/cleaned/v1/0517df1d-aedf-4166-902b-9489275cbdef?aid=01M767CSFP29Y11MFW2C92DK9H&mid=K6RA',
    'https://cdn.trychannel3.com/d5ec0f3e-c355-4e78-a619-3bb682dbfec1?aid=01M767CTPRTKBT1EG0H0MAQ2RT&mid=nSgk',
    'https://cdn.trychannel3.com/91cf3b24-7a27-427f-8631-1c2f4741e7c0?aid=01M767CVW4CCHR74XF4CTT8J6A&mid=w4H1',
    'https://cdn.trychannel3.com/cleaned/v1/65db5562-6386-4a82-b002-548b5a36cdea?aid=01M767CZFNVQF3884MBNNKE4TK&mid=DyQi',
  ],
  Watches: [
    'https://cdn.trychannel3.com/ca01491f-b37f-4208-bdc5-0198cd7b06b7?aid=01M767D0SXYSMYGZX0ZRP2A3ZH&mid=p1PB',
    'https://cdn.trychannel3.com/ebd5edbf-a517-4bca-9c2d-4a2fd3be9c73?aid=01M767D1Q0DMG928DTVE1EQR1H&mid=fdOw',
    'https://cdn.trychannel3.com/cleaned/v1/7d392aab-e062-47d9-8af0-9f8d9cdaca53?aid=01M767D302VB7TEDVCVX7Y1RET&mid=K6RA',
    'https://cdn.trychannel3.com/f7822e16-e9a9-4f00-96de-ea826901ad39?aid=01M767D4ATGDN88TDNSCJWCVHX&mid=r9yS',
  ],
  Cameras: [
    'https://cdn.trychannel3.com/cleaned/v1/3dab2449-5cbf-4b8b-8849-0aaadb11326a?aid=01M767D6S8NAMNRC8GSNGWPRHG&mid=ZQQu',
    'https://cdn.trychannel3.com/cleaned/v1/8d6f6877-6e11-4a09-87e0-6608704087d4?aid=01M767D7ZJ6HK844WX6PA8M0P8&mid=oHaU',
    'https://cdn.trychannel3.com/cleaned/v1/455866be-f885-4d1b-951e-a94abf86d1e1?aid=01M767D9BQ6V7NDK0TM8TRJ25V&mid=oHaU',
    'https://cdn.trychannel3.com/907eaf14-c3b8-414f-923d-620d9ea31649?aid=01M767DAMWQ48M26Y9V0Y53AZH&mid=Qoqa',
  ],
};

function stableHash(input: string): number {
  let hash = 0;
  for (let i = 0; i < input.length; i++) {
    hash = ((hash << 5) - hash + input.charCodeAt(i)) | 0;
  }
  return Math.abs(hash);
}

export function inferProductCategory(title?: string | null, explicitCategory?: string | null): string {
  const cleanCat = (explicitCategory ?? '').trim();
  if (cleanCat && CATEGORY_FALLBACK_POOL[cleanCat]) {
    return cleanCat;
  }
  const t = (title ?? '').toLowerCase();
  if (/headphone|earbud|airpod|bose|sonos|sennheiser|jbl|speaker|wh-1000/.test(t)) return 'Audio';
  if (/iphone|samsung|galaxy|pixel|ipad|tablet|oneplus|phone/.test(t)) return 'Phones & Tablets';
  if (/macbook|laptop|dell|xps|asus|monitor|mac mini|display/.test(t)) return 'Laptops';
  if (/ps5|playstation|switch|xbox|steam deck|quest|dualsense|gaming/.test(t)) return 'Gaming';
  if (/nike|jordan|adidas|sneaker|shoe|belt bag|ray-ban|sunglass|bag/.test(t)) return 'Sneakers & Fashion';
  if (/dyson|nespresso|ninja|breville|kitchenaid|coffee|espresso|vacuum|blender|mixer/.test(t)) return 'Home & Kitchen';
  if (/watch|garmin|seiko|tissot|rolex|whoop|fenix/.test(t)) return 'Watches';
  if (/camera|dji|gopro|fujifilm|stream deck|sony zv/.test(t)) return 'Cameras';
  return 'Laptops';
}

export function getCategoryStudioFallback(category?: string | null, title?: string | null): string {
  const cat = inferProductCategory(title, category);
  const pool = CATEGORY_FALLBACK_POOL[cat] ?? CATEGORY_FALLBACK_POOL.Laptops;
  const idx = stableHash(`${cat}:${title ?? ''}`) % pool.length;
  return pool[idx];
}

export function resolveProductImageSrc(
  imageUrl?: string | null,
  category?: string | null,
  title?: string | null,
): string {
  const trimmed = (imageUrl ?? '').trim();
  if (trimmed.startsWith('/')) {
    return `${getApiBaseUrl()}${trimmed}`;
  }
  if (trimmed.startsWith('https://') || trimmed.startsWith('http://')) {
    return trimmed;
  }
  return getCategoryStudioFallback(category, title);
}

export function handleProductImageError(
  event: SyntheticEvent<HTMLImageElement>,
  category?: string | null,
  title?: string | null,
): void {
  const img = event.currentTarget;
  const stage = img.dataset.fallbackStage ?? '';

  if (stage === '') {
    const resolvedCategory = inferProductCategory(title, category);
    const studioUrl = getCategoryStudioFallback(resolvedCategory, title);
    if (img.src !== studioUrl) {
      img.dataset.fallbackStage = 'studio';
      img.src = studioUrl;
      return;
    }
  }

  if (stage !== 'svg') {
    img.dataset.fallbackStage = 'svg';
    const catParam = encodeURIComponent(inferProductCategory(title, category));
    const titleParam = encodeURIComponent(title ?? 'Product');
    img.src = `${getApiBaseUrl()}/api/catalog/thumbnail?category=${catParam}&title=${titleParam}`;
  }
}

