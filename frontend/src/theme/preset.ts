import Aura from '@primeuix/themes/aura'
import { definePreset } from '@primeuix/themes'

// Decision 1.14: the one PrimeVue preset. Values point at the Tailwind @theme variables in
// src/style.css, so the tokens live in one place.
export default definePreset(Aura, {
  semantic: {
    // Full scale from the one brand green: lighter shades mix in white, darker shades mix in black.
    primary: {
      50: 'color-mix(in srgb, var(--color-brand-green) 8%, white)',
      100: 'color-mix(in srgb, var(--color-brand-green) 16%, white)',
      200: 'color-mix(in srgb, var(--color-brand-green) 32%, white)',
      300: 'color-mix(in srgb, var(--color-brand-green) 50%, white)',
      400: 'color-mix(in srgb, var(--color-brand-green) 75%, white)',
      500: 'var(--color-brand-green)',
      600: 'color-mix(in srgb, var(--color-brand-green) 85%, black)',
      700: 'color-mix(in srgb, var(--color-brand-green) 70%, black)',
      800: 'color-mix(in srgb, var(--color-brand-green) 55%, black)',
      900: 'color-mix(in srgb, var(--color-brand-green) 40%, black)',
      950: 'color-mix(in srgb, var(--color-brand-green) 25%, black)',
    },
    borderRadius: {
      none: '0',
      xs: '2px',
      sm: '4px',
      md: 'var(--radius-md)',
      lg: 'var(--radius-lg)',
      xl: 'var(--radius-lg)',
    },
    colorScheme: {
      light: {
        primary: {
          color: 'var(--color-brand-green)',
          contrastColor: 'var(--color-on-green)',
          hoverColor: '{primary.600}',
          activeColor: '{primary.700}',
        },
        surface: {
          0: 'var(--color-surface)',
          50: 'var(--color-surface-sunken)',
        },
        text: {
          color: 'var(--color-ink)',
          mutedColor: 'var(--color-ink-muted)',
        },
      },
    },
  },
})
