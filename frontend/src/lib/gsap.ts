import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { useGSAP } from '@gsap/react';

/**
 * Single registration point for GSAP plugins used by the marketing pages.
 * Import `{ gsap, ScrollTrigger, useGSAP }` from here so registration happens
 * exactly once and every component shares the same plugin instances.
 */
gsap.registerPlugin(ScrollTrigger, useGSAP);

export { gsap, ScrollTrigger, useGSAP };
