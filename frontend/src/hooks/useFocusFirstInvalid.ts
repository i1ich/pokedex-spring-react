import { useEffect, useRef } from 'react'

/**
 * Moves focus to the first field marked `aria-invalid` whenever `attempt` changes (after the form
 * re-rendered with its errors), so keyboard and screen-reader users land on the problem.
 */
export function useFocusFirstInvalid(attempt: number) {
  const formRef = useRef<HTMLFormElement>(null)
  useEffect(() => {
    if (attempt > 0) formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
  }, [attempt])
  return formRef
}
