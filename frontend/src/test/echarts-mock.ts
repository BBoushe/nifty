import { defineComponent, h } from 'vue'
import { vi } from 'vitest'

// Decision 1.14: jsdom has no canvas, so component tests render a stub instead of a real chart.
// Loaded for every test through `setupFiles` in vite.config.ts.
vi.mock('vue-echarts', () => ({
  default: defineComponent({
    name: 'VChart',
    props: { option: { type: Object, default: () => ({}) } },
    setup: (props) => () => h('div', { 'data-testid': 'chart', 'data-option': JSON.stringify(props.option) }),
  }),
}))
