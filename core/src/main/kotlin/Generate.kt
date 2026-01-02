package koto.core

data class GenerateResult(
    val code: String,
)

fun generate(input: ElaborateResult): GenerateResult {
    val code = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        html,
        body {
            margin: 0;
            height: 100%;
        }
        canvas {
            display: block;
            width: 100%;
            height: 100%;
        }
    </style>
    <script type="module">
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
    </script>
</head>
<body>
    <canvas id="main"></canvas>
</body>
</html>
"""
    return GenerateResult(code)
}
