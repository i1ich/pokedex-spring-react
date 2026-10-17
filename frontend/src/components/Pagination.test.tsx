import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { Pagination } from './Pagination'

function renderPagination(page: number, totalPages: number) {
  render(
    <MemoryRouter initialEntries={['/']}>
      <Pagination page={page} totalPages={totalPages} />
    </MemoryRouter>,
  )
  return within(screen.getByRole('navigation', { name: /pagination/i }))
}

describe('Pagination', () => {
  it('links to the neighbouring pages through the ?page= query parameter', () => {
    const nav = renderPagination(2, 5)

    expect(nav.getByRole('link', { name: /previous/i })).toHaveAttribute('href', '/?page=1')
    expect(nav.getByRole('link', { name: /next/i })).toHaveAttribute('href', '/?page=3')
    expect(nav.getByRole('link', { name: /first/i })).toHaveAttribute('href', '/?page=1')
    expect(nav.getByRole('link', { name: /last/i })).toHaveAttribute('href', '/?page=5')
    expect(nav.getByText('Page 2 of 5')).toBeInTheDocument()
  })

  it('disables Previous and First on the first page', () => {
    const nav = renderPagination(1, 5)

    expect(nav.queryByRole('link', { name: /previous/i })).not.toBeInTheDocument()
    expect(nav.queryByRole('link', { name: /first/i })).not.toBeInTheDocument()
    expect(nav.getByText(/previous/i)).toHaveAttribute('aria-disabled', 'true')
    expect(nav.getByRole('link', { name: /next/i })).toBeInTheDocument()
  })

  it('disables Next and Last on the last page', () => {
    const nav = renderPagination(5, 5)

    expect(nav.queryByRole('link', { name: /next/i })).not.toBeInTheDocument()
    expect(nav.queryByRole('link', { name: /last/i })).not.toBeInTheDocument()
    expect(nav.getByText(/next/i)).toHaveAttribute('aria-disabled', 'true')
  })

  it('marks the current page position for assistive technology', () => {
    const nav = renderPagination(2, 5)
    expect(nav.getByText('Page 2 of 5')).toHaveAttribute('aria-current', 'page')
  })
})
