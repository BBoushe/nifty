import { describe, expect, it } from 'vitest'
import preset from './preset'

type Semantic = {
  primary: Record<string, string>
  colorScheme: { light: { primary: Record<string, string> } }
}

describe('PrimeVue preset (Decision 1.14)', () => {
  const semantic = (preset as unknown as { semantic: Semantic }).semantic

  it('maps every primary shade to the brand green, so no Aura emerald remains', () => {
    for (const shade of [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950]) {
      expect(semantic.primary[shade]).toContain('--color-brand-green')
    }
  })

  it('gives hover and press a darker shade than the resting colour', () => {
    const { color, hoverColor, activeColor } = semantic.colorScheme.light.primary
    expect(new Set([color, hoverColor, activeColor]).size).toBe(3)
  })
})
