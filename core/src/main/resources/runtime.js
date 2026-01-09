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

const checkType = (target, type) => {
	if (!type[refineSymbol].predicate(target)) {
		throw new TypeError(`Predicate failed for value: ${target}`)
	}
}
