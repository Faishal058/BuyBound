import { useEffect, useId, useRef, useState } from 'react';
import type { FormEvent, MouseEvent as ReactMouseEvent, ReactElement } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useOptionalAuth } from '../../../context/authState';
import { gsap, ScrollTrigger, useGSAP } from '../../../lib/gsap';

interface ProductFind {
  id: string;
  name: string;
  category: 'all' | 'audio' | 'laptops' | 'gaming';
  merchant: string;
  listPrice: string;
  dealPrice: string;
  targetPrice: string;
  discount: string;
  image: string;
  fallbackImage: string;
  status: 'ALLOW' | 'HOLD' | 'CAPTURED';
  statusLabel: string;
  statusTone: 'success' | 'warning';
  checkedAgo: string;
  limitCap: string;
  ruleSummary: string;
  capProgress: string;
  bottomLabel: string;
  bottomTone: 'success' | 'warning' | 'neutral';
}

const FEATURED_FINDS: ProductFind[] = [
  {
    id: 'find-macbook',
    name: 'Apple MacBook Air 15" M3 (512GB)',
    category: 'laptops',
    merchant: 'Best Buy',
    listPrice: '$1,499.00',
    dealPrice: '$1,249.00',
    targetPrice: '$1,300.00',
    discount: 'Save $250',
    image: 'https://cdn.trychannel3.com/c938c0ec-d365-4be9-a116-8a4f9b88b34c?aid=01M767CTNPSR8MFD3D93GCXYFX&mid=DoAg',
    fallbackImage: 'https://cdn.trychannel3.com/ef47f9c7-900c-46e3-96bd-0b2559bc3425?aid=01M767CVQ32EZA6423NXC8Q434&mid=p1PB',
    status: 'CAPTURED',
    statusLabel: 'Bought with PayPal',
    statusTone: 'success',
    checkedAgo: '4m ago',
    limitCap: '$1,500 Max',
    ruleSummary: 'Trusted: Best Buy, B&H Photo',
    capProgress: '83%',
    bottomLabel: 'Safety Rules Passed · Saved $250',
    bottomTone: 'success',
  },
  {
    id: 'find-ps5',
    name: 'Sony PlayStation 5 Slim (1TB)',
    category: 'gaming',
    merchant: 'Target',
    listPrice: '$499.99',
    dealPrice: '$429.99',
    targetPrice: '$440.00',
    discount: 'Save $70',
    image: 'https://cdn.trychannel3.com/cleaned/v1/ad4afa8c-5448-4e35-bc4c-46dd78587a2f?aid=01M767D0V52QZBZZB4H7AZ5H6S&mid=hl1t',
    fallbackImage: 'https://cdn.trychannel3.com/c88dd36d-ee96-4da0-aad6-840b6261c899?aid=01M767D1YRYYT8EVYG3WSTFW53&mid=Qoqa',
    status: 'HOLD',
    statusLabel: 'Needs Your Approval',
    statusTone: 'warning',
    checkedAgo: '9m ago',
    limitCap: '$450 Max',
    ruleSummary: 'Review above $400 threshold',
    capProgress: '96%',
    bottomLabel: 'Paused Before Charging PayPal',
    bottomTone: 'warning',
  },
  {
    id: 'find-sony',
    name: 'Sony WH-1000XM5 ANC Headphones',
    category: 'audio',
    merchant: 'Best Buy',
    listPrice: '$399.99',
    dealPrice: '$279.00',
    targetPrice: '$290.00',
    discount: 'Save $121',
    image: 'https://cdn.trychannel3.com/cleaned/v1/a3b8b014-b3b2-40be-b363-b3402bb7e855?aid=01M767ET0P8FHYV5FQ3XX7P7HB&mid=oOY7',
    fallbackImage: 'https://cdn.trychannel3.com/cleaned/v1/c6a01953-ca4a-47ee-8b6d-70e93d8210c8?aid=01M767CC7FZ9YZ2E45DBFRJD4W&mid=BsaX',
    status: 'CAPTURED',
    statusLabel: 'Purchased',
    statusTone: 'success',
    checkedAgo: '18m ago',
    limitCap: '$300 Max',
    ruleSummary: 'PayPal Receipt Verified',
    capProgress: '93%',
    bottomLabel: 'All Spending Limits Passed',
    bottomTone: 'neutral',
  },
];

const WORK_STEPS = [
  {
    num: '01',
    title: 'Shop Any Product or Set Price Alerts',
    desc: 'Browse 44+ top products across 8 categories or search for literally any product. Buy the lowest verified price today or set a target drop alert.',
    detailLabel: 'Coverage',
    detailValue: '8 Categories + Live Search',
    tone: 'coral' as const,
  },
  {
    num: '02',
    title: 'Connect PayPal & Custom Spending Caps',
    desc: 'Set your per-item limit, weekly budget, and approved stores once — update any limit anytime or pause all buying with one tap.',
    detailLabel: 'Control',
    detailValue: 'Editable Limits + Kill Switch',
    tone: 'coral' as const,
  },
  {
    num: '03',
    title: 'Safe Instant Checkout or Auto-Buy',
    desc: 'Every listing passes scam, accessory-bait, and subscription checks before PayPal pays, generating a receipt proving your real savings.',
    detailLabel: 'Checkout',
    detailValue: 'Verified Savings Receipt',
    tone: 'green' as const,
  },
];

type CheckState = 'pass' | 'hold' | 'fail';

interface DemoCheck {
  code: string;
  label: string;
  value: string;
  state: CheckState;
}

interface DemoScenario {
  key: 'allow' | 'hold' | 'block';
  decision: 'Approved' | 'On Hold' | 'Blocked';
  offer: string;
  reason: string;
  summary: string;
  checks: DemoCheck[];
}

