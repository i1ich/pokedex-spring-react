import { fireEvent, screen } from '@testing-library/react'
import { renderWithProviders } from '../test/helpers'
import { PokemonCard } from './PokemonCard'

const SPRITE = 'https://raw.example/sprites/pokemon/10301.png'
const ARTWORK = 'https://raw.example/sprites/pokemon/other/official-artwork/10301.png'

const zygarde = {
  id: 10301,
  name: 'zygarde-mega',
  spriteUrl: SPRITE,
  category: null,
  weightKg: 610,
  abilities: [],
}

describe('PokemonCard', () => {
  it('falls back to the official artwork when the sprite fails to load', () => {
    renderWithProviders(<PokemonCard pokemon={zygarde} />)

    fireEvent.error(screen.getByRole('img', { name: 'Sprite of Zygarde Mega' }))

    expect(screen.getByRole('img', { name: 'Sprite of Zygarde Mega' })).toHaveAttribute(
      'src',
      ARTWORK,
    )
  })

  it('shows the placeholder when the artwork fails too', () => {
    renderWithProviders(<PokemonCard pokemon={zygarde} />)

    fireEvent.error(screen.getByRole('img', { name: 'Sprite of Zygarde Mega' }))
    fireEvent.error(screen.getByRole('img', { name: 'Sprite of Zygarde Mega' }))

    expect(
      screen.getByRole('img', { name: 'No sprite available for Zygarde Mega' }),
    ).toBeInTheDocument()
  })
})
