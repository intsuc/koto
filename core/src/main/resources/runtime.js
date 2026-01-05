const adapter = await navigator.gpu.requestAdapter()
const device = await adapter.requestDevice()
const canvas = document.getElementById("main")
const context = canvas.getContext("webgpu")
const format = navigator.gpu.getPreferredCanvasFormat()
context.configure({
  device,
  format,
})
const observer = new ResizeObserver(([{
  devicePixelContentBoxSize,
  contentBoxSize,
  target,
}]) => {
  const width = devicePixelContentBoxSize?.[0].inlineSize ?? contentBoxSize[0].inlineSize * devicePixelRatio
  const height = devicePixelContentBoxSize?.[0].blockSize ?? contentBoxSize[0].blockSize * devicePixelRatio
  target.width = Math.max(1, Math.min(width, device.limits.maxTextureDimension2D))
  target.height = Math.max(1, Math.min(height, device.limits.maxTextureDimension2D))
})
try {
  observer.observe(canvas, { box: "device-pixel-content-box" })
} catch {
  observer.observe(canvas, { box: "content-box" })
}

const type = Symbol("type")
const bool = Symbol("bool")
const int64 = Symbol("int64")
const float64 = Symbol("float64")
const str = Symbol("str")
const funSymbol = Symbol("fun")
const fun = (params, result) => ({ [funSymbol]: { params, result } })
const recordSymbol = Symbol("record")
const record = (fields) => ({ [recordSymbol]: fields })
const refineSymbol = Symbol("refine")
const refine = (base, predicate) => ({ [refineSymbol]: { base, predicate } })

const checkType = (targetValue, expectedType) => {
  if (expectedType === type && targetValue === type) return
  if (expectedType === type && targetValue === bool) return
  if (expectedType === type && targetValue === int64) return
  if (expectedType === type && targetValue === float64) return
  if (expectedType === type && targetValue === str) return

  const typeOfTarget = typeof targetValue
  if (expectedType === bool && typeOfTarget === "boolean") return
  if (expectedType === int64 && typeOfTarget === "bigint") return
  if (expectedType === float64 && typeOfTarget === "number") return
  if (expectedType === str && typeOfTarget === "string") return

  if (funSymbol in expectedType) {
    // TODO
  }

  if (recordSymbol in expectedType) {
    // TODO
  }

  if (refineSymbol in expectedType) {
    const { base, predicate } = expectedType[refineSymbol]
    checkType(targetValue, base)
    if (!predicate(targetValue)) {
      throw new TypeError(`Refinement predicate failed for value: ${targetValue}`)
    }
    return
  }

  throw new TypeError(`Expected type ${expectedType}, but got value: ${targetValue}`)
}
