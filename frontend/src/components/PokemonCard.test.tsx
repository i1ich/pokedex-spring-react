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

const image = () => screen.getByRole('img', { name: 'Sprite of Zygarde Mega' })

describe('PokemonCard', () => {
  it('shows the sprite, as the user story asks', () => {
    renderWithProviders(<PokemonCard pokemon={zygarde} />)

    expect(image()).toHaveAttribute('src', SPRITE)
  })

  it('falls back to the official artwork when the sprite fails to load', () => {
    renderWithProviders(<PokemonCard pokemon={zygarde} />)

    fireEvent.error(image())

    expect(image()).toHaveAttribute('src', ARTWORK)
  })

  it('uses the artwork when there is no sprite', () => {
    renderWithProviders(<PokemonCard pokemon={{ ...zygarde, spriteUrl: null }} />)

    expect(image()).toHaveAttribute('src', ARTWORK)
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
