import type { Config } from "tailwindcss";

/**
 * Plantora design tokens.
 *
 * Aesthetic: Apple's calm + Stripe's clarity, scaled up for older eyes & thumbs.
 * - Legibility over delicacy: 17px base, medium+ weights, high contrast.
 * - Obvious affordances: solid buttons, 48px min / 56px primary tap targets.
 * - Calm orange brand system.
 *
 * These tokens are the single source of truth for every later screen.
 */
const config: Config = {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  // The dev-only /_styleguide renders primary shades via dynamic class names
  // (e.g. `bg-primary-600`), which JIT can't see by scanning source. Safelist them.
  safelist: [
    { pattern: /^bg-primary-(50|100|200|300|400|500|600|700|800|900)$/ },
  ],
  theme: {
    extend: {
      colors: {
        // The brand orange. 600 (#F05B01) is the primary action fill; white on it
        // is ~3.4:1, which clears WCAG AA for LARGE text only — so keep white-on-
        // brand labels bold and >=19px, and use 700 for brand-coloured body text
        // sitting on a light background (5.0:1).
        // Previous palettes, to paste back: deep orange 600 #C2410C / 700 #9A3412;
        // botanical green 600 #138A56 / 700 #0E6E44.
        primary: {
          50: "#FFF4EC",
          100: "#FFE3D0",
          200: "#FFC29A",
          300: "#FF9D60",
          400: "#FB7C2B",
          500: "#F05B01", // brand
          600: "#F05B01", // primary action fill
          700: "#C24700", // hover / pressed / active borders / accessible brand text
          800: "#973700",
          900: "#6B2700",
        },
        // Text & surfaces. Near-black ink for primary text (never light gray).
        ink: {
          DEFAULT: "#181A18", // primary text
          soft: "#50544E", // secondary text — still clearly readable
          faint: "#797D74", // tertiary; use sparingly, never for essential text
        },
        surface: {
          DEFAULT: "#FFFFFF", // cards — pure white, pops on warm canvas
          muted: "#F6F5F1", // warm off-white app background
          sunken: "#EFEEE8", // inset wells
        },
        border: {
          DEFAULT: "#E4E5DE", // hairline (used at 1px, not 2px)
          strong: "#D2D4C9", // a real edge where one is needed
        },
        // Semantic
        success: { DEFAULT: "#138A56", soft: "#EAF8F0" },
        danger: { DEFAULT: "#C02626", soft: "#FBEAEA" }, // high-contrast error red
        warning: { DEFAULT: "#B45309", soft: "#FCF1E3" }, // dark amber for contrast
        // Payment accents
        cash: "#138A56",
        upi: "#2F6FB0",
      },
      fontFamily: {
        sans: [
          "Inter",
          "ui-sans-serif",
          "system-ui",
          "-apple-system",
          "Segoe UI",
          "Roboto",
          "Helvetica Neue",
          "Arial",
          "sans-serif",
        ],
      },
      fontSize: {
        // Generous scale; body is 17px. [size, lineHeight]
        xs: ["13px", "1.4"],
        sm: ["15px", "1.5"],
        base: ["17px", "1.55"], // body default
        lg: ["19px", "1.5"],
        xl: ["22px", "1.4"],
        "2xl": ["26px", "1.3"],
        "3xl": ["32px", "1.25"],
        "4xl": ["40px", "1.15"],
        "5xl": ["48px", "1.05"], // takings hero
        "6xl": ["60px", "1.0"], // success total
      },
      letterSpacing: {
        tightest: "-0.03em", // display numbers
      },
      borderRadius: {
        // Consistent radii: controls 12px, cards 18px, hero/summary 24px.
        control: "12px",
        card: "18px",
        hero: "24px",
      },
      boxShadow: {
        // Real, soft, layered elevation — depth, not flat.
        sm: "0 1px 2px rgba(20,30,24,0.05), 0 3px 10px -2px rgba(20,30,24,0.08)",
        card: "0 1px 3px rgba(20,30,24,0.06), 0 10px 26px -8px rgba(20,30,24,0.12)",
        "card-lg": "0 6px 16px rgba(20,30,24,0.09), 0 24px 48px -16px rgba(20,30,24,0.18)",
        // Brand-tinted lift so the primary action feels alive and floating.
        btn: "0 2px 4px rgba(194,71,0,0.18), 0 8px 20px -6px rgba(194,71,0,0.34)",
        focus: "0 0 0 4px rgba(240,91,1,0.25)",
      },
      minHeight: {
        tap: "48px", // minimum interactive height
        action: "56px", // primary action height
      },
      height: {
        tap: "48px",
        action: "56px",
      },
      transitionDuration: {
        gentle: "200ms",
      },
      keyframes: {
        "fade-in": {
          from: { opacity: "0" },
          to: { opacity: "1" },
        },
      },
    },
  },
  plugins: [],
};

export default config;
