import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import type { LocalPokemon } from '../api/types'
import { ErrorBanner } from '../components/ErrorBanner'
import { Field } from '../components/Field'
import { TagInput } from '../components/TagInput'
import { useFocusFirstInvalid } from '../hooks/useFocusFirstInvalid'
import { useLocalPokemon } from '../hooks/useLocalPokemon'
import { useUpdateLocalPokemon } from '../hooks/useUpdateLocalPokemon'
import { formatHeight, formatName, formatPokemonId, formatWeight } from '../lib/format'
import { serverFieldErrors } from '../lib/serverErrors'
import {
  addTag,
  toUpdateRequest,
  validateLocalPokemonForm,
  type LocalPokemonFormField,
  type LocalPokemonFormValues,
} from '../lib/validation'

const FORM_FIELDS = ['localName', 'region', 'notes', 'tags'] as const

type FieldMessages = Partial<Record<LocalPokemonFormField, string>>

function parseId(raw: string | undefined): number | null {
  if (raw === undefined || !/^[1-9]\d{0,9}$/.test(raw)) return null
  return Number(raw)
}

function NotInCollection() {
  return (
    <>
      <h1>Not in your collection</h1>
      <p>This Pokémon is not in your collection (any more).</p>
      <p>
        <Link to="/collection">Back to my collection</Link>
      </p>
    </>
  )
}

export function EditPokemonPage() {
  const { id: rawId } = useParams()
  const id = parseId(rawId)
  const query = useLocalPokemon(id)
  // Bumped by "Reload": remounts the form so it starts again from the freshly fetched record.
  const [resetToken, setResetToken] = useState(0)

  if (id === null) return <NotInCollection />
  if (query.isPending) {
    return (
      <p role="status" className="empty">
        Loading…
      </p>
    )
  }
  if (query.isError && !query.data) {
    if (query.error instanceof ApiError && query.error.status === 404) return <NotInCollection />
    return <ErrorBanner error={query.error} onRetry={() => void query.refetch()} />
  }

  async function reload() {
    const result = await query.refetch()
    if (result.isError) throw result.error
    setResetToken((token) => token + 1)
  }

  return <EditForm key={resetToken} record={query.data!} onReload={reload} />
}

interface EditFormProps {
  record: LocalPokemon
  /** Refetches the record and resets the form; rejects when the refetch fails. */
  onReload: () => Promise<void>
}

