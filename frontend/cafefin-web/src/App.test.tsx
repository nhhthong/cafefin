import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

// Task 0.8 scaffold: proves Vitest + jsdom + Testing Library render a real
// React component (JSX/TSX pipeline through Vite's transform), not just
// that the runner starts.
describe('App', () => {
  it('renders the scaffolded heading', () => {
    render(<App />)
    // getByText throws if no match is found, so a plain truthy check is
    // enough — no need for the extra @testing-library/jest-dom dependency
    // just for a .toBeInTheDocument() matcher.
    expect(screen.getByText('Get started')).toBeTruthy()
  })
})