const DEMO_SCENARIOS: DemoScenario[] = [
  {
    key: 'allow',
    decision: 'Approved',
    offer: 'Sony WH-1000XM5 · Best Buy · $239.00',
    reason: 'All 5 Safety Checks Passed · Saved $61.00',
    summary: 'Every spending limit and product check passed, so BuyBound checked out cleanly with PayPal.',
    checks: [
      { code: '01', label: 'Automatic buying switch', value: 'Active', state: 'pass' },
      { code: '02', label: 'Trusted store allowlist', value: 'Best Buy', state: 'pass' },
      { code: '03', label: 'Per-item price cap', value: '$239.00 ≤ $260.00', state: 'pass' },
      { code: '04', label: 'Exact product check', value: 'Verified headphones', state: 'pass' },
      { code: '05', label: 'Hidden subscription check', value: 'None detected', state: 'pass' },
      { code: '06', label: 'Weekly budget left', value: '$261.00 of $500.00', state: 'pass' },
    ],
  },
  {
    key: 'hold',
    decision: 'On Hold',
    offer: 'Sony WH-1000XM5 · Flash Outlet · $215.00',
    reason: 'Paused for Your Review · $0.00 Charged',
    summary: 'The store is not on your trusted list. BuyBound holds the deal so you can approve or cancel with one tap.',
    checks: [
      { code: '01', label: 'Automatic buying switch', value: 'Active', state: 'pass' },
      { code: '02', label: 'Trusted store allowlist', value: 'Unlisted store', state: 'hold' },
      { code: '03', label: 'Per-item price cap', value: '$215.00 ≤ $260.00', state: 'pass' },
      { code: '04', label: 'Exact product check', value: 'Verified headphones', state: 'pass' },
      { code: '05', label: 'Hidden subscription check', value: 'None detected', state: 'pass' },
      { code: '06', label: 'PayPal action', value: 'Waiting on your OK', state: 'hold' },
    ],
  },
  {
    key: 'block',
    decision: 'Blocked',
    offer: 'WH-1000XM5 Replacement Ear Pads (+$29/mo VIP) · $49.00',
    reason: 'Blocked Before PayPal · $0.00 Charged',
    summary: 'Stopped immediately: listing was ear-pad accessory bait paired with a sneaky $29/mo subscription fee.',
    checks: [
      { code: '01', label: 'Exact product check', value: 'Wrong item (ear pads)', state: 'fail' },
      { code: '02', label: 'Hidden subscription check', value: '$29/mo club trap', state: 'fail' },
      { code: '03', label: 'Trusted store allowlist', value: 'Unverified seller', state: 'fail' },
      { code: '04', label: 'Weekly budget impact', value: '$0.00 touched', state: 'pass' },
    ],
  },
];

const CHECK_TONE: Record<CheckState, { text: string; badge: string; icon: ReactElement }> = {
  pass: {
    text: 'text-[#0E7044]',
    badge: 'bg-[#E8F7EE]',
    icon: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M5 13l4 4L19 7" />,
  },
  hold: {
    text: 'text-[#B54708]',
    badge: 'bg-[#FFF4E5]',
    icon: (
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 8v4l2.5 2.5M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    ),
  },
  fail: {
    text: 'text-[#C02619]',
    badge: 'bg-[#FEECEB]',
    icon: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" />,
  },
};

const DECISION_TONE: Record<DemoScenario['decision'], { box: string; dot: string; label: string }> = {
  Approved: { box: 'bg-[#E8F7EE] text-[#0E7044] border-[#0E7044]/25', dot: 'bg-[#0E7044]', label: 'Bought safely' },
  'On Hold': { box: 'bg-[#FFF4E5] text-[#B54708] border-[#B54708]/30', dot: 'bg-[#B54708]', label: 'Needs your approval' },
  Blocked: { box: 'bg-[#FEECEB] text-[#C02619] border-[#C02619]/30', dot: 'bg-[#C02619]', label: 'Blocked before payment' },
};

