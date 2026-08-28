'use client'

import { useEffect, useRef, useState } from 'react'

/**
 * Measures the container and computes the largest box with the given aspect
 * ratio that fits inside it — deterministic letterboxing for the viewfinder.
 */
export function useFittedBox(aspect: number) {
  const ref = useRef<HTMLDivElement>(null)
  const [box, setBox] = useState({ w: 0, h: 0 })
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const measure = () => {
      const cw = el.clientWidth
      const ch = el.clientHeight
      if (cw <= 0 || ch <= 0) return
      let w = cw
      let h = cw / aspect
      if (h > ch) {
        h = ch
        w = ch * aspect
      }
      const nw = Math.floor(w)
      const nh = Math.floor(h)
      setBox((prev) => (prev.w === nw && prev.h === nh ? prev : { w: nw, h: nh }))
    }
    measure()
    const ro = new ResizeObserver(measure)
    ro.observe(el)
    return () => ro.disconnect()
  }, [aspect])
  return { ref, box }
}
