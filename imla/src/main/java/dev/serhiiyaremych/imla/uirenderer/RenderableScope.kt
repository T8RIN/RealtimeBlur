/*
 * Copyright 2024, Serhii Yaremych
 * SPDX-License-Identifier: MIT
 */

package dev.serhiiyaremych.imla.uirenderer

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.trace
import dev.romainguy.kotlin.math.Float2
import dev.romainguy.kotlin.math.Float3
import dev.serhiiyaremych.imla.renderer.Framebuffer
import dev.serhiiyaremych.imla.renderer.RenderCommand
import dev.serhiiyaremych.imla.renderer.Renderer2D
import dev.serhiiyaremych.imla.renderer.ShaderProgram
import dev.serhiiyaremych.imla.renderer.camera.OrthographicCamera
import dev.serhiiyaremych.imla.renderer.camera.OrthographicCameraController

internal class RenderableScope(
    internal val scale: Float,
    private val originalSizeInt: IntSize,
    private val renderer: Renderer2D = Renderer2D
) {

    val size: Float2 = Float2(originalSizeInt.width.toFloat(), originalSizeInt.height.toFloat())
    val center: Float3 = Float3(size / 2f)

    private val scaledSizeInt: IntSize = IntSize(
        width = (originalSizeInt.width * scale).toInt(),
        height = (originalSizeInt.height * scale).toInt()
    )
    val scaledSize: Float2 = Float2(scaledSizeInt.width.toFloat(), scaledSizeInt.height.toFloat())
    val scaledCenter: Float3 = Float3(scaledSize / 2f)

    internal val cameraController = OrthographicCameraController.createPixelUnitsController(
        viewportWidth = originalSizeInt.width,
        viewportHeight = originalSizeInt.height
    )

    internal val scaledCameraController = OrthographicCameraController.createPixelUnitsController(
        viewportWidth = scaledSizeInt.width,
        viewportHeight = scaledSizeInt.height
    )


    inline fun bindFrameBuffer(framebuffer: Framebuffer, draw: Renderer2D.() -> Unit) =
        trace("RenderableScope#bindFrameBuffer") {
            try {
                framebuffer.bind()
                draw(renderer)
            } finally {
                framebuffer.unbind()
                RenderCommand.setViewPort(0, 0, originalSizeInt.width, originalSizeInt.height)
            }
        }

    inline fun drawScene(
        camera: OrthographicCamera = scaledCameraController.camera,
        draw: Renderer2D.() -> Unit
    ) = trace("RenderableScope#drawScene") {
        try {
            renderer.beginScene(camera)
            draw(renderer)
        } finally {
            renderer.endScene()
        }
    }


    inline fun drawScene(
        camera: OrthographicCamera = scaledCameraController.camera,
        shaderProgram: ShaderProgram,
        draw: Renderer2D.() -> Unit
    ) = trace("RenderableScope#drawScene") {
        try {
            renderer.beginScene(camera, shaderProgram)
            draw(renderer)
        } finally {
            renderer.endScene()
        }
    }
}