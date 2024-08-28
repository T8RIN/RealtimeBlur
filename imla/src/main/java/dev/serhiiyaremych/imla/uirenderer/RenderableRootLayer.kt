/*
 * Copyright 2024, Serhii Yaremych
 * SPDX-License-Identifier: MIT
 */

package dev.serhiiyaremych.imla.uirenderer

import android.content.res.AssetManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.annotation.MainThread
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import androidx.tracing.trace
import dev.serhiiyaremych.imla.renderer.Bind
import dev.serhiiyaremych.imla.renderer.Framebuffer
import dev.serhiiyaremych.imla.renderer.FramebufferAttachmentSpecification
import dev.serhiiyaremych.imla.renderer.FramebufferSpecification
import dev.serhiiyaremych.imla.renderer.FramebufferTextureFormat
import dev.serhiiyaremych.imla.renderer.FramebufferTextureSpecification
import dev.serhiiyaremych.imla.renderer.RenderCommand
import dev.serhiiyaremych.imla.renderer.Renderer2D
import dev.serhiiyaremych.imla.renderer.Shader
import dev.serhiiyaremych.imla.renderer.SimpleRenderer
import dev.serhiiyaremych.imla.renderer.Texture
import dev.serhiiyaremych.imla.renderer.Texture2D
import dev.serhiiyaremych.imla.uirenderer.processing.SimpleQuadRenderer

internal class RenderableRootLayer(
    private val assetManager: AssetManager,
    private val layerDownsampleFactor: Int,
    private val density: Density,
    internal val graphicsLayer: GraphicsLayer,
    internal val renderer2D: Renderer2D,
    private val simpleQuadRenderer: SimpleQuadRenderer,
    private val onLayerTextureUpdated: () -> Unit
) {
    val sizeInt: IntSize get() = graphicsLayer.size
    val sizeDec: Size get() = sizeInt.toSize()
    val scale: Float
        get() = 1.0f / layerDownsampleFactor

    val isReady: Boolean
        get() = sizeInt == IntSize.Zero

    private lateinit var renderableScope: RenderableScope
    private val drawingScope: CanvasDrawScope = CanvasDrawScope()
    private lateinit var layerExternalTexture: SurfaceTexture
    private lateinit var layerSurface: Surface
    private lateinit var extOesLayerTexture: Texture2D

    lateinit var highResFBO: Framebuffer
        private set

    private var isInitialized: Boolean = false
    private var isDestroyed: Boolean = false

    private lateinit var extOesShaderProgram: Shader

    fun initialize() {
        require(!isDestroyed) { "Can't re-init destroyed layer" }
        if (!isReady) {
            trace("RenderableRootLayer#initialize") {
                renderableScope =
                    RenderableScope(scale = scale, originalSizeInt = sizeInt, renderer = renderer2D)
                val specification = FramebufferSpecification(
                    size = sizeInt,
                    attachmentsSpec = FramebufferAttachmentSpecification(
                        listOf(
                            FramebufferTextureSpecification(
                                format = FramebufferTextureFormat.RGBA8,
                                flip = true
                            )
                        )
                    ),
                )

                highResFBO = Framebuffer.create(specification)

                extOesLayerTexture = Texture2D.create(
                    target = Texture.Target.TEXTURE_EXTERNAL_OES,
                    specification = Texture.Specification(size = sizeInt, flipTexture = false)
                )
                extOesLayerTexture.bind()
                layerExternalTexture = SurfaceTexture(extOesLayerTexture.id)
                layerExternalTexture.setDefaultBufferSize(sizeInt.width, sizeInt.height)
                layerSurface = Surface(layerExternalTexture)

                layerExternalTexture.setOnFrameAvailableListener {
                    trace("surfaceTexture#updateTexImage") { it.updateTexImage() }
                    copyTextureToFrameBuffer()
                    onLayerTextureUpdated()
                }

                extOesShaderProgram = Shader.create(
                    assetManager = assetManager,
                    vertexAsset = "shader/simple_quad.vert",
                    fragmentAsset = "shader/simple_ext_quad.frag"
                ).apply {
                    bindUniformBlock(
                        SimpleRenderer.TEXTURE_DATA_UBO_BLOCK,
                        SimpleRenderer.TEXTURE_DATA_UBO_BINDING_POINT
                    )
                }
                isInitialized = true
            }
        }
    }

    fun resize() {
        TODO("Implement runtime layer resizing")
    }

    private fun copyTextureToFrameBuffer() = trace(
        "copyExtTextureToFrameBuffer"
    ) {
        trace("fullSizeBuffer") {
            highResFBO.bind(Bind.DRAW)
            RenderCommand.clear()
            simpleQuadRenderer.draw(extOesShaderProgram, extOesLayerTexture)
        }
    }

    @MainThread
    fun updateTex() = trace("RenderableRootLayer#updateTex") {
        require(!isDestroyed) { "Can't update destroyed layer" }
        require(!graphicsLayer.isReleased) { "GraphicsLayer has been released!" }
        require(isInitialized) { "RenderableRootLayer not initialized!" }

        trace("drawLayerToExtTexture[$sizeInt]") {

            layerSurface.lockHardwareCanvas()?.let { canvas ->
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                drawingScope.draw(density, LayoutDirection.Ltr, Canvas(canvas), sizeDec) {
                    trace("drawGraphicsLayer") {
                        scale(scaleX = 1.0f, scaleY = -1f) {
                            drawLayer(graphicsLayer)
                        }
                    }
                }
                layerSurface.unlockCanvasAndPost(canvas)
            }
        }
    }

    fun destroy() {
        layerExternalTexture.release()
        layerSurface.release()
        extOesLayerTexture.destroy()
        isDestroyed = true
    }
}

internal operator fun IntSize.compareTo(other: IntSize): Int {
    return (width.toLong() * height).compareTo((other.width.toLong() * other.height))
}
