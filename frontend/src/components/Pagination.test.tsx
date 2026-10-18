import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { Pagination } from './Pagination'

function LocationProbe() {
  return <p data-testid="search">{useLocation().search}</p>
}

function renderPagination(page: number, totalPages: number) {
  render(
    <MemoryRouter initialEntries={['/']}>
      <Pagination page={page} totalPages={totalPages} />
      <LocationProbe />
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

  describe('go to page', () => {
    async function goTo(value: string, page = 2, totalPages = 451) {
      renderPagination(page, totalPages)
      const input = screen.getByRole('spinbutton', { name: /go to page/i })
      await userEvent.clear(input)
      if (value) await userEvent.type(input, value)
      await userEvent.click(screen.getByRole('button', { name: /^go$/i }))
    }

    it('jumps to the typed page', async () => {
      await goTo('225')
      expect(screen.getByTestId('search')).toHaveTextContent('?page=225')
    })

    it('clamps a page past the end to the last page', async () => {
      await goTo('9999')
      expect(screen.getByTestId('search')).toHaveTextContent('?page=451')
    })

    it('clamps zero to the first page', async () => {
      await goTo('0', 5)
      expect(screen.getByTestId('search')).toHaveTextContent('?page=1')
    })

    it('ignores an empty value', async () => {
      await goTo('')
      expect(screen.getByTestId('search')).toBeEmptyDOMElement()
    })

    it('limits the input to the existing pages', () => {
      renderPagination(2, 451)
      const input = screen.getByRole('spinbutton', { name: /go to page/i })
      expect(input).toHaveAttribute('min', '1')
      expect(input).toHaveAttribute('max', '451')
    })
  })
})
