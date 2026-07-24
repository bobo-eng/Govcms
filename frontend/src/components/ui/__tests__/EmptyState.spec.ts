import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EmptyState from '../EmptyState.vue'

describe('EmptyState', () => {
  it('renders title and description', () => {
    const wrapper = mount(EmptyState, {
      props: { title: '暂无数据', description: '请稍后重试' }
    })
    expect(wrapper.text()).toContain('暂无数据')
    expect(wrapper.text()).toContain('请稍后重试')
  })

  it('renders action button when actionText and actionTo provided', () => {
    const wrapper = mount(EmptyState, {
      props: { title: 'X', actionText: '去创建', actionTo: '/content' }
    })
    expect(wrapper.find('button').exists()).toBe(true)
    expect(wrapper.text()).toContain('去创建')
  })

  it('does not render action button without actionTo', () => {
    const wrapper = mount(EmptyState, {
      props: { title: 'X', actionText: '去创建' }
    })
    expect(wrapper.find('button').exists()).toBe(false)
  })
})