function useReducedMotion() {
  const [reduced, setReduced] = useState(
    () => typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  );

  useEffect(() => {
    const mq = window.matchMedia('(prefers-reduced-motion: reduce)');
    const onChange = () => setReduced(mq.matches);
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);

  return reduced;
}

function SafetyRulesDemo() {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const [inView, setInView] = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);
  const reduced = useReducedMotion();
  const scenario = DEMO_SCENARIOS[index];
  const tone = DECISION_TONE[scenario.decision];

  useEffect(() => {
    const node = panelRef.current;
    if (!node || typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver(
      (entries) => entries.forEach((entry) => setInView(entry.isIntersecting)),
      { threshold: 0.35 },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (reduced || paused || !inView) return undefined;
    const id = window.setInterval(() => {
      setIndex((current) => (current + 1) % DEMO_SCENARIOS.length);
    }, 4800);
    return () => window.clearInterval(id);
  }, [reduced, paused, inView]);

  // Animation 4: Staggered GSAP timeline on scenario changes
  useGSAP(
    () => {
      if (reduced) return;
      const tl = gsap.timeline({ defaults: { ease: 'power2.out' } });
      tl.from('[data-demo-offer]', { y: 10, autoAlpha: 0, duration: 0.3 })
        .from('[data-demo-check]', { y: 8, autoAlpha: 0, duration: 0.28, stagger: 0.06 }, '-=0.05')
        .from('[data-demo-decision]', { y: 10, autoAlpha: 0, duration: 0.4, ease: 'back.out(1.5)' }, '-=0.05');
    },
    { scope: panelRef, dependencies: [index, reduced], revertOnUpdate: true },
  );

  return (
    <div ref={panelRef} className="rounded-2xl border border-brand-border bg-white shadow-custom-lg overflow-hidden">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 px-5 sm:px-6 py-4 border-b border-brand-border bg-brand-ivory/70">
        <div className="flex items-center gap-2.5">
          <span className="w-7 h-7 rounded-lg bg-brand-peach text-brand-coral flex items-center justify-center">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="2.2"
                d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"
              />
            </svg>
          </span>
          <span className="font-display font-bold text-sm sm:text-base text-brand-charcoal">
            Live Spending Safety Check
          </span>
        </div>

        <button
          type="button"
          onClick={() => setPaused((p) => !p)}
          className="rounded-lg border border-brand-border bg-white px-2.5 py-1 text-[11px] font-semibold text-brand-charcoal-muted hover:text-brand-charcoal"
        >
          {paused ? 'Play preview' : 'Pause preview'}
        </button>
      </div>

      {/* Listing under check */}
      <div className="px-5 sm:px-6 pt-5" data-demo-offer>
        <p className="text-[11px] font-semibold uppercase tracking-wider text-brand-charcoal-muted mb-1.5">
          Store offer found
        </p>
        <p className="font-mono text-sm sm:text-[15px] text-brand-charcoal bg-brand-ivory border border-brand-border rounded-lg px-3 py-2.5 break-words">
          {scenario.offer}
        </p>
      </div>

      {/* Rule checks */}
      <ul className="px-5 sm:px-6 py-4 space-y-0.5">
        {scenario.checks.map((check) => {
          const toneStyle = CHECK_TONE[check.state];
          return (
            <li
              key={check.code}
              data-demo-check
              className="flex items-center justify-between gap-3 py-2 border-b border-brand-border/60 last:border-b-0"
            >
              <span className="flex items-center gap-2.5 min-w-0">
                <span className={`w-5 h-5 rounded-md ${toneStyle.badge} ${toneStyle.text} flex items-center justify-center shrink-0`}>
                  <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                    {toneStyle.icon}
                  </svg>
                </span>
                <span className="text-sm text-brand-charcoal truncate">{check.label}</span>
              </span>
              <span className={`font-mono text-xs sm:text-[13px] font-semibold ${toneStyle.text} text-right shrink-0`}>
                {check.value}
              </span>
            </li>
          );
        })}
      </ul>

      {/* Decision */}
      <div className="px-5 sm:px-6 pb-5" data-demo-decision>
        <div className={`rounded-xl border ${tone.box} px-4 py-3.5 flex flex-wrap items-center justify-between gap-3`}>
          <div className="flex items-center gap-2.5">
            <span className={`w-2.5 h-2.5 rounded-full ${tone.dot}`} aria-hidden="true" />
            <span className="font-display font-extrabold text-xl sm:text-2xl tracking-tight">
              {scenario.decision}
            </span>
            <span className="text-xs font-semibold opacity-85 hidden sm:inline">{tone.label}</span>
          </div>
          <span className="font-mono text-[11px] font-semibold">{scenario.reason}</span>
        </div>
        <p className="text-xs text-brand-charcoal-muted leading-relaxed mt-3">{scenario.summary}</p>
      </div>

      {/* Scenario switcher */}
      <div className="px-5 sm:px-6 py-4 border-t border-brand-border flex flex-wrap items-center justify-between gap-3 bg-brand-ivory/50">
        <div className="flex items-center gap-2" role="group" aria-label="Safety rule check examples">
          {DEMO_SCENARIOS.map((item, i) => (
            <button
              key={item.key}
              type="button"
              onClick={() => setIndex(i)}
              aria-pressed={i === index}
              className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition-colors ${
                i === index
                  ? 'bg-brand-charcoal text-white border-brand-charcoal'
                  : 'bg-white text-brand-charcoal-muted border-brand-border hover:border-brand-coral/50 hover:text-brand-charcoal'
              }`}
            >
              {item.decision}
            </button>
          ))}
        </div>
        <span className="text-[11px] font-mono text-brand-charcoal-muted">
          {reduced ? 'Reduced motion active' : paused ? 'Paused' : 'Cycling examples'}
        </span>
      </div>
    </div>
  );
}

// Animation 3: GSAP hover-lift ProductCard
function ProductCard({ item }: { item: ProductFind }) {
  const cardRef = useRef<HTMLDivElement>(null);
  const liftRef = useRef<gsap.core.Tween | null>(null);

  useGSAP(
    () => {
      const node = cardRef.current;
      if (!node) return;
      const mm = gsap.matchMedia();
      mm.add('(prefers-reduced-motion: no-preference)', () => {
        const lift = gsap.to(node, { y: -6, duration: 0.28, ease: 'power2.out', paused: true });
        liftRef.current = lift;
        return () => {
          lift.kill();
          liftRef.current = null;
        };
      });
      return () => mm.revert();
    },
    { scope: cardRef },
  );

  return (
    <article
      ref={cardRef}
      data-product-card
      onMouseEnter={() => liftRef.current?.play()}
      onMouseLeave={() => liftRef.current?.reverse()}
      className="group bg-white rounded-2xl p-5 sm:p-6 border border-brand-border hover:border-brand-coral/40 hover:shadow-custom-lg transition-[box-shadow,border-color] duration-300 flex flex-col h-full"
    >
      <div className="space-y-4 flex-1">
        <div className="relative bg-brand-ivory rounded-xl overflow-hidden aspect-[4/3] flex items-center justify-center p-4">
          <span
            className={`absolute top-3 left-3 z-10 text-[11px] font-bold px-2.5 py-0.5 rounded-full border flex items-center gap-1.5 ${
              item.statusTone === 'success'
                ? 'bg-[#E8F7EE] text-[#0E7044] border-[#0E7044]/20'
                : 'bg-[#FFF4E5] text-[#B54708] border-[#B54708]/20'
            }`}
          >
            <span className={`w-1.5 h-1.5 rounded-full ${item.statusTone === 'success' ? 'bg-[#0E7044]' : 'bg-[#B54708]'}`} />
            {item.statusLabel}
          </span>
          <span className="absolute top-3 right-3 z-10 bg-white/95 text-[11px] font-mono font-semibold text-brand-charcoal px-2 py-0.5 rounded border border-brand-border">
            {item.discount}
          </span>
          <img
            src={item.image}
            onError={(e) => {
              (e.target as HTMLImageElement).src = item.fallbackImage;
            }}
            alt={item.name}
            className="w-full h-full object-contain mix-blend-multiply group-hover:scale-[1.04] transition-transform duration-300"
            loading="lazy"
          />
        </div>

        <div className="space-y-2">
          <div className="flex items-center justify-between text-xs text-brand-charcoal-muted gap-3">
            <span className="truncate font-medium">{item.merchant}</span>
            <span className="font-mono shrink-0">{item.checkedAgo}</span>
          </div>
          <h3 className="font-display font-bold text-lg lg:text-xl text-brand-charcoal leading-snug">{item.name}</h3>
          <div className="flex flex-wrap items-baseline gap-x-2.5 gap-y-1">
            <span className="font-mono font-bold text-2xl text-brand-charcoal">{item.dealPrice}</span>
            <span className="text-sm text-brand-charcoal-muted line-through font-mono">{item.listPrice}</span>
            <span className="text-xs font-semibold text-[#0E7044] bg-[#E8F7EE] px-2 py-0.5 rounded">
              Target: {item.targetPrice}
            </span>
          </div>
        </div>

        <div className="p-3 rounded-lg bg-brand-ivory border border-brand-border text-xs space-y-1.5">
          <div className="flex items-center justify-between gap-3 font-mono text-[11px]">
            <span className="text-brand-charcoal-muted">Spending cap</span>
            <span className="text-brand-charcoal font-semibold">{item.limitCap}</span>
          </div>
          <div className="flex items-center justify-between gap-3 font-mono text-[11px]">
            <span className="text-brand-charcoal-muted shrink-0">Rule status</span>
            <span className="text-brand-charcoal text-right">{item.ruleSummary}</span>
          </div>
          <div className="w-full bg-brand-border h-1.5 rounded-full overflow-hidden mt-1">
            <div
              className={`h-full rounded-full ${item.statusTone === 'success' ? 'bg-[#0E7044]' : 'bg-[#B54708]'}`}
              style={{ width: item.capProgress }}
            />
          </div>
        </div>
      </div>

      <div className="pt-4 mt-4 border-t border-brand-border flex items-center justify-between gap-3">
        <span
          className={`text-xs font-semibold flex items-center gap-1.5 ${
            item.bottomTone === 'success'
              ? 'text-[#0E7044]'
              : item.bottomTone === 'warning'
                ? 'text-[#B54708]'
                : 'text-brand-charcoal-muted font-mono'
          }`}
        >
          {item.bottomTone === 'success' && (
            <svg className="w-3.5 h-3.5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 13l4 4L19 7" />
            </svg>
          )}
          {item.bottomTone === 'warning' && (
            <svg className="w-3.5 h-3.5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="2"
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
              />
            </svg>
          )}
          {item.bottomLabel}
        </span>
        <Link
          to={`/app/shop?q=${encodeURIComponent(item.name)}`}
          className="text-xs font-semibold text-brand-coral hover:text-brand-coral-hover flex items-center gap-1 shrink-0"
        >
          Compare deal
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7" />
          </svg>
        </Link>
      </div>
    </article>
  );
}

export function LandingPage() {
  const navigate = useNavigate();
  const auth = useOptionalAuth();
  const rootRef = useRef<HTMLDivElement>(null);
  const heroSearchId = useId();
  const [heroSearch, setHeroSearch] = useState('');
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState<'all' | 'laptops' | 'audio' | 'gaming'>('all');

  const handleHeroSearchSubmit = (e: FormEvent) => {
    e.preventDefault();
    const q = heroSearch.trim();
    navigate(q ? `/app/shop?q=${encodeURIComponent(q)}` : '/app/shop');
  };

  const scrollToSection = (id: string) => (e: ReactMouseEvent) => {
    e.preventDefault();
    setMobileMenuOpen(false);
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' });
  };

  // Animations 1 & 2: Hero Timeline Entrance + ScrollTrigger Section Reveals
  useGSAP(
    () => {
      const mm = gsap.matchMedia();

      mm.add(
        {
          reduceMotion: '(prefers-reduced-motion: reduce)',
          allowMotion: '(prefers-reduced-motion: no-preference)',
        },
        (context) => {
          if (context.conditions?.reduceMotion) return;

          // Hero entrance timeline
          gsap
            .timeline({ defaults: { ease: 'power3.out' } })
            .from('[data-hero-eyebrow]', { y: 14, autoAlpha: 0, duration: 0.4 })
            .from('[data-hero-title]', { y: 22, autoAlpha: 0, duration: 0.55 }, '-=0.2')
            .from('[data-hero-copy]', { y: 16, autoAlpha: 0, duration: 0.45 }, '-=0.32')
            .from('[data-hero-actions] > *', { y: 14, autoAlpha: 0, duration: 0.4, stagger: 0.08 }, '-=0.28')
            .from('[data-hero-trust] > *', { y: 8, autoAlpha: 0, duration: 0.35, stagger: 0.05 }, '-=0.24')
            .from('[data-hero-preview]', { y: 26, autoAlpha: 0, duration: 0.6 }, '-=0.7');

          // Scroll-triggered section reveals
          gsap.utils.toArray<HTMLElement>('[data-reveal]').forEach((section) => {
            const items = section.querySelectorAll('[data-reveal-item]');
            gsap.from(items.length > 0 ? Array.from(items) : section, {
              y: 20,
              autoAlpha: 0,
              duration: 0.5,
              stagger: 0.09,
              ease: 'power2.out',
              scrollTrigger: { trigger: section, start: 'top 88%', once: true },
            });
          });
        },
      );

      if (typeof document !== 'undefined' && document.fonts) {
        document.fonts.ready
          .then(() => ScrollTrigger.refresh())
          .catch(() => undefined);
      }

      return () => mm.revert();
    },
    { scope: rootRef },
  );

  const filteredFinds =
    selectedCategory === 'all' ? FEATURED_FINDS : FEATURED_FINDS.filter((item) => item.category === selectedCategory);

  return (
    <div ref={rootRef} className="bg-brand-ivory font-sans antialiased text-brand-charcoal min-h-screen selection:bg-brand-peach selection:text-brand-coral">
      <a href="#landing-main" className="skip-link visually-hidden">
        Skip to main content
      </a>

      {/* Top Navbar */}
      <header className="sticky top-0 z-50 w-full bg-brand-ivory/95 backdrop-blur-md border-b border-brand-border">
        <div className="page-shell h-16 flex items-center justify-between gap-6">
          <Link to="/" className="flex items-center gap-2.5 group shrink-0" aria-label="BuyBound Home">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-brand-coral to-brand-coral-hover flex items-center justify-center shadow-sm group-hover:scale-105 transition-transform">
              <svg className="w-5 h-5 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z" />
                <path d="M3 6h18" />
                <path d="M16 10a4 4 0 0 1-8 0" />
              </svg>
            </div>
            <span className="font-display font-bold text-xl tracking-tight text-brand-charcoal">
              BuyBound<span className="text-brand-coral">.</span>
            </span>
          </Link>

          <div className="hidden md:flex items-center gap-8">
            <nav className="flex items-center gap-7 text-sm font-medium text-brand-charcoal-muted" aria-label="Primary">
              <Link to="/app/shop" className="hover:text-brand-coral transition-colors">
                Shop Products
              </Link>
              <a href="#how-it-works" onClick={scrollToSection('how-it-works')} className="hover:text-brand-coral transition-colors">
                How It Works
              </a>
              <a href="#safety-rules" onClick={scrollToSection('safety-rules')} className="hover:text-brand-coral transition-colors">
                Safety Rules
              </a>
              <a href="#live-pricing" onClick={scrollToSection('live-pricing')} className="hover:text-brand-coral transition-colors">
                Live Pricing
              </a>
            </nav>
            <div className="flex items-center gap-3">
              <Link
                to={auth?.isAuthenticated ? '/app/shop' : '/login'}
                className="text-sm font-semibold text-white bg-brand-coral hover:bg-brand-coral-hover px-5 py-2 rounded-full shadow-sm transition-colors"
              >
                Start Shopping
              </Link>
            </div>
          </div>

          <div className="flex items-center gap-2 md:hidden">
            <Link
              to={auth?.isAuthenticated ? '/app/shop' : '/login'}
              className="text-xs font-semibold text-white bg-brand-coral px-3.5 py-2 rounded-full"
            >
              Start Shopping
            </Link>
            <button
              type="button"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              aria-label="Toggle navigation menu"
              aria-expanded={mobileMenuOpen}
              className="p-2 rounded-xl text-brand-charcoal hover:bg-brand-peach/50 transition-colors"
            >
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                {mobileMenuOpen ? (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12" />
                ) : (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 6h16M4 12h16m-7 6h7" />
                )}
              </svg>
            </button>
          </div>
        </div>

        {mobileMenuOpen && (
          <div className="md:hidden bg-brand-ivory px-5 pt-2 pb-5 border-t border-brand-border/70">
            <nav className="flex flex-col font-medium text-brand-charcoal-muted" aria-label="Mobile">
              <Link to="/app/shop" onClick={() => setMobileMenuOpen(false)} className="py-3 hover:text-brand-coral">
                Shop Products
              </Link>
              <a href="#how-it-works" onClick={scrollToSection('how-it-works')} className="py-3 hover:text-brand-coral">
                How It Works
              </a>
              <a href="#safety-rules" onClick={scrollToSection('safety-rules')} className="py-3 hover:text-brand-coral">
                Safety Rules
              </a>
              <a href="#live-pricing" onClick={scrollToSection('live-pricing')} className="py-3 hover:text-brand-coral">
                Live Pricing
              </a>
            </nav>
            <div className="pt-3 flex flex-col gap-2">
              <Link
                to={auth?.isAuthenticated ? '/app/shop' : '/login'}
                onClick={() => setMobileMenuOpen(false)}
                className="text-center py-3 text-sm font-semibold text-white bg-brand-coral rounded-xl"
              >
                Start Shopping
              </Link>
            </div>
          </div>
        )}
      </header>

      <main id="landing-main" className="w-full">
        {/* Hero Section */}
        <section className="section-pad">
          <div className="page-shell">
            <div className="grid grid-cols-1 lg:grid-cols-[1.08fr_1fr] gap-10 lg:gap-14 xl:gap-16 items-center">
              <div className="max-w-2xl space-y-5">
                <p
                  data-hero-eyebrow
                  className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-brand-peach text-xs font-semibold tracking-wide text-brand-coral w-fit"
                >
                  44+ Curated Products · Unlimited Store Search · Built-In Spending Limits
                </p>

                <h1
                  data-hero-title
                  className="font-display text-4xl/[1.14] sm:text-5xl/[1.1] lg:text-[54px]/[1.08] font-extrabold text-brand-charcoal tracking-[-0.03em]"
                >
                  Compare every store.{' '}
                  <span className="text-brand-coral">Buy instantly or automate price drops—inside your limits.</span>
                </h1>

                <p data-hero-copy className="text-base sm:text-lg text-brand-charcoal-muted leading-relaxed max-w-xl">
                  Search any product across electronics, laptops, gaming, fashion, and home essentials. BuyBound compares
                  competing stores in real time, blocks scam listings, and lets PayPal check out only inside your rules.
                </p>

                <search data-hero-actions className="space-y-3 pt-1">
                  <form onSubmit={handleHeroSearchSubmit} className="flex flex-col sm:flex-row gap-2.5 max-w-xl">
                    <label htmlFor={heroSearchId} className="sr-only">
                      Search any product to compare store prices
                    </label>
                    <input
                      id={heroSearchId}
                      type="search"
                      value={heroSearch}
                      onChange={(e) => setHeroSearch(e.target.value)}
                      placeholder="Search any product (MacBook Air M3, PlayStation 5, Dyson)..."
                      className="flex-1 rounded-full border border-brand-border bg-white px-5 py-3 text-sm text-brand-charcoal shadow-sm placeholder:text-brand-charcoal-faint focus:border-brand-coral focus:outline-none focus:ring-2 focus:ring-brand-coral/20"
                    />
                    <button
                      type="submit"
                      className="inline-flex items-center justify-center gap-2 px-6 py-3 rounded-full bg-brand-charcoal hover:bg-brand-charcoal-light text-white font-semibold text-sm shadow-sm transition-colors shrink-0"
                    >
                      Search Stores
                    </button>
                  </form>

                  <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
                    <Link
                      to="/app/shop"
                      className="inline-flex items-center justify-center gap-2 px-6 py-3.5 rounded-full bg-brand-coral hover:bg-brand-coral-hover text-white font-semibold text-sm shadow-md transition-colors"
                    >
                      <span>Shop All Products</span>
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.2" d="M14 5l7 7m0 0l-7 7m7-7H3" />
                      </svg>
                    </Link>
                    <Link
                      to="/app/watches"
                      className="inline-flex items-center justify-center gap-2 px-6 py-3.5 rounded-full bg-white hover:bg-brand-peach-soft text-brand-charcoal border border-brand-border font-semibold text-sm transition-colors"
                    >
                      <span>Create Price Alert</span>
                    </Link>
                    <a
                      href="#safety-rules"
                      onClick={scrollToSection('safety-rules')}
                      className="inline-flex items-center justify-center gap-1.5 px-4 py-3.5 rounded-full text-brand-charcoal-muted hover:text-brand-coral font-medium text-sm transition-colors"
                    >
                      <span>See Safety Rules</span>
                      <svg className="w-4 h-4 text-brand-charcoal-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
                      </svg>
                    </a>
                  </div>
                </search>

                <div data-hero-trust className="flex flex-wrap items-center gap-y-2.5 gap-x-6 pt-1 text-xs font-medium text-brand-charcoal-muted">
                  {['One-Tap Kill Switch', 'Zero Hidden Subscriptions', 'BuyBound Purchase Guarantee'].map((badge) => (
                    <span key={badge} className="flex items-center gap-1.5">
                      <svg className="w-3.5 h-3.5 text-[#0E7044]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M5 13l4 4L19 7" />
                      </svg>
                      {badge}
                    </span>
                  ))}
                </div>
              </div>

              {/* Hero Interactive Example Preview */}
              <div data-hero-preview className="w-full animate-float-slow">
                <div className="bg-white rounded-2xl p-5 sm:p-6 lg:p-7 border border-brand-border shadow-custom-lg space-y-4">
                  <div className="bg-brand-ivory rounded-xl px-3.5 py-3 border border-brand-border flex items-center gap-3">
                    <span className="w-7 h-7 rounded-full bg-brand-peach flex items-center justify-center shrink-0 text-brand-coral">
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth="2"
                          d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
                        />
                      </svg>
                    </span>
                    <p className="text-sm sm:text-[15px] font-mono text-brand-charcoal">
                      &quot;Sony WH-1000XM5 Target: $250, only trusted stores&quot;
                    </p>
                  </div>

                  <div className="flex items-center justify-between gap-3 px-3.5 py-2.5 rounded-lg bg-brand-peach-soft border border-brand-peach text-xs">
                    <span className="flex items-center gap-2 font-semibold text-brand-coral min-w-0">
                      <span className="inline-block w-2 h-2 rounded-full bg-brand-coral animate-ping shrink-0" aria-hidden="true" />
                      <span className="truncate">Found at Best Buy for $239.00 → Safety Rules Passed</span>
                    </span>
                    <span className="text-brand-charcoal-muted line-through text-[11px] font-mono shrink-0 hidden sm:inline">
                      $300.00
                    </span>
                  </div>

                  <div className="rounded-xl overflow-hidden border border-brand-border bg-brand-ivory">
                    <div className="grid grid-cols-5 items-center">
                      <div className="col-span-2 p-3 flex items-center justify-center">
                        <img
                          src="https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800&auto=format&fit=crop&q=80"
                          alt="Sony WH-1000XM5 headphones"
                          className="w-full h-36 sm:h-44 lg:h-48 object-cover rounded-lg"
                        />
                      </div>
                      <div className="col-span-3 p-4 space-y-1.5">
                        <span className="inline-block text-[10px] font-bold uppercase tracking-wider text-brand-charcoal-muted bg-white px-2 py-0.5 rounded border border-brand-border">
                          Target: $290.00
                        </span>
                        <h2 className="font-display font-bold text-base lg:text-lg text-brand-charcoal leading-tight">
                          Sony WH-1000XM5 ANC
                        </h2>
                        <div className="flex items-baseline gap-2 pt-0.5">
                          <span className="font-mono font-bold text-2xl lg:text-3xl text-brand-charcoal">$279.00</span>
                          <span className="text-xs font-semibold text-[#0E7044] bg-[#E8F7EE] px-2 py-0.5 rounded">
                            Saved $121
                          </span>
                        </div>
                        <p className="text-xs text-brand-charcoal-muted">
                          <strong className="text-brand-charcoal">Best Buy</strong> (Trusted store · 4 stores checked)
                        </p>
                      </div>
                    </div>
                  </div>

                  <div className="rounded-xl p-4 bg-[#E8F7EE]/60 border border-[#0E7044]/25 space-y-2.5">
                    <div className="flex items-center justify-between gap-3 text-xs">
                      <span className="font-semibold text-brand-charcoal flex items-center gap-1.5">
                        <svg className="w-4 h-4 text-[#0E7044]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                        </svg>
                        Bought with PayPal
                      </span>
                      <span className="font-mono text-[11px] font-bold text-[#0E7044] bg-white px-2.5 py-0.5 rounded-full border border-[#0E7044]/25">
                        Saved $120.99
                      </span>
                    </div>
                    <div className="grid grid-cols-3 gap-2 text-xs">
                      <div className="bg-white p-2 rounded-lg border border-brand-border text-center">
                        <span className="text-brand-charcoal-muted block text-[10px]">Item Limit</span>
                        <span className="font-mono font-bold text-brand-charcoal">$300</span>
                      </div>
                      <div className="bg-white p-2 rounded-lg border border-brand-border text-center">
                        <span className="text-brand-charcoal-muted block text-[10px]">Paid</span>
                        <span className="font-mono font-bold text-[#0E7044]">$279</span>
                      </div>
                      <div className="bg-white p-2 rounded-lg border border-brand-border text-center">
                        <span className="text-brand-charcoal-muted block text-[10px]">Safety Rules</span>
                        <span className="font-mono font-bold text-[#0E7044]">6 / 6 Passed</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* 3-Step Visual "How It Works" Section */}
        <section id="how-it-works" className="section-pad scroll-mt-20" data-reveal>
          <div className="page-shell">
            <div className="max-w-2xl space-y-3 mb-12 lg:mb-14" data-reveal-item>
              <p className="text-xs font-bold uppercase tracking-wider text-brand-coral">How It Works</p>
              <h2 className="font-display text-3xl sm:text-4xl font-extrabold text-brand-charcoal tracking-tight">
                From price target to verified PayPal receipt
              </h2>
              <p className="text-base text-brand-charcoal-muted leading-relaxed">
                Three clear steps that give you hands-free savings without giving up control of your wallet.
              </p>
            </div>

            <ol className="grid grid-cols-1 md:grid-cols-3 gap-x-8 gap-y-10">
              {WORK_STEPS.map((step) => (
                <li key={step.num} data-reveal-item className="flex flex-col gap-3 rounded-2xl border border-brand-border bg-white p-6 shadow-custom h-full">
                  <div className="flex items-center gap-3">
                    <span className="font-mono text-xs font-bold text-brand-coral bg-brand-peach px-2.5 py-1 rounded-md">
                      Step {step.num}
                    </span>
                    <span className="h-px flex-1 bg-brand-border" aria-hidden="true" />
                  </div>
                  <h3 className="font-display font-bold text-lg text-brand-charcoal">{step.title}</h3>
                  <p className="text-sm text-brand-charcoal-muted leading-relaxed">{step.desc}</p>
                  <div className="mt-auto pt-3 flex items-center justify-between gap-2 text-[11px] font-mono bg-brand-ivory border border-brand-border rounded-lg px-3 py-2">
                    <span className="text-brand-charcoal-muted">{step.detailLabel}</span>
                    <span
                      className={`font-bold px-2 py-0.5 rounded ${
                        step.tone === 'green' ? 'text-[#0E7044] bg-[#E8F7EE]' : 'text-brand-coral bg-brand-peach-soft'
                      }`}
                    >
                      {step.detailValue}
                    </span>
                  </div>
                </li>
              ))}
            </ol>
          </div>
        </section>

        {/* Live Pricing & Store Comparison Showcase */}
        <section id="live-pricing" className="section-pad scroll-mt-20" data-reveal>
          <div className="page-shell">
            <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-5 mb-8" data-reveal-item>
              <div className="space-y-2.5 max-w-2xl">
                <p className="text-xs font-bold uppercase tracking-wider text-brand-coral">Live Marketplace</p>
                <h2 className="font-display text-3xl sm:text-4xl font-extrabold text-brand-charcoal tracking-tight">
                  44+ real products across 8 categories — verified inside your limits
                </h2>
                <p className="text-sm sm:text-base text-brand-charcoal-muted">
                  Real multi-store price comparisons across Laptops, Audio, Phones, Gaming, Fashion, Home, Watches, and
                  Cameras before PayPal charges a cent.
                </p>
              </div>
              <Link
                to="/app/shop"
                className="inline-flex items-center gap-1.5 text-sm font-semibold text-brand-coral hover:text-brand-coral-hover group shrink-0"
              >
                <span>Browse all 44+ products</span>
                <svg className="w-4 h-4 transition-transform group-hover:translate-x-1" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7" />
                </svg>
              </Link>
            </div>

            <div className="flex flex-wrap items-center gap-2 pb-1 mb-7" data-reveal-item>
              {([
                { key: 'all' as const, label: 'All highlights' },
                { key: 'laptops' as const, label: 'Laptops & monitors' },
                { key: 'gaming' as const, label: 'Gaming consoles' },
                { key: 'audio' as const, label: 'Audio & headphones' },
              ]).map(({ key, label }) => (
                <button
                  key={key}
                  type="button"
                  onClick={() => setSelectedCategory(key)}
                  aria-pressed={selectedCategory === key}
                  className={`px-4 py-2 rounded-full text-xs sm:text-sm font-semibold transition-colors shrink-0 ${
                    selectedCategory === key
                      ? 'bg-brand-charcoal text-white'
                      : 'bg-white text-brand-charcoal-muted hover:text-brand-charcoal border border-brand-border'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5 lg:gap-6" data-reveal-item>
              {filteredFinds.map((item) => (
                <ProductCard key={item.id} item={item} />
              ))}
            </div>
          </div>
        </section>

        {/* Trust & Safety Section */}
        <section id="safety-rules" className="section-pad scroll-mt-20" data-reveal>
          <div className="page-shell grid grid-cols-1 lg:grid-cols-12 gap-10 lg:gap-12 items-start">
            <div className="lg:col-span-5 space-y-5" data-reveal-item>
              <p className="text-xs font-bold uppercase tracking-wider text-brand-coral">Safety Rules</p>
              <h2 className="font-display text-3xl sm:text-4xl font-extrabold text-brand-charcoal tracking-tight leading-tight">
                Why BuyBound never makes a surprise purchase
              </h2>
              <p className="text-base text-brand-charcoal-muted leading-relaxed">
                AI reads listings to match products and spot seller tricks, while hard spending rules hold the final key
                before PayPal can ever process a payment.
              </p>

              <ul className="space-y-3.5 pt-1">
                {[
                  { title: 'One-Tap Kill Switch', desc: 'Pause all active watches and turn off automatic PayPal buying instantly.' },
                  { title: 'Zero Hidden Subscriptions', desc: 'Blocks forced monthly VIP memberships, sneaky fees, and accessory bait.' },
                  { title: 'BuyBound Purchase Guarantee', desc: 'Every PayPal receipt compares what you paid against normal retail.' },
                  { title: 'Trusted Store Allowlist', desc: 'Unknown shops are always paused for your review, never bought automatically.' },
                  { title: 'Weekly & Per-Item Limits', desc: 'Hard ceilings enforced cleanly across every single store check.' },
                ].map((capability) => (
                  <li key={capability.title} className="flex items-start gap-3">
                    <span className="w-5 h-5 rounded-md bg-brand-peach text-brand-coral flex items-center justify-center shrink-0 mt-0.5">
                      <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M5 13l4 4L19 7" />
                      </svg>
                    </span>
                    <span className="text-sm text-brand-charcoal-muted leading-relaxed">
                      <strong className="text-brand-charcoal font-semibold">{capability.title}</strong> — {capability.desc}
                    </span>
                  </li>
                ))}
              </ul>

              <div className="flex flex-wrap items-center gap-x-4 gap-y-2 pt-2">
                <Link
                  to="/app/safety"
                  className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-full bg-brand-charcoal hover:bg-black text-white text-sm font-semibold transition-colors"
                >
                  <span>Test Scam Protection Live →</span>
                </Link>
              </div>
            </div>

            <div className="lg:col-span-7" data-reveal-item>
              <SafetyRulesDemo />
            </div>
          </div>
        </section>

        {/* Bottom CTA */}
        <section className="section-pad" data-reveal>
          <div className="page-shell">
            <div
              data-reveal-item
              className="rounded-3xl bg-brand-peach/60 border border-brand-border px-6 py-12 sm:px-12 sm:py-16 lg:px-16 text-center"
            >
              <div className="max-w-2xl mx-auto space-y-4">
                <h2 className="font-display text-3xl sm:text-4xl lg:text-5xl font-extrabold text-brand-charcoal tracking-tight">
                  Ready for smarter, protected shopping?
                </h2>
                <p className="text-base sm:text-lg text-brand-charcoal-muted">
                  Set your first price watch in seconds, connect PayPal with built-in limits, and never miss a drop
                  again.
                </p>
              </div>

              <div className="flex flex-col sm:flex-row items-center justify-center gap-3 mt-8">
                <Link
                  to="/app/shop"
                  className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-8 py-3.5 rounded-full bg-brand-coral hover:bg-brand-coral-hover text-white font-semibold text-sm shadow-md transition-colors"
                >
                  <span>Explore Marketplace (44+ Deals)</span>
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.2" d="M14 5l7 7m0 0l-7 7m7-7H3" />
                  </svg>
                </Link>
                <Link
                  to="/app/watches"
                  className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-7 py-3.5 rounded-full bg-white hover:bg-brand-ivory text-brand-charcoal border border-brand-border font-semibold text-sm transition-colors"
                >
                  <span>Set a Price Alert</span>
                </Link>
              </div>
            </div>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="bg-white border-t border-brand-border pt-12 pb-8">
        <div className="page-shell">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-8 pb-10">
            <div className="col-span-2 sm:col-span-1 space-y-3">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-brand-coral to-brand-coral-hover flex items-center justify-center shadow-sm">
                  <svg className="w-4 h-4 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z" />
                    <path d="M3 6h18" />
                    <path d="M16 10a4 4 0 0 1-8 0" />
                  </svg>
                </div>
                <span className="font-display font-bold text-lg tracking-tight text-brand-charcoal">
                  BuyBound<span className="text-brand-coral">.</span>
                </span>
              </div>
              <p className="text-sm text-brand-charcoal-muted leading-relaxed">
                Automated price-drop buying with built-in spending limits and PayPal protection.
              </p>
            </div>

            <div className="space-y-3">
              <h3 className="font-display font-bold text-xs uppercase tracking-wider text-brand-charcoal">App</h3>
              <ul className="space-y-2 text-sm text-brand-charcoal-muted">
                <li><Link to="/app/shop" className="hover:text-brand-coral transition-colors">Shop Products</Link></li>
                <li><Link to="/app/watches" className="hover:text-brand-coral transition-colors">Price Alerts</Link></li>
                <li><Link to="/app/offers" className="hover:text-brand-coral transition-colors">Compare Offers</Link></li>
                <li><Link to="/app/purchases" className="hover:text-brand-coral transition-colors">Orders &amp; Receipts</Link></li>
                <li><Link to="/app/safety" className="hover:text-brand-coral transition-colors">Wallet &amp; Safety</Link></li>
              </ul>
            </div>

            <div className="space-y-3">
              <h3 className="font-display font-bold text-xs uppercase tracking-wider text-brand-charcoal">Overview</h3>
              <ul className="space-y-2 text-sm text-brand-charcoal-muted">
                <li><a href="#how-it-works" onClick={scrollToSection('how-it-works')} className="hover:text-brand-coral transition-colors">How It Works</a></li>
                <li><a href="#live-pricing" onClick={scrollToSection('live-pricing')} className="hover:text-brand-coral transition-colors">Live Pricing</a></li>
                <li><a href="#safety-rules" onClick={scrollToSection('safety-rules')} className="hover:text-brand-coral transition-colors">Safety Rules</a></li>
              </ul>
            </div>

            <div className="space-y-3">
              <h3 className="font-display font-bold text-xs uppercase tracking-wider text-brand-charcoal">Trust</h3>
              <ul className="space-y-2 text-sm text-brand-charcoal-muted">
                <li>One-Tap Kill Switch</li>
                <li>PayPal Purchase Protection</li>
                <li>
                  <a href="https://opensource.org/licenses/MIT" target="_blank" rel="noopener noreferrer" className="hover:text-brand-coral transition-colors">
                    MIT license
                  </a>
                </li>
              </ul>
            </div>
          </div>

          <div className="pt-6 border-t border-brand-border flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-brand-charcoal-muted">
            <p>&copy; 2026 BuyBound. Universal Deal Discovery &amp; Safe Automated PayPal Checkout.</p>
            <Link to="/app/shop" className="font-semibold text-brand-coral hover:underline">
              Open Marketplace →
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
