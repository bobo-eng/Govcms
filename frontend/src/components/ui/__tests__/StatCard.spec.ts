import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { FileTextOutlined } from '@ant-design/icons-vue'
import StatCard from '../StatCard.vue'

describe('StatCard', () => {
  it('renders title and value', () => {
    const wrapper = mount(StatCard, {
      props: { title: '内容总数', value: 128, icon: FileTextOutlined }
    })
    expect(wrapper.text()).toContain('内容总数')
    expect(wrapper.text()).toContain('128')
  })

  it('renders description when provided', () => {
    const wrapper = mount(StatCard, {
      props: { title: 'X', value: 0, icon: FileTextOutlined, description: '较上周 +12%' }
    })
    expect(wrapper.text()).toContain('较上周 +12%')
  })

  it('applies danger status class', () => {
    const wrapper = mount(StatCard, {
      props: { title: 'X', value: 5, icon: FileTextOutlined, status: 'danger' }
    })
    expect(wrapper.find('.status-danger').exists()).toBe(true)
  })
})