function EditForm({ record, onReload }: EditFormProps) {
  const navigate = useNavigate()
  const update = useUpdateLocalPokemon(record.id)
  // The version the form was filled from. A background refetch must not change it silently: the
  // user would then overwrite changes they never saw.
  const [{ version }] = useState(record)
  const [text, setText] = useState({
    localName: record.localName ?? '',
    region: record.region ?? '',
    notes: record.notes ?? '',
  })
  const [tags, setTags] = useState(record.tags)
  const [draft, setDraft] = useState('')
  const [draftError, setDraftError] = useState<string | null>(null)
  const [submitted, setSubmitted] = useState(false)
  const [serverFields, setServerFields] = useState<FieldMessages>({})
  const [attempt, setAttempt] = useState(0)
  const [reloading, setReloading] = useState(false)
  const [reloadError, setReloadError] = useState<unknown>(null)
  const formRef = useFocusFirstInvalid(attempt)

  const name = formatName(record.name)
  const values: LocalPokemonFormValues = { ...text, tags }
  const clientErrors: FieldMessages = submitted ? validateLocalPokemonForm(values) : {}
  const errors: FieldMessages = {
    localName: serverFields.localName ?? clientErrors.localName,
    region: serverFields.region ?? clientErrors.region,
    notes: serverFields.notes ?? clientErrors.notes,
    tags: draftError ?? serverFields.tags ?? clientErrors.tags,
  }

  const failure = update.isError ? update.error : null
  const conflict = failure instanceof ApiError && failure.status === 409
  const gone = failure instanceof ApiError && failure.status === 404
  const unmappedFailure =
    failure !== null &&
    !conflict &&
    !gone &&
    Object.keys(serverFieldErrors(failure, FORM_FIELDS)).length === 0

  function setField(field: 'localName' | 'region' | 'notes', value: string) {
    setText((current) => ({ ...current, [field]: value }))
    setServerFields((current) => ({ ...current, [field]: undefined }))
  }

  function changeTags(next: string[]) {
    setTags(next)
    setServerFields((current) => ({ ...current, tags: undefined }))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (update.isPending || conflict) return
    setSubmitted(true)
    setServerFields({})
    update.reset()

    // Text still sitting in the tag input counts: the user would expect it to be saved.
    const withDraft = addTag(tags, draft)
    if (withDraft.error) {
      setDraftError(withDraft.error)
      setAttempt((n) => n + 1)
      return
    }
    setTags(withDraft.tags)
    setDraft('')
    const checked: LocalPokemonFormValues = { ...text, tags: withDraft.tags }
    if (Object.keys(validateLocalPokemonForm(checked)).length > 0) {
      setAttempt((n) => n + 1)
      return
    }

    update.mutate(toUpdateRequest(checked, version), {
      onSuccess: () =>
        navigate('/collection', { state: { notice: `Saved your changes to ${name}.` } }),
      onError: (error) => {
        const fields = serverFieldErrors(error, FORM_FIELDS)
        if (Object.keys(fields).length > 0) {
          setServerFields(fields)
          setAttempt((n) => n + 1)
        }
      },
    })
  }

  async function handleReload() {
    setReloading(true)
    setReloadError(null)
    try {
      await onReload()
    } catch (error) {
      setReloadError(error)
      setReloading(false)
    }
  }

  const image = record.artworkUrl ?? record.spriteUrl

  return (
    <article className="edit">
      <p>
        <Link to="/collection">Back to my collection</Link>
      </p>
      <h1>Edit {name}</h1>

      <section className="edit__info" aria-label={`${name} from PokeAPI`}>
        {image ? (
          <img
            src={image}
            alt={`${record.artworkUrl ? 'Artwork' : 'Sprite'} of ${name}`}
            width={120}
            height={120}
          />
        ) : null}
        <div>
          <p className="card__id">{formatPokemonId(record.id)}</p>
          <dl className="facts facts--stacked">
            {record.category && (
              <div>
                <dt>Category</dt>
                <dd>{record.category}</dd>
              </div>
            )}
            <div>
              <dt>Abilities</dt>
              <dd>{record.abilities.map(formatName).join(', ') || 'None'}</dd>
            </div>
            <div>
              <dt>Height</dt>
              <dd>{formatHeight(record.heightM)}</dd>
            </div>
            <div>
              <dt>Weight</dt>
              <dd>{formatWeight(record.weightKg)}</dd>
            </div>
            <div>
              <dt>Synced</dt>
              <dd>
                {new Date(record.syncedAt).toLocaleDateString('en-US', { dateStyle: 'medium' })}
              </dd>
            </div>
          </dl>
          <p>
            <Link to={`/pokemon/${encodeURIComponent(record.name)}`}>View PokeAPI details</Link>
          </p>
        </div>
      </section>

      <form ref={formRef} className="form" onSubmit={handleSubmit} noValidate>
        {conflict && (
          <div className="error-banner" role="alert">
            <div className="error-banner__text">
              <strong className="error-banner__title">
                This Pokémon was edited elsewhere. Reload to get the latest version.
              </strong>
            </div>
            <button type="button" className="button" onClick={handleReload} disabled={reloading}>
              Reload
            </button>
          </div>
        )}
        {gone && (
          <div className="error-banner" role="alert">
            <div className="error-banner__text">
              <strong className="error-banner__title">
                This Pokémon is no longer in your collection.
              </strong>
              <p className="error-banner__detail">
                <Link to="/collection">Back to my collection</Link>
              </p>
            </div>
          </div>
        )}
        {unmappedFailure && <ErrorBanner error={failure} />}
        {reloadError !== null && <ErrorBanner error={reloadError} />}

        <Field
          id="localName"
          label="Local name"
          hint="Optional. Your own name for this Pokémon, up to 100 characters."
          error={errors.localName}
        >
          {(control) => (
            <input
              {...control}
              className="input"
              type="text"
              value={text.localName}
              onChange={(event) => setField('localName', event.target.value)}
            />
          )}
        </Field>
        <Field id="region" label="Region" hint="Up to 100 characters." error={errors.region}>
          {(control) => (
            <input
              {...control}
              className="input"
              type="text"
              value={text.region}
              onChange={(event) => setField('region', event.target.value)}
            />
          )}
        </Field>
        <Field id="notes" label="Notes" hint="Up to 1000 characters." error={errors.notes}>
          {(control) => (
            <textarea
              {...control}
              className="input"
              rows={5}
              value={text.notes}
              onChange={(event) => setField('notes', event.target.value)}
            />
          )}
        </Field>
        <TagInput
          id="tags"
          label="Tags"
          hint="Press Enter or comma to add. Lowercase letters, digits and hyphens; at most 10."
          tags={tags}
          onTagsChange={changeTags}
          draft={draft}
          onDraftChange={setDraft}
          error={errors.tags}
          onDraftError={setDraftError}
        />

        <div className="form__actions">
          <button type="submit" className="button" disabled={update.isPending || conflict}>
            {update.isPending ? 'Saving…' : 'Save changes'}
          </button>
          <Link className="button button--ghost" to="/collection">
            Cancel
          </Link>
        </div>
      </form>
    </article>
  )
}
