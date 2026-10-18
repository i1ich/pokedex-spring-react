import { render, screen } from '@testing-library/react'
import { NoImage } from './NoImage'

describe('NoImage', () => {
  it('is one labelled image: a pokéball with a question mark on it', () => {
    const { container } = render(<NoImage label="No image available for Koraidon" />)

    const image = screen.getByRole('img', { name: 'No image available for Koraidon' })
    expect(image).toHaveTextContent('?')
    expect(image.querySelector('svg')).toBeInTheDocument()
    expect(container.querySelectorAll('[role="img"]')).toHaveLength(1)
  })
})
