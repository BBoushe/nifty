import Aura from '@primeuix/themes/aura'
import { definePreset } from '@primeuix/themes'

// Decision 1.14: the one PrimeVue preset. Values point at the Tailwind @theme variables in
// src/style.css, so the tokens live in one place.
export default definePreset(Aura, {
  semantic: {
    primary: {
      50: 'var(--color-green-soft)',
      500: 'var(--color-brand-green)',
      600: 'var(--color-brand-green)',
      700: 'var(--color-brand-green)',
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
          hoverColor: 'var(--color-brand-green)',
          activeColor: 'var(--color-brand-green)',
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
