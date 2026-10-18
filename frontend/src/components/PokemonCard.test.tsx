import { fireEvent, screen } from '@testing-library/react'
import { renderWithProviders } from '../test/helpers'
import { PokemonCard } from './PokemonCard'

const SPRITE = 'https://raw.example/sprites/pokemon/10301.png'
const ARTWORK = 'https://raw.example/sprites/pokemon/other/official-artwork/10301.png'

const zygarde = {
  id: 10301,
  name: 'zygarde-mega',
  spriteUrl: SPRITE,
  artworkUrl: ARTWORK,
  category: null,
  weightKg: 610,
  abilities: [],
}

const image = () => screen.getByRole('img', { name: 'Artwork of Zygarde Mega' })

describe('PokemonCard', () => {
  it('shows the official artwork, the same picture as the detail page', () => {
    renderWithProviders(<PokemonCard pokemon={zygarde} />)

    expect(image()).toHaveAttribute('src', ARTWORK)
  })

  it('falls back to the pixel sprite when the artwork fails to load', () => {
    renderWithProviders(<PokemonCard pokemon={zygarde} />)

    fireEvent.error(image())

    expect(image()).toHaveAttribute('src', SPRITE)
  })

  it('uses the sprite when there is no artwork', () => {
    renderWithProviders(<PokemonCard pokemon={{ ...zygarde, artworkUrl: null }} />)

    expect(image()).toHaveAttribute('src', SPRITE)
  })

  it('shows the placeholder when nothing loads', () => {
    renderWithProviders(<PokemonCard pokemon={zygarde} />)

    fireEvent.error(image())
    fireEvent.error(image())

    expect(
      screen.getByRole('img', { name: 'No image available for Zygarde Mega' }),
    ).toBeInTheDocument()
  })
})
